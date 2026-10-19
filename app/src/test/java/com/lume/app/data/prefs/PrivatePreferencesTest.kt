package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
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

/** Up to v0.6.1 tokens shared the settings file; they move to their own store on start. */
@RunWith(AndroidJUnit4::class)
class PrivatePreferencesTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun tokensMoveOutOfTheSettingsFile() = runBlocking {
        val file = context.preferencesDataStoreFile("lume_prefs")
        val writerJob = Job()
        val old = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + writerJob)) { file }
        old.edit {
            it[stringPreferencesKey("anilist_access_token")] = "anilist-token"
            it[stringPreferencesKey("tracker_myanimelist_refresh_token")] = "mal-refresh"
            it[stringPreferencesKey("auto_backup_folder")] = "content://tree/backups"
            it[booleanPreferencesKey("tracker_auto_link_enabled")] = false
            it[booleanPreferencesKey("theme_pure_black")] = true
        }
        writerJob.cancelAndJoin()

        val scope = CoroutineScope(Dispatchers.Default + Job())
        val prefs = AppPreferences(context, scope)

        assertEquals("anilist-token", prefs.anilistAccessToken.value)
        assertEquals("mal-refresh", prefs.trackerValue("myanimelist", "refresh_token"))
        assertEquals("content://tree/backups", prefs.autoBackupFolder.value)
        assertFalse(prefs.trackerAutoLink.value)
        assertTrue(prefs.pureBlack.value)

        val exported = prefs.exportSettings().map { it.first }.toSet()
        assertTrue("theme_pure_black" in exported)
        assertTrue("tracker_auto_link_enabled" in exported)
        assertFalse(exported.any { it.contains("token") || it == "auto_backup_folder" })

        // The settings file itself no longer holds them.
        assertFalse(file.readBytes().decodeToString().contains("anilist-token"))

        prefs.setAniListAccessToken(null)
        assertNull(prefs.anilistAccessToken.first { it == null })
    }
}
