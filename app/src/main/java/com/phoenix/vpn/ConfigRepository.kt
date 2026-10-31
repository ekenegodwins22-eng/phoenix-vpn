package com.phoenix.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TunnelDocument(val name: String, val protocol: TunnelProtocol, val content: String, val createdAt: Long = System.currentTimeMillis())

class ConfigRepository(context: Context) {
    private val root = File(context.filesDir, "phoenix").apply { mkdirs() }
    private val profiles = File(root, "profiles").apply { mkdirs() }
    private val tweaks = File(root, "tweaks.json")
    private val logFile = File(root, "tunnel.log")
    private val selFile = File(root, "selected.txt")

    fun selectedProfile(): String? = if (selFile.exists()) selFile.readText().trim().ifEmpty { null } else null
    fun selectProfile(name: String) { selFile.writeText(name) }

    fun listProfiles(): List<TunnelDocument> {
        val files = profiles.listFiles { f -> f.extension == "conf" || f.extension == "ssh" } ?: return emptyList()
        val docs = ArrayList<TunnelDocument>()
        for (file in files.sortedBy { it.name }) {
            val proto: TunnelProtocol
            if (file.extension == "ssh") {
                val pn = runCatching { JSONObject(file.readText()).optString("protocol", "SSH_DIRECT") }.getOrDefault("SSH_DIRECT")
                proto = runCatching { TunnelProtocol.valueOf(pn) }.getOrDefault(TunnelProtocol.SSH_DIRECT)
            } else {
                proto = TunnelProtocol.WIREGUARD
            }
            docs.add(TunnelDocument(file.nameWithoutExtension, proto, file.readText(), file.lastModified()))
        }
        return docs
    }

    fun saveProfile(name: String, content: String, protocol: TunnelProtocol = TunnelProtocol.WIREGUARD) {
        require(name.matches(Regex("[A-Za-z0-9 _.-]{1,48}"))) { "Use a simple profile name" }
        if (protocol == TunnelProtocol.WIREGUARD) {
            WireGuardConfigParser.parse(content)
            File(profiles, name.trim() + ".conf").writeText(content)
        } else {
            require(protocol == TunnelProtocol.SSH_DIRECT || protocol == TunnelProtocol.SSH_SSL_TLS || protocol == TunnelProtocol.SSH_SSL_PAYLOAD) { protocol.label + " is not implemented" }
            SshProfile.parse(content)
            val json = JSONObject(content).put("protocol", protocol.name).toString(2)
            File(profiles, name.trim() + ".ssh").writeText(json)
        }
    }

    fun saveFeedServer(name:String,host:String,port:Int,type:String){
        require(name.matches(Regex("[A-Za-z0-9 _.-]{1,48}"))) { "Use a simple profile name" }
        require(host.isNotBlank() && port in 1..65535) { "Invalid server from feed" }
        if(type.equals("wg+ssh",true)){
            val content="# FEED PROFILE: replace both PLACEHOLDER keys and confirm server peer details before connecting.\n[Interface]\nPrivateKey = PLACEHOLDER_REPLACE_WITH_CLIENT_PRIVATE_KEY\nAddress = 10.13.13.2/32\nDNS = 1.1.1.1\n\n[Peer]\nPublicKey = PLACEHOLDER_REPLACE_WITH_SERVER_PUBLIC_KEY\nEndpoint = $host:51820\nAllowedIPs = 0.0.0.0/0\nPersistentKeepalive = 25\n"
            File(profiles,name.trim()+".conf").writeText(content)
        }
        if(type.equals("node",true)||type.equals("wg+ssh",true)){
            val content=JSONObject().put("host",host).put("port",port).put("username","REPLACE_WITH_SSH_USERNAME").put("password","REPLACE_WITH_SSH_PASSWORD").put("payload","").put("sniffedHost","").put("protocol",TunnelProtocol.SSH_DIRECT.name).put("feedNote","Replace username and password before connecting").toString(2)
            File(profiles,name.trim()+".ssh").writeText(content)
        }
    }

    fun importProfile(name: String, content: String) = saveProfile(name, content)

    fun deleteProfile(name: String): Boolean {
        val a = File(profiles, name + ".conf").delete()
        val b = File(profiles, name + ".ssh").delete()
        return a || b
    }

    fun listTweaks(): List<TweakProfile> {
        if (!tweaks.exists()) return emptyList()
        val arr = JSONArray(tweaks.readText())
        val out = ArrayList<TweakProfile>()
        for (i in 0 until arr.length()) out.add(TweakProfileJsonParser.parse(arr.getJSONObject(i).toString()))
        return out
    }

    fun saveTweak(profile: TweakProfile) {
        val all = listTweaks().filterNot { it.name == profile.name }.toMutableList()
        all.add(profile)
        val arr = JSONArray()
        for (t in all) {
            val o = JSONObject()
            o.put("name", t.name)
            o.put("protocol", t.protocol)
            o.put("payload", t.payload)
            o.put("sni", t.sni)
            o.put("active", t.active)
            if(t.host!=null)o.put("host",t.host)
            if(t.port!=null)o.put("port",t.port)
            arr.put(o)
        }
        tweaks.writeText(arr.toString(2))
    }

    fun addLog(message: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        logFile.appendText(stamp + "  " + message + "\n")
    }

    fun logs(): String = if (logFile.exists()) logFile.readText() else "No logs yet"
}
