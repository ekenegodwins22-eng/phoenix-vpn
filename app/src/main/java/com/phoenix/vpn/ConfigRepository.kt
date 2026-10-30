package com.phoenix.vpn

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class TunnelDocument(val name:String,val protocol:TunnelProtocol,val content:String,val createdAt:Long=System.currentTimeMillis())
class ConfigRepository(context:Context) {
    private val root=File(context.filesDir,"phoenix").apply{mkdirs()}; private val profiles=File(root,"profiles").apply{mkdirs()}; private val tweaks=File(root,"tweaks.json"); private val logFile=File(root,"tunnel.log")
    fun listProfiles():List<TunnelDocument> = profiles.listFiles { f -> f.extension in setOf("conf", "ssh") }?.sortedBy { it.name }?.map { file ->
        val protocol = if (file.extension == "ssh") runCatching { TunnelProtocol.valueOf(org.json.JSONObject(file.readText()).optString("protocol", "SSH_DIRECT")) }.getOrDefault(TunnelProtocol.SSH_DIRECT) else TunnelProtocol.WIREGUARD
        TunnelDocument(file.nameWithoutExtension, protocol, file.readText(), file.lastModified())
    } ?: emptyList()
    fun saveProfile(name:String,content:String,protocol:TunnelProtocol=TunnelProtocol.WIREGUARD){
        require(name.matches(Regex("[A-Za-z0-9 _.-]{1,48}"))){"Use a simple profile name"}
        val extension = if (protocol == TunnelProtocol.WIREGUARD) "conf" else "ssh"
        if (protocol == TunnelProtocol.WIREGUARD) WireGuardConfigParser.parse(content) else {
            require(protocol == TunnelProtocol.SSH_DIRECT || protocol == TunnelProtocol.SSH_SSL_TLS || protocol == TunnelProtocol.SSH_SSL_PAYLOAD) { "${protocol.label} is not implemented" }
            SshProfile.parse(content)
            val profileJson = org.json.JSONObject(content).put("protocol", protocol.name).toString(2)
            File(profiles,"${name.trim()}.$extension").writeText(profileJson)
            return
        }
        File(profiles,"${name.trim()}.$extension").writeText(content)
    }
    fun deleteProfile(name:String)=File(profiles,"${name}.conf").delete() || File(profiles,"${name}.ssh").delete()
    fun importProfile(name:String,content:String)=saveProfile(name,content,TunnelProtocol.WIREGUARD)
    fun listTweaks():List<TweakProfile>{if(!tweaks.exists())return emptyList(); val a=JSONArray(tweaks.readText()); return (0 until a.length()).map{TweakProfileJsonParser.parse(a.getJSONObject(it).toString())}}
    fun saveTweak(profile:TweakProfile){val all=listTweaks().filterNot{it.name==profile.name}.toMutableList();all.add(profile); val a=JSONArray();all.forEach{a.put(JSONObject().put("name",it.name).put("protocol",it.protocol).put("payload",it.payload).put("sni",it.sni).put("active",it.active))};tweaks.writeText(a.toString(2))}
    fun addLog(message:String){logFile.appendText("${SimpleDateFormat("yyyy-MM-dd HH:mm:ss",Locale.US).format(Date())}  $message\n")}
    fun logs()=if(logFile.exists())logFile.readText() else "No logs yet"
}
