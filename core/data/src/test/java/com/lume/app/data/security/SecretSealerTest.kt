package com.lume.app.data.security

import com.lume.app.data.server.CredentialCipher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reversible and not plain text, like the Keystore cipher; [broken] stands for a lost key. */
class TestCipher(var broken: Boolean = false, var failEncrypt: Boolean = false) : CredentialCipher {
    override fun encrypt(plain: ByteArray): ByteArray {
        check(!failEncrypt) { "keystore unavailable" }
        return byteArrayOf(MARK) + plain.map { (it.toInt() xor 0x5A).toByte() }
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        check(!broken && sealed.firstOrNull() == MARK) { "cannot decrypt" }
        return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
    }

    private companion object {
        const val MARK: Byte = 0x7E
    }
}

class SecretSealerTest {
    @Test
    fun sealsAndOpens() {
        val sealer = SecretSealer(TestCipher())
        val sealed = sealer.seal("secret-token")
        assertTrue(sealer.isSealed(sealed))
        assertFalse(sealed.contains("secret-token"))
        assertEquals("secret-token", SecretSealer(TestCipher()).open(sealed))
    }

    @Test
    fun plainValuesFromOlderVersionsReadAsTheyAre() {
        val sealer = SecretSealer(TestCipher())
        assertEquals("old-token", sealer.open("old-token"))
        assertNull(sealer.open(null))
    }

    @Test
    fun lostKeyReadsAsSignedOut() {
        val sealed = SecretSealer(TestCipher()).seal("secret-token")
        assertNull(SecretSealer(TestCipher(broken = true)).open(sealed))
    }

    @Test
    fun keystoreFailureKeepsThePlainValue() {
        val sealer = SecretSealer(TestCipher(failEncrypt = true))
        assertEquals("secret-token", sealer.seal("secret-token"))
    }

    @Test
    fun emptyAndSealedValuesAreLeftAlone() {
        val sealer = SecretSealer(TestCipher())
        assertEquals("", sealer.seal(""))
        val sealed = sealer.seal("x")
        assertEquals(sealed, sealer.seal(sealed))
    }
}
