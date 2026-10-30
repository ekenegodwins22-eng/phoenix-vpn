package com.phoenix.vpn

import org.junit.Assert.*
import org.junit.Test

class WireGuardConfigParserTest {
    private val good = """[Interface]
PrivateKey = priv
Address = 10.0.0.2/32
DNS = 1.1.1.1
[Peer]
PublicKey = pub
Endpoint = vpn.test:51820
AllowedIPs = 0.0.0.0/0
PersistentKeepalive = 25"""
    @Test fun parsesBasicConfig() { val c=WireGuardConfigParser.parse(good); assertEquals("vpn.test:51820",c.endpoint); assertEquals(listOf("10.0.0.2/32"),c.addresses); assertEquals(25,c.persistentKeepalive) }
    @Test fun rejectsMissingPeer() { try { WireGuardConfigParser.parse("[Interface]\nPrivateKey=x\nAddress=10.0.0.1/32"); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("Peer")) } }
    @Test fun rejectsPlaceholderKeys() { try { WireGuardConfigParser.parse(good.replace("priv","PLACEHOLDER_PRIVATE_KEY")); fail() } catch(e:IllegalArgumentException) { assertTrue(e.message!!.contains("PLACEHOLDER")) } }
}
