package com.phoenix.vpn

enum class TunnelProtocol(val label: String) {
    WIREGUARD("WireGuard"),
    SSH_DIRECT("SSH > DIRECT"),
    SSH_SSL_TLS("SSH > SSL/TLS"),
    SSH_SSL_PAYLOAD("SSH > SSL/PAYLOAD"),
    SSH_DNSTT("SSH > DNSTT"),
    V2RAY_VMESS("V2RAY > VMESS"),
    V2RAY_VLESS("V2RAY > VLESS"),
    UDP_HYSTERIA("UDP > HYSTERIA");

    companion object {
        fun fromLabel(value: String) = entries.firstOrNull {
            it.label.equals(value.trim(), true) || it.name.equals(value.trim(), true)
        } ?: throw IllegalArgumentException("Unsupported protocol: $value")
    }
}

interface TunnelEngine {
    val protocol: TunnelProtocol
    fun validate(config: String): String
    fun connect(config: String)
}

class WireGuardEngine : TunnelEngine {
    override val protocol = TunnelProtocol.WIREGUARD
    override fun validate(config: String) = WireGuardConfigParser.parse(config).let { "Valid WireGuard profile for ${it.endpoint}" }
    override fun connect(config: String) { WireGuardConfigParser.parse(config) }
}

class StubTunnelEngine(override val protocol: TunnelProtocol) : TunnelEngine {
    override fun validate(config: String) = "${protocol.label}: engine not yet implemented"
    override fun connect(config: String) { android.util.Log.i("PhoenixTunnel", "engine not yet implemented: ${protocol.label}") }
}

object TunnelEngines {
    fun forProtocol(protocol: TunnelProtocol, context: android.content.Context? = null, protectSocket: (java.net.Socket) -> Boolean = { true }, log: (String) -> Unit = { android.util.Log.i("PhoenixTunnel", it) }): TunnelEngine = when (protocol) {
        TunnelProtocol.WIREGUARD -> WireGuardEngine()
        TunnelProtocol.SSH_DIRECT, TunnelProtocol.SSH_SSL_TLS, TunnelProtocol.SSH_SSL_PAYLOAD ->
            SshEngine(requireNotNull(context) { "Context is required for SSH" }, protocol, protectSocket, log)
        else -> StubTunnelEngine(protocol)
    }
}
