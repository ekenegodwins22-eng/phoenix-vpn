package com.phoenix.vpn

data class TweakProfile(val name: String, val protocol: String, val payload: String, val sni: String? = null, val active: Boolean = false)
object TweakProfileJsonParser {
    fun parse(json: String): TweakProfile {
        val obj=org.json.JSONObject(json); val name=obj.optString("name", "").trim(); val protocol=obj.optString("protocol", "").trim(); val payload=obj.optString("payload", "")
        require(name.isNotEmpty()) { "Tweak profile name is required" }; require(protocol.isNotEmpty()) { "Tweak profile protocol is required" }; require(payload.isNotBlank()) { "Tweak profile payload is required" }
        return TweakProfile(name,protocol,payload,obj.optString("sni").takeIf{it.isNotBlank()},obj.optBoolean("active",false))
    }
}
