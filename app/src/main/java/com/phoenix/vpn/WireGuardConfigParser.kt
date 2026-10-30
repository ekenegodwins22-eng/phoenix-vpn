package com.phoenix.vpn

data class WireGuardConfig(val privateKey: String, val addresses: List<String>, val dns: List<String>, val publicKey: String, val endpoint: String, val allowedIps: List<String>, val persistentKeepalive: Int?)
object WireGuardConfigParser {
    fun parse(text: String): WireGuardConfig {
        var section = ""; val values = mutableMapOf<String, MutableMap<String,String>>()
        text.lineSequence().forEachIndexed { i, raw ->
            val line = raw.substringBefore('#').substringBefore(';').trim(); if (line.isEmpty()) return@forEachIndexed
            if (line.startsWith("[") && line.endsWith("]")) { section = line.substring(1,line.length-1).trim().lowercase(); require(section in setOf("interface","peer")) { "Line ${i+1}: unsupported section" }; values.getOrPut(section){mutableMapOf()}; return@forEachIndexed }
            val split = line.indexOf('='); require(split > 0 && section.isNotEmpty()) { "Line ${i+1}: expected Key = Value inside a section" }
            val key = line.substring(0,split).trim().lowercase(); val value = line.substring(split+1).trim(); require(value.isNotEmpty()) { "Line ${i+1}: empty $key" }; values.getValue(section)[key]=value
        }
        val iface=values["interface"] ?: throw IllegalArgumentException("Missing [Interface] section"); val peer=values["peer"] ?: throw IllegalArgumentException("Missing [Peer] section")
        val privateKey=iface["privatekey"] ?: throw IllegalArgumentException("Missing Interface PrivateKey"); val addresses=iface["address"]?.split(",")?.map{it.trim()}?.filter{it.isNotEmpty()} ?: throw IllegalArgumentException("Missing Interface Address")
        val publicKey=peer["publickey"] ?: throw IllegalArgumentException("Missing Peer PublicKey"); val endpoint=peer["endpoint"] ?: throw IllegalArgumentException("Missing Peer Endpoint")
        require(endpoint.contains(":") && !endpoint.startsWith("PLACEHOLDER")) { "Invalid or placeholder Endpoint" }; require(!privateKey.startsWith("PLACEHOLDER") && !publicKey.startsWith("PLACEHOLDER")) { "Replace PLACEHOLDER keys before use" }
        val allowed=peer["allowedips"]?.split(",")?.map{it.trim()}?.filter{it.isNotEmpty()} ?: throw IllegalArgumentException("Missing Peer AllowedIPs")
        val keep=peer["persistentkeepalive"]?.toIntOrNull(); if(peer["persistentkeepalive"]!=null && keep==null) throw IllegalArgumentException("PersistentKeepalive must be an integer")
        return WireGuardConfig(privateKey,addresses,iface["dns"]?.split(",")?.map{it.trim()}?.filter{it.isNotEmpty()}?: emptyList(),publicKey,endpoint,allowed,keep)
    }
}
