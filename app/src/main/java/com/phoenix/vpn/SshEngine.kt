package com.phoenix.vpn

import android.content.Context
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.connection.channel.direct.DirectConnection
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import javax.net.SocketFactory
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import javax.net.ssl.SSLParameters
import javax.net.ssl.SNIHostName
import javax.net.ssl.SSLSocketFactory

/** SSH password-auth tunnel with a local SOCKS5 CONNECT endpoint. */
class SshEngine(
    private val context: Context,
    override val protocol: TunnelProtocol,
    private val protectSocket: (Socket) -> Boolean,
    private val log: (String) -> Unit
) : TunnelEngine {
    @Volatile var socksPort: Int = 0
        private set
    private var client: SSHClient? = null
    private var server: ServerSocket? = null
    private var acceptThread: Thread? = null
    private val stopped = AtomicBoolean(false)

    override fun validate(config: String): String {
        val profile = SshProfile.parse(config)
        return "SSH profile for ${profile.host}:${profile.port} (${profile.username})"
    }

    override fun connect(config: String) {
        val profile = SshProfile.parse(config)
        stopped.set(false)
        log("Connecting SSH to ${profile.host}:${profile.port}")
        log("Injecting")
        val ssh = SSHClient()
        ssh.setConnectTimeout(15_000)
        ssh.setTimeout(30_000)
        ssh.addHostKeyVerifier(TrustOnFirstUseVerifier(context, log))
        ssh.setSocketFactory(object : SocketFactory() {
            private fun createConnectedSocket(): Socket {
                var raw: Socket? = null
                try {
                    val socket = Socket()
                    raw = socket
                    if (!protectSocket(socket)) throw IllegalStateException("Could not protect SSH socket from VPN routing")
                    socket.connect(InetSocketAddress(profile.host, profile.port), 15_000)
                    socket.soTimeout = 15_000
                    var transport: Socket = socket
                    if (protocol == TunnelProtocol.SSH_SSL_TLS) {
                        val tls = SSLSocketFactory.getDefault() as SSLSocketFactory
                        val sni = profile.sniffedHost.ifBlank { profile.host }
                        val ssl = tls.createSocket(socket, sni, profile.port, true) as javax.net.ssl.SSLSocket
                        val params = ssl.sslParameters
                        if (sni.isNotBlank() && !sni.matches(Regex("^[0-9.]+$")) && !sni.contains(':')) {
                            params.serverNames = listOf(SNIHostName(sni))
                        }
                        ssl.sslParameters = params
                        ssl.startHandshake()
                        transport = ssl
                        log("TLS handshake complete${if (profile.sniffedHost.isNotBlank()) " with SNI ${profile.sniffedHost}" else ""}")
                    }
                    if (profile.payload.isNotBlank()) {
                        val bytes = expandPayload(profile.payload, profile.host).toByteArray(Charsets.UTF_8)
                        transport.getOutputStream().write(bytes)
                        transport.getOutputStream().flush()
                        log("Sent ${bytes.size} payload bytes")
                        transport.soTimeout = 8000
                        val resp = try { transport.getInputStream().bufferedReader().readLine() } catch (e: Exception) { null }
                        transport.soTimeout = 0
                        if (!resp.isNullOrBlank()) log("Response: ${resp.trim()}")
                    }
                    transport.soTimeout = 0
                    return transport
                } catch (e: Exception) {
                    try { raw?.close() } catch (_: Exception) { }
                    throw e
                }
            }
            override fun createSocket(): Socket = createConnectedSocket()
            override fun createSocket(host: String, port: Int): Socket = createConnectedSocket()
            override fun createSocket(host: String, port: Int, localHost: java.net.InetAddress, localPort: Int): Socket = createConnectedSocket()
            override fun createSocket(host: java.net.InetAddress, port: Int): Socket = createConnectedSocket()
            override fun createSocket(host: java.net.InetAddress, port: Int, localHost: java.net.InetAddress, localPort: Int): Socket = createConnectedSocket()
        })
        try {
            ssh.connect(profile.host, profile.port)
            log("Authenticating with password")
            ssh.authPassword(profile.username, profile.password)
            client = ssh
            log("Authenticated")
            startSocksListener(ssh)
            log("Local SOCKS5 proxy listening on 127.0.0.1:$socksPort")
        } catch (e: Exception) {
            try { ssh.disconnect() } catch (_: Exception) { }
            throw IllegalStateException("SSH connection failed: ${e.message}", e)
        }
    }

    private fun startSocksListener(ssh: SSHClient) {
        val listener = ServerSocket().apply { reuseAddress = true; bind(InetSocketAddress("127.0.0.1", 0), 64) }
        server = listener
        socksPort = listener.localPort
        acceptThread = Thread({
            while (!stopped.get()) {
                try {
                    val socket = listener.accept()
                    Thread({ handleSocksClient(ssh, socket) }, "Phoenix-SOCKS-client").apply { isDaemon = true; start() }
                } catch (e: SocketException) {
                    if (!stopped.get()) log("SOCKS listener stopped: ${e.message}")
                    break
                } catch (e: Exception) {
                    if (!stopped.get()) log("SOCKS accept error: ${e.message}")
                }
            }
        }, "Phoenix-SOCKS-listener").apply { isDaemon = true; start() }
    }

    private fun handleSocksClient(ssh: SSHClient, socket: Socket) {
        socket.use { local ->
            local.soTimeout = 30_000
            val input = BufferedInputStream(local.getInputStream())
            val output = BufferedOutputStream(local.getOutputStream())
            val version = input.read()
            if (version != 5) return
            val methods = input.read()
            if (methods < 0) return
            repeat(methods) { input.read() }
            output.write(byteArrayOf(5, 0))
            output.flush()
            val request = ByteArray(4)
            readFully(input, request)
            if (request[0].toInt() != 5 || request[1].toInt() != 1) {
                output.write(byteArrayOf(5, 7)); output.flush(); return
            }
            val address = when (request[3].toInt() and 0xff) {
                1 -> ByteArray(4).also { readFully(input, it) }.joinToString(".") { (it.toInt() and 0xff).toString() }
                3 -> { val len = input.read(); if (len < 0) return; ByteArray(len).also { readFully(input, it) }.toString(Charsets.UTF_8) }
                4 -> ByteArray(16).also { b -> readFully(input, b) }.joinToString(":") { "%02x".format(it) }
                else -> return
            }
            val portBytes = ByteArray(2).also { readFully(input, it) }
            val targetPort = ((portBytes[0].toInt() and 0xff) shl 8) or (portBytes[1].toInt() and 0xff)
            var channel: DirectConnection? = null
            try {
                channel = ssh.newDirectConnection(address, targetPort)
                channel.open()
                output.write(byteArrayOf(5, 0, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush()
                val upstream = channel.inputStream
                val downstream = channel.outputStream
                val up = Thread({ copy(local, input, downstream) }, "Phoenix-SOCKS-up").apply { isDaemon = true; start() }
                copy(local, upstream, output)
                try { local.shutdownOutput() } catch (_: Exception) { }
                try { up.join(1_000) } catch (_: InterruptedException) { Thread.currentThread().interrupt() }
            } catch (e: Exception) {
                try { output.write(byteArrayOf(5, 5, 0, 1, 0, 0, 0, 0, 0, 0)); output.flush() } catch (_: Exception) { }
                log("SOCKS CONNECT $address:$targetPort failed: ${e.message}")
            } finally {
                try { channel?.close() } catch (_: Exception) { }
            }
        }
    }

    private fun copy(socket: Socket, input: java.io.InputStream, output: java.io.OutputStream) {
        val buffer = ByteArray(16 * 1024)
        try {
            while (!stopped.get()) {
                val count = input.read(buffer)
                if (count < 0) break
                output.write(buffer, 0, count)
                output.flush()
            }
        } catch (_: Exception) { } finally {
            try { socket.shutdownOutput() } catch (_: Exception) { }
        }
    }

    private fun readFully(input: java.io.InputStream, target: ByteArray) {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) throw java.io.EOFException("Incomplete SOCKS request")
            offset += count
        }
    }

    fun disconnect() {
        stopped.set(true)
        try { server?.close() } catch (_: Exception) { }
        try { client?.disconnect() } catch (_: Exception) { }
        try { client?.close() } catch (_: Exception) { }
        client = null
        server = null
        socksPort = 0
        log("SSH tunnel stopped")
    }

    private fun expandPayload(payload: String, host: String) = payload
        .replace("[host]", host)
        .replace("[ua]", "Mozilla/5.0 PhoenixTunnel")
        .replace("[crlf]", "\r\n")
        .replace("[split]", "\r\n\r\n")

    private class TrustOnFirstUseVerifier(context: Context, private val log: (String) -> Unit) : HostKeyVerifier {
        private val file = File(context.filesDir, "phoenix/ssh-host-keys.txt").apply { parentFile?.mkdirs() }
        @Synchronized override fun verify(hostname: String, port: Int, key: java.security.PublicKey): Boolean {
            val id = "$hostname:$port"
            val fingerprint = Base64.getEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(key.encoded)
            )
            val savedLines = if (file.exists()) file.readLines() else emptyList()
            val saved = savedLines.firstOrNull { it.substringBefore('=') == id }?.substringAfter('=')
            if (saved == null) {
                file.appendText("$id=$fingerprint\n")
                log("First-seen SSH host key saved for $id. Verify the server fingerprint independently")
                return true
            }
            if (saved != fingerprint) log("SSH host key changed for $id, rejecting connection")
            return saved == fingerprint
        }
        override fun findExistingAlgorithms(hostname: String, port: Int): MutableList<String> = mutableListOf()
    }

    companion object {
        fun expandPayload(payload: String, host: String) = payload.replace("[host]", host).replace("[ua]", "Mozilla/5.0 PhoenixTunnel").replace("[crlf]", "\r\n").replace("[split]", "\r\n\r\n")
    }
}

data class SshProfile(val host: String, val port: Int, val username: String, val password: String, val payload: String = "", val sniffedHost: String = "") {
    companion object {
        fun parse(config: String): SshProfile {
            val json = JSONObject(config)
            val host = json.optString("host").trim()
            val port = json.optInt("port", 22)
            val username = json.optString("username")
            val password = json.optString("password")
            require(host.isNotBlank()) { "SSH host is required" }
            require(port in 1..65535) { "SSH port must be 1-65535" }
            require(username.isNotBlank()) { "SSH username is required" }
            require(password.isNotEmpty()) { "SSH password is required" }
            return SshProfile(host, port, username, password, json.optString("payload"), json.optString("sniffedHost", json.optString("sni")))
        }
    }
}
