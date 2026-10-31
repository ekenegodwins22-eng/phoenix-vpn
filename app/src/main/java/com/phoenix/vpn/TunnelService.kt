package com.phoenix.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import org.amnezia.awg.hevtunnel.TProxyService
import java.io.ByteArrayInputStream
import java.io.File

class TunnelService : VpnService() {
    private lateinit var backend: GoBackend
    private var sshEngine: SshEngine? = null
    private var vpnInterface: ParcelFileDescriptor? = null
    private var hevStarted = false
    private val activeTunnel = object : Tunnel {
        override fun getName() = "PhoenixTunnel"
        override fun onStateChange(newState: Tunnel.State) { addLog("WireGuard state: $newState") }
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(71, notification("Preparing VPN"))
        val source = intent?.getStringExtra(EXTRA_CONFIG)
        val protocolName = intent?.getStringExtra(EXTRA_PROTOCOL)
        if (source.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        Thread {
            try {
                val protocol = protocolName?.let { TunnelProtocol.valueOf(it) } ?: TunnelProtocol.WIREGUARD
                if (protocol == TunnelProtocol.WIREGUARD) {
                    val config = Config.parse(ByteArrayInputStream(source.toByteArray()))
                    addLog("starting tunnel service")
                    addLog("Routes: " + config.peers.firstOrNull()?.routes?.joinToString(", "){it.toString()} ?: "0.0.0.0/0")
                    backend.setState(activeTunnel, Tunnel.State.UP, config)
                    addLog("WireGuard tunnel is up")
                } else {
                    startSshTunnel(protocol, source)
                }
                getSystemService(NotificationManager::class.java).notify(71, notification("Connected"))
            } catch (e: Exception) {
                addLog("Tunnel start failed: ${e.message}")
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }

    private fun startSshTunnel(protocol: TunnelProtocol, source: String) {
        require(protocol == TunnelProtocol.SSH_DIRECT || protocol == TunnelProtocol.SSH_SSL_TLS || protocol == TunnelProtocol.SSH_SSL_PAYLOAD) {
            "${protocol.label} is not implemented"
        }
        val engine = TunnelEngines.forProtocol(protocol, this, { socket -> protect(socket) }, ::addLog) as SshEngine
        sshEngine = engine
        engine.connect(source)
        addLog("Connected. starting tunnel service")
        addLog("Configuring Android TUN interface")
        val tun = Builder()
            .setSession("PhoenixTunnel SSH")
            .setMtu(1500)
            .addAddress("10.77.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .establish() ?: throw IllegalStateException("Android refused to establish VPN interface")
        vpnInterface = tun
        val yaml = """
            tunnel:
              mtu: 1500
              ipv4: 10.77.0.2
            socks5:
              address: 127.0.0.1
              port: ${engine.socksPort}
              udp: 'tcp'
            misc:
              log-level: warn
        """.trimIndent() + "\n"
        val configFile = File(filesDir, "phoenix/hev-ssh.yaml").apply {
            parentFile?.mkdirs()
            writeText(yaml)
        }
        addLog("Starting hev TUN-to-SOCKS bridge on 127.0.0.1:${engine.socksPort}")
        TProxyService.TProxyStartService(configFile.absolutePath, tun.fd)
        hevStarted = true
        addLog("tun2socks started")
        addLog("Tunnel fully up. TCP forwarding active")
    }

    private fun addLog(message: String) { ConfigRepository(this).addLog(message) }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel("phoenix_vpn", "PhoenixTunnel", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "phoenix_vpn")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("PhoenixTunnel")
            .setContentText(text)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        addLog("stopping tunnel service")
        if (hevStarted) try { TProxyService.TProxyStopService() } catch (e: Exception) { addLog("hev stop failed: ${e.message}") }
        hevStarted = false
        try { vpnInterface?.close() } catch (_: Exception) { }
        addLog("closing VPN interface")
        vpnInterface = null
        sshEngine?.disconnect()
        sshEngine = null
        try { backend.setState(activeTunnel, Tunnel.State.DOWN, null) } catch (_: Exception) { }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_CONFIG = "config"
        const val EXTRA_PROTOCOL = "protocol"
    }
}
