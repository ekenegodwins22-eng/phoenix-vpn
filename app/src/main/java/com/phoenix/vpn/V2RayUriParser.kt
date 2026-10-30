package com.phoenix.vpn

import org.json.JSONObject

data class V2RayConfig(val protocol: String, val server: String, val port: Int, val id: String, val name: String, val security: String, val network: String, val tls: Boolean, val sni: String?)
object V2RayUriParser {
    fun parse(uri: String): V2RayConfig {
        val raw=uri.trim(); require(raw.startsWith("vmess://",true)||raw.startsWith("vless://",true)) { "URI must start with vmess:// or vless://" }
        val scheme=raw.substringBefore("://").lowercase()
        return if(scheme=="vmess") parseVmess(raw.substringAfter("://")) else parseVless(raw)
    }
    private fun parseVmess(body:String):V2RayConfig {
        val decoded=try { String(java.util.Base64.getMimeDecoder().decode(body.trim()),Charsets.UTF_8) } catch(e:Exception) { try { String(java.util.Base64.getUrlDecoder().decode(body.trim()),Charsets.UTF_8) } catch(inner:Exception) { throw IllegalArgumentException("Invalid vmess base64",inner) } }
        val j=try { JSONObject(decoded) } catch(e:Exception) { throw IllegalArgumentException("Invalid vmess JSON",e) }
        val host=j.optString("add",j.optString("address")); val port=j.optInt("port",0); val id=j.optString("id",j.optString("uuid"))
        require(host.isNotBlank() && port in 1..65535 && id.isNotBlank()) { "vmess JSON requires add, valid port, and id" }
        val tls=j.optString("tls").equals("tls",true); return V2RayConfig("vmess",host,port,id,j.optString("ps",host),j.optString("scy","auto"),j.optString("net","tcp"),tls,j.optString("sni").takeIf{it.isNotBlank()})
    }
    private fun parseVless(raw:String):V2RayConfig {
        val uri=try { java.net.URI(raw) } catch(e:Exception) { throw IllegalArgumentException("Invalid vless URI",e) }
        val port=uri.port; val id=uri.userInfo ?: ""; val query=uri.rawQuery.orEmpty().split('&').mapNotNull{ val x=it.split('=',limit=2); if(x.size==2) x[0] to java.net.URLDecoder.decode(x[1],"UTF-8") else null }.toMap()
        require(!uri.host.isNullOrBlank() && port in 1..65535 && id.isNotBlank()) { "vless URI requires UUID, host, and valid port" }
        return V2RayConfig("vless",uri.host,port,id,java.net.URLDecoder.decode(uri.fragment.orEmpty(),"UTF-8").ifBlank{uri.host},query["encryption"]?:"none",query["type"]?:"tcp",query["security"].let{it=="tls"||it=="reality"},query["sni"])
    }
}
