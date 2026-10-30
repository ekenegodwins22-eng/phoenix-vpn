package com.phoenix.vpn

import org.junit.Assert.*
import org.junit.Test

class TweakProfileJsonParserTest {
    @Test fun parsesProfile(){val p=TweakProfileJsonParser.parse("""{"name":"MTN","protocol":"SSH","payload":"[host][crlf]"}""");assertEquals("MTN",p.name);assertEquals("[host][crlf]",p.payload)}
    @Test fun rejectsMissingName(){try{TweakProfileJsonParser.parse("""{"protocol":"SSH","payload":"hello"}""");fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("name"))}}
    @Test fun acceptsFeedTweakWithEmptyPayloadAndKeepsTarget(){val p=TweakProfileJsonParser.parse("""{"name":"Wikipedia","host":"en.m.wikipedia.org","port":443,"protocol":"SSH_DIRECT","payload":""}""");assertEquals("",p.payload);assertEquals("en.m.wikipedia.org",p.host);assertEquals(443,p.port)}
}
