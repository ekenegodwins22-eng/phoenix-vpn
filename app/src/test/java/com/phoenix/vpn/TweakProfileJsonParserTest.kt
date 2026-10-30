package com.phoenix.vpn

import org.junit.Assert.*
import org.junit.Test

class TweakProfileJsonParserTest {
    @Test fun parsesProfile(){val p=TweakProfileJsonParser.parse("""{"name":"MTN","protocol":"SSH","payload":"[host][crlf]"}""");assertEquals("MTN",p.name);assertEquals("[host][crlf]",p.payload)}
    @Test fun rejectsMissingName(){try{TweakProfileJsonParser.parse("""{"protocol":"SSH","payload":"hello"}""");fail()}catch(e:IllegalArgumentException){assertTrue(e.message!!.contains("name"))}}
}
