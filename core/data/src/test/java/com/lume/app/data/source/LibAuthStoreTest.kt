package com.lume.app.data.source

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.data.security.SecretSealer
import com.lume.app.data.security.TestCipher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

/** Site tokens saved as plain text before v0.9.11 are encrypted on start and still sign in. */
@RunWith(AndroidJUnit4::class)
class LibAuthStoreTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun plainTokensAreEncryptedAndStillRead() = runBlocking {
        val file = context.preferencesDataStoreFile("lib_auth")
        val raw = """{"auth":{"id":7},"token":{"token_type":"Bearer","access_token":"site-plain-token"}}"""
        val writerJob = Job()
        PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + writerJob)) { file }
            .edit { it[stringPreferencesKey("token_mangalib")] = raw }
        writerJob.cancelAndJoin()

        val store = LibAuthStore(context, SecretSealer(TestCipher()))
        assertEquals("Bearer site-plain-token", store.authorizationHeader("mangalib"))

        store.sealStored()
        assertFalse(file.readBytes().decodeToString().contains("site-plain-token"))
        assertEquals("Bearer site-plain-token", LibAuthStore(context, SecretSealer(TestCipher())).authorizationHeader("mangalib"))

        store.saveRaw("remanga", raw.replace("site-plain-token", "second-token"))
        assertFalse(file.readBytes().decodeToString().contains("second-token"))
        assertEquals("Bearer second-token", store.authorizationHeader("remanga"))

        // The key is gone (for example after moving to a new phone): signed out, no crash.
        assertNull(LibAuthStore(context, SecretSealer(TestCipher(broken = true))).getToken("mangalib"))
    }
}
