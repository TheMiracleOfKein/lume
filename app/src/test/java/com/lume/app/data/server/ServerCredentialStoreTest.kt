package com.lume.app.data.server

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Android Keystore is not available in JVM tests, so the cipher is a fake with the same
 * contract; the real [KeystoreCredentialCipher] is checked on a device (docs/DEVICE_TESTING.md).
 */
@RunWith(AndroidJUnit4::class)
class ServerCredentialStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    /** Reversible and not plain text: bytes are flipped and a marker byte is prepended. */
    private class FakeCipher(var broken: Boolean = false) : CredentialCipher {
        override fun encrypt(plain: ByteArray): ByteArray = byteArrayOf(MARK) + plain.map { (it.toInt() xor 0x5A).toByte() }

        override fun decrypt(sealed: ByteArray): ByteArray {
            check(!broken && sealed.firstOrNull() == MARK) { "cannot decrypt" }
            return sealed.drop(1).map { (it.toInt() xor 0x5A).toByte() }.toByteArray()
        }

        companion object {
            const val MARK: Byte = 0x7E
        }
    }

    @Test
    fun roundTripSurvivesANewStore() = runBlocking {
        val store = ServerCredentialStore(context, FakeCipher())
        store.put("server-a", ServerCredentials(username = "alice", password = "hunter2"))
        store.put("server-b", ServerCredentials(apiKey = "KEY-123"))
        assertEquals("alice", store.get("server-a")?.username)

        // A fresh instance only has what reached the file.
        val reopened = ServerCredentialStore(context, FakeCipher())
        assertNull(reopened.get("server-a"))
        reopened.load()
        assertEquals(ServerCredentials(username = "alice", password = "hunter2"), reopened.get("server-a"))
        assertEquals("KEY-123", reopened.get("server-b")?.apiKey)
        assertTrue(reopened.get("server-b")!!.hasApiKey)
        assertFalse(reopened.get("server-b")!!.hasPassword)

        store.remove("server-a")
        store.remove("server-b")
    }

    @Test
    fun theFileHoldsNoPlainText() = runBlocking {
        val store = ServerCredentialStore(context, FakeCipher())
        val credentials = ServerCredentials(username = "alice", password = "hunter2-unique", apiKey = "KEY-unique")
        // What goes into DataStore is the sealed, Base64 text.
        val sealed = store.encode(credentials)
        assertFalse("hunter2-unique" in sealed)
        assertFalse("KEY-unique" in sealed)
        assertFalse("alice" in sealed)
        assertEquals(credentials, store.decode(sealed))
    }

    @Test
    fun emptyCredentialsAreNotKept() = runBlocking {
        val store = ServerCredentialStore(context, FakeCipher())
        store.put("server-empty", ServerCredentials(username = "bob", password = "x"))
        store.put("server-empty", ServerCredentials())
        assertNull(store.get("server-empty"))
        val reopened = ServerCredentialStore(context, FakeCipher())
        reopened.load()
        assertNull(reopened.get("server-empty"))
    }

    @Test
    fun anEntryThatCannotBeDecryptedIsSkipped() = runBlocking {
        val good = ServerCredentialStore(context, FakeCipher())
        good.put("server-lost", ServerCredentials(apiKey = "k"))
        // For example after a restore on another device: the key is gone.
        val lost = ServerCredentialStore(context, FakeCipher(broken = true))
        lost.load()
        assertNull(lost.get("server-lost"))
        good.remove("server-lost")
    }

    @Test
    fun removeForgetsTheServer() = runBlocking {
        val store = ServerCredentialStore(context, FakeCipher())
        store.put("server-gone", ServerCredentials(apiKey = "k"))
        store.remove("server-gone")
        assertNull(store.get("server-gone"))
        val reopened = ServerCredentialStore(context, FakeCipher())
        reopened.load()
        assertNull(reopened.get("server-gone"))
    }
}
