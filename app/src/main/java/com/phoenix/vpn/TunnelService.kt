package com.phoenix.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import androidx.core.app.NotificationCompat
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import java.io.ByteArrayInputStream

class TunnelService : VpnService() {
    private lateinit var backend: GoBackend
    private val activeTunnel = object : Tunnel {
        override fun getName() = "PhoenixTunnel"
        override fun onStateChange(newState: Tunnel.State) { addLog("WireGuard state: $newState") }
    }
    override fun onCreate() { super.onCreate(); backend = GoBackend(this); createChannel() }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(71, notification("Preparing VPN"))
        val source = intent?.getStringExtra(EXTRA_CONFIG)
        if (source.isNullOrBlank()) { stopSelf(); return START_NOT_STICKY }
        Thread {
            try {
                val config = Config.parse(ByteArrayInputStream(source.toByteArray()))
                backend.setState(activeTunnel, Tunnel.State.UP, config)
                addLog("WireGuard tunnel is up")
                getSystemService(NotificationManager::class.java).notify(71, notification("Connected"))
            } catch (e: Exception) {
                addLog("Tunnel start failed: ${e.message}")
                stopSelf()
            }
        }.start()
        return START_NOT_STICKY
    }
    private fun addLog(message: String) { ConfigRepository(this).addLog(message) }
    private fun createChannel() { if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel("phoenix_vpn", "PhoenixTunnel", NotificationManager.IMPORTANCE_LOW)) }
    private fun notification(text: String): Notification {
        val pi = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this, "phoenix_vpn").setSmallIcon(android.R.drawable.stat_sys_warning).setContentTitle("PhoenixTunnel").setContentText(text).setContentIntent(pi).setOngoing(true).build()
    }
    override fun onDestroy() { try { backend.setState(activeTunnel, Tunnel.State.DOWN, null) } catch (_: Exception) {}; super.onDestroy() }
    companion object { const val EXTRA_CONFIG = "config" }
}
