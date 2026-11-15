package com.lume.app.data.nearby

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NearbyQrTest {
    @Test
    fun roundTripIpv4() {
        val text = NearbyQr.encode("192.168.1.20", 45123, "Anna's phone & tablet")
        assertEquals(NearbyQrTarget("192.168.1.20", 45123, "Anna's phone & tablet"), NearbyQr.parse(text))
    }

    @Test
    fun roundTripUnicodeName() {
        val text = NearbyQr.encode("10.0.0.5", 80, "Телефон Ани")
        assertEquals("Телефон Ани", NearbyQr.parse(text)?.name)
    }

    @Test
    fun roundTripIpv6() {
        val text = NearbyQr.encode("fe80::1", 9000, "x")
        assertEquals("lume-nearby://[fe80::1]:9000?name=x", text)
        assertEquals(NearbyQrTarget("fe80::1", 9000, "x"), NearbyQr.parse(text))
    }

    @Test
    fun nameIsOptional() {
        assertEquals(NearbyQrTarget("host.local", 1234, null), NearbyQr.parse("lume-nearby://host.local:1234"))
    }

    @Test
    fun rejectsMalformed() {
        listOf(
            "",
            "https://192.168.1.2:80",
            "lume-nearby://",
            "lume-nearby://192.168.1.2",
            "lume-nearby://192.168.1.2:0",
            "lume-nearby://192.168.1.2:70000",
            "lume-nearby://192.168.1.2:abc",
            "lume-nearby://:80",
            "lume-nearby://[::1:80",
            "lume-nearby://[]:80",
            "lume-nearby://user@1.2.3.4:80",
            "lume-nearby://a b:80",
            "lume-nearby://1.2.3.4:80:90",
        ).forEach { assertNull(it, NearbyQr.parse(it)) }
    }

    @Test
    fun badNameEncodingKeepsAddress() {
        assertEquals(NearbyQrTarget("1.2.3.4", 80, null), NearbyQr.parse("lume-nearby://1.2.3.4:80?name=%zz"))
    }

    @Test
    fun longNameIsCut() {
        val parsed = NearbyQr.parse(NearbyQr.encode("1.2.3.4", 80, "n".repeat(500)))
        assertEquals(64, parsed?.name?.length)
    }
}
