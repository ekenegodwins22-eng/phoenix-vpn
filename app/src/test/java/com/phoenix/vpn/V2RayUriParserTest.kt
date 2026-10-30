package com.phoenix.vpn

import org.junit.Assert.*
import org.junit.Test
import java.util.Base64

class V2RayUriParserTest {
    @Test fun parsesVmessJson(){val b=Base64.getEncoder().encodeToString("""{"add":"vpn.example","port":"443","id":"uuid","ps":"demo","net":"ws","tls":"tls"}""".toByteArray());val c=V2RayUriParser.parse("vmess://$b");assertEquals("vpn.example",c.server);assertEquals(443,c.port);assertTrue(c.tls)}
    @Test fun parsesVless(){val c=V2RayUriParser.parse("vless://uuid@vpn.example:443?security=tls&type=ws&sni=front.example#test");assertEquals("vless",c.protocol);assertEquals("front.example",c.sni);assertTrue(c.tls)}
    @Test fun rejectsUnknownScheme(){try{V2RayUriParser.parse("ss://abc");fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("vmess"))}}
}
