package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Up to v0.6.1 tokens shared the settings file; they move to their own store on start. Up to
 * v0.9.10 that store was plain text; its secrets are encrypted on start.
 *
 * One test only: the DataStore delegates keep their first file for the whole test process.
 */
@RunWith(AndroidJUnit4::class)
class PrivatePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tokensMoveOutOfTheSettingsFileAndAreEncrypted() = runBlocking {
        val file = context.preferencesDataStoreFile("lume_prefs")
        val privateFile = context.preferencesDataStoreFile("lume_private")
        val writerJob = Job()
        val writerScope = CoroutineScope(Dispatchers.IO + writerJob)
        PreferenceDataStoreFactory.create(scope = writerScope) { file }.edit {
            it[stringPreferencesKey("anilist_access_token")] = "anilist-token"
            it[stringPreferencesKey("tracker_myanimelist_refresh_token")] = "mal-refresh"
            it[stringPreferencesKey("auto_backup_folder")] = "content://tree/backups"
            it[booleanPreferencesKey("tracker_auto_link_enabled")] = false
            it[booleanPreferencesKey("theme_pure_black")] = true
        }
        PreferenceDataStoreFactory.create(scope = writerScope) { privateFile }.edit {
            it[stringPreferencesKey("shikimori_access_token")] = "shiki-plain-token"
            it[stringPreferencesKey("tracker_kitsu_access_token")] = "kitsu-plain-token"
            it[stringPreferencesKey("sync_webdav_password")] = "dav-plain-password"
            it[stringPreferencesKey("sync_webdav_url")] = "https://dav.example"
        }
        writerJob.cancelAndJoin()

        val scope = CoroutineScope(Dispatchers.Default + Job())
        val prefs = AppPreferences(context, scope, SecretSealer(TestCipher()))

        assertEquals("anilist-token", prefs.anilistAccessToken.value)
        assertEquals("mal-refresh", prefs.trackerValue("myanimelist", "refresh_token"))
        assertEquals("content://tree/backups", prefs.autoBackupFolder.value)
        assertFalse(prefs.trackerAutoLink.value)
        assertTrue(prefs.pureBlack.value)
        assertEquals("shiki-plain-token", prefs.shikimoriAccessToken.value)
        assertEquals("kitsu-plain-token", prefs.trackerValue("kitsu", "access_token"))
        assertEquals("dav-plain-password", prefs.syncSettings.value.webDavPassword)
        assertEquals("https://dav.example", prefs.syncSettings.value.webDavUrl)

        val exported = prefs.exportSettings().map { it.first }.toSet()
        assertTrue("theme_pure_black" in exported)
        assertTrue("tracker_auto_link_enabled" in exported)
        assertFalse(exported.any { it.contains("token") || it == "auto_backup_folder" })

        // The settings file itself no longer holds them.
        assertFalse(file.readBytes().decodeToString().contains("anilist-token"))

        prefs.setTrackerValue("kitsu", "refresh_token", "kitsu-new-refresh")
        prefs.trackerValues.first { "kitsu_refresh_token" in it }
        assertEquals("kitsu-new-refresh", prefs.trackerValue("kitsu", "refresh_token"))

        // The private file holds no secret as plain text; other values stay readable.
        val stored = privateFile.readBytes().decodeToString()
        listOf("anilist-token", "mal-refresh", "shiki-plain-token", "kitsu-plain-token", "dav-plain-password", "kitsu-new-refresh")
            .forEach { assertFalse("$it is stored as plain text", stored.contains(it)) }
        assertTrue(stored.contains("https://dav.example"))
        assertTrue(stored.contains("content://tree/backups"))

        prefs.setAniListAccessToken(null)
        assertNull(prefs.anilistAccessToken.first { it == null })
    }
}
