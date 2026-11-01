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
    @Volatile private var stopping = false
    @Volatile private var connectionThread: Thread? = null
    @Volatile private var stopThread: Thread? = null
    private val activeTunnel = object : Tunnel {
        override fun getName() = "PhoenixTunnel"
        override fun onStateChange(newState: Tunnel.State) {
            addLog("WireGuard state: $newState")
            when (newState) {
                Tunnel.State.UP -> notifyState(STATE_WAITING_HANDSHAKE)
                Tunnel.State.DOWN -> if (stopping) notifyState(STATE_DISCONNECTED) else notifyDown()
                else -> Unit
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        backend = GoBackend(this)
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_TUNNEL_STOP) {
            stopTunnel()
            return START_NOT_STICKY
        }
        if (connectionThread?.isAlive == true) {
            addLog("Ignoring duplicate tunnel start while connecting")
            return START_NOT_STICKY
        }
        stopping = false
        startForeground(71, notification("Preparing VPN"))
        val source = intent?.getStringExtra(EXTRA_CONFIG)
        val protocolName = intent?.getStringExtra(EXTRA_PROTOCOL)
        if (source.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        notifyState(STATE_CONNECTING)
        connectionThread = Thread {
            try {
                val protocol = protocolName?.let { TunnelProtocol.valueOf(it) } ?: TunnelProtocol.WIREGUARD
                if (protocol == TunnelProtocol.WIREGUARD) {
                    if (stopping) return@Thread
                    val config = Config.parse(ByteArrayInputStream(source.toByteArray()))
                    addLog("starting tunnel service")
                    addLog("Routes: " + (Regex("AllowedIPs\\s*=\\s*(.+)").find(source)?.groupValues?.get(1)?.trim() ?: "0.0.0.0/0"))
                    notifyState(STATE_WAITING_HANDSHAKE)
                    backend.setState(activeTunnel, Tunnel.State.UP, config)
                    addLog("WireGuard tunnel interface is up; waiting for peer handshake")
                    val handedUpAt = System.currentTimeMillis()
                    val timeoutMs = WG_HANDSHAKE_TIMEOUT_MS
                    var handshook = false
                    try {
                        while (!stopping && System.currentTimeMillis() - handedUpAt < timeoutMs) {
                            Thread.sleep(WG_STATS_POLL_MS)
                            try {
                                val stats = backend.getStatistics(activeTunnel)
                                val peerHandshake = stats.peers().any { peer ->
                                    (stats.peer(peer)?.latestHandshakeEpochMillis ?: 0L) > 0L
                                }
                                if (peerHandshake) {
                                    handshook = true
                                    break
                                }
                            } catch (e: Exception) {
                                addLog("WireGuard peer stats unavailable: ${e.message}")
                            }
                        }
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                    if (handshook && !stopping) {
                        addLog("WireGuard peer exchanged traffic; tunnel connected")
                        notifyUp()
                    } else if (!stopping) {
                        addLog("WireGuard peer handshake not confirmed before timeout")
                        notifyDown()
                        backend.setState(activeTunnel, Tunnel.State.DOWN, null)
                        stopSelf()
                        return@Thread
                    } else {
                        return@Thread
                    }
                } else {
                    if (stopping) return@Thread
                    notifyState(STATE_WAITING_HANDSHAKE)
                    startSshTunnel(protocol, source)
                    if (stopping) { sshEngine?.disconnect(); return@Thread }
                    notifyUp()
                }
                getSystemService(NotificationManager::class.java).notify(71, notification("Connected"))
            } catch (e: Exception) {
                addLog("Tunnel start failed: ${e.message}")
                notifyDown()
                stopSelf()
            } finally {
                connectionThread = null
            }
        }.apply { name = "PhoenixTunnel-connect"; start() }
        return START_NOT_STICKY
    }

    private fun startSshTunnel(protocol: TunnelProtocol, source: String) {
        require(protocol == TunnelProtocol.SSH_DIRECT || protocol == TunnelProtocol.SSH_SSL_TLS || protocol == TunnelProtocol.SSH_SSL_PAYLOAD) {
            "${protocol.label} is not implemented"
        }
        addLog("Configuring Android TUN interface")
        val tun = Builder()
            .setSession("PhoenixTunnel SSH")
            .setMtu(1500)
            .addAddress("10.77.0.2", 32)
            .addRoute("0.0.0.0", 0)
            .establish() ?: throw IllegalStateException("Android refused to establish VPN interface")
        vpnInterface = tun
        addLog("VPN interface ready")
        val engine = TunnelEngines.forProtocol(protocol, this, { socket -> protect(socket) }, ::addLog) as SshEngine
        sshEngine = engine
        engine.connect(source)
        addLog("Connected. starting tunnel service")
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

    private fun notifyState(state: String) {
        sendBroadcast(Intent(ACTION_TUNNEL_STATE).setPackage(packageName).putExtra(EXTRA_STATE, state))
    }

    private fun notifyUp() {
        notifyState(STATE_CONNECTED)
        sendBroadcast(Intent(ACTION_TUNNEL_UP).setPackage(packageName))
    }

    private fun notifyDown() {
        notifyState(STATE_DISCONNECTED)
        sendBroadcast(Intent(ACTION_TUNNEL_DOWN).setPackage(packageName))
    }

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
        val stopIntent = PendingIntent.getService(
            this, 71, Intent(this, TunnelService::class.java).setAction(ACTION_TUNNEL_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, "phoenix_vpn")
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle("PhoenixTunnel")
            .setContentText(text)
            .setContentIntent(pi)
            .addAction(android.R.drawable.ic_media_pause, "STOP", stopIntent)
            .setOngoing(true)
            .build()
    }

    private fun stopTunnel() {
        if (stopThread?.isAlive == true) return
        stopping = true
        notifyState(STATE_DISCONNECTING)
        connectionThread?.interrupt()
        stopThread = Thread {
            try { if (::backend.isInitialized) backend.setState(activeTunnel, Tunnel.State.DOWN, null) }
            catch (e: Exception) { addLog("WireGuard stop failed: ${e.message}") }
            stopSelf()
        }
        stopThread?.name = "PhoenixTunnel-stop"
        stopThread?.start()
    }

    override fun onDestroy() {
        stopping = true
        connectionThread?.interrupt()
        if (Thread.currentThread() !== connectionThread) connectionThread?.join(5_000)
        if (Thread.currentThread() !== stopThread) stopThread?.join(5_000)
        notifyState(STATE_DISCONNECTING)
        addLog("stopping tunnel service")
        if (hevStarted) try { TProxyService.TProxyStopService() } catch (e: Exception) { addLog("hev stop failed: ${e.message}") }
        hevStarted = false
        sshEngine?.disconnect()
        sshEngine = null
        try { backend.setState(activeTunnel, Tunnel.State.DOWN, null) } catch (_: Exception) { }
        try { vpnInterface?.close() } catch (_: Exception) { }
        addLog("closing VPN interface")
        vpnInterface = null
        notifyState(STATE_DISCONNECTED)
        super.onDestroy()
    }

    companion object {
        private const val WG_HANDSHAKE_TIMEOUT_MS = 30_000L
        private const val WG_STATS_POLL_MS = 1_000L

        fun hasWireGuardPeerTraffic(peerRxBytes: Long, peerTxBytes: Long): Boolean = peerRxBytes > 0L && peerTxBytes > 0L
        fun isUiStateConnected(state: String): Boolean = state == STATE_CONNECTED

        const val EXTRA_CONFIG = "config"
        const val EXTRA_PROTOCOL = "protocol"
        const val ACTION_TUNNEL_STOP = "com.phoenix.vpn.TUNNEL_STOP"
        const val ACTION_TUNNEL_UP = "com.phoenix.vpn.TUNNEL_UP"
        const val ACTION_TUNNEL_DOWN = "com.phoenix.vpn.TUNNEL_DOWN"
        const val ACTION_TUNNEL_STATE = "com.phoenix.vpn.TUNNEL_STATE"
        const val EXTRA_STATE = "state"
        const val STATE_CONNECTING = "connecting"
        const val STATE_WAITING_HANDSHAKE = "waiting-handshake"
        const val STATE_CONNECTED = "connected"
        const val STATE_DISCONNECTING = "disconnecting"
        const val STATE_DISCONNECTED = "disconnected"
    }
}
