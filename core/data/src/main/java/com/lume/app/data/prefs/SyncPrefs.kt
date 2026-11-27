package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.lume.app.data.security.SecretSealer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

data class SyncSettings(
    /** [OFF], [FOLDER] or [WEBDAV]. */
    val backend: String = OFF,
    /** The picked folder (a tree URI) for [FOLDER]. */
    val folder: String? = null,
    val webDavUrl: String = "",
    val webDavUser: String = "",
    val webDavPassword: String = "",
    /** Encrypts the sync files when not empty; every device needs the same one. */
    val passphrase: String = "",
    val intervalHours: Int = 6,
) {
    val ready: Boolean
        get() = when (backend) {
            FOLDER -> folder != null
            WEBDAV -> webDavUrl.startsWith("http://") || webDavUrl.startsWith("https://")
            else -> false
        }

    companion object {
        const val OFF = "off"
        const val FOLDER = "folder"
        const val WEBDAV = "webdav"
        val BACKENDS = listOf(OFF, FOLDER, WEBDAV)
    }
}

data class SyncStatus(
    val lastAt: Long = 0L,
    val error: String? = null,
    /** Other devices whose files the last sync read. */
    val devices: Int = 0,
    /** Records the last sync changed on this device. */
    val changes: Int = 0,
)

/** Sync between devices: the settings and the status of the last run. */
abstract class SyncPrefs(context: Context, scope: CoroutineScope, secrets: SecretSealer) :
    AiPrefs(context, scope, secrets) {
    private val syncBackendKey = stringPreferencesKey("sync_backend")
    private val syncFolderKey = stringPreferencesKey("sync_folder")
    private val syncWebDavUrlKey = stringPreferencesKey("sync_webdav_url")
    private val syncWebDavUserKey = stringPreferencesKey("sync_webdav_user")
    private val syncWebDavPasswordKey = stringPreferencesKey("sync_webdav_password")
    private val syncPassphraseKey = stringPreferencesKey("sync_passphrase")
    private val syncIntervalKey = intPreferencesKey("sync_interval_hours")
    private val syncLastAtKey = longPreferencesKey("sync_last_at")
    private val syncLastErrorKey = stringPreferencesKey("sync_last_error")
    private val syncDevicesKey = intPreferencesKey("sync_devices")
    private val syncChangesKey = intPreferencesKey("sync_changes")

    /**
     * Sync between devices (Settings → Sync). All of it belongs to this install (a folder
     * permission, a server password), so it lives in the private store and never in backups.
     */
    val syncSettings: StateFlow<SyncSettings> = privateState { p ->
        SyncSettings(
            backend = p[syncBackendKey]?.takeIf { it in SyncSettings.BACKENDS } ?: SyncSettings.OFF,
            folder = p[syncFolderKey]?.takeIf(String::isNotBlank),
            webDavUrl = p[syncWebDavUrlKey].orEmpty(),
            webDavUser = p[syncWebDavUserKey].orEmpty(),
            webDavPassword = p.secret(syncWebDavPasswordKey).orEmpty(),
            passphrase = p.secret(syncPassphraseKey).orEmpty(),
            intervalHours = p[syncIntervalKey]?.takeIf { it in AppPreferences.SYNC_INTERVALS } ?: 6,
        )
    }

    /** The last sync: when it finished, what failed, how many other devices it saw. */
    val syncStatus: StateFlow<SyncStatus> = privateState { p ->
        SyncStatus(
            lastAt = p[syncLastAtKey] ?: 0L,
            error = p[syncLastErrorKey]?.takeIf(String::isNotBlank),
            devices = p[syncDevicesKey] ?: 0,
            changes = p[syncChangesKey] ?: 0,
        )
    }

    suspend fun setSyncSettings(settings: SyncSettings) {
        context.privateDataStore.edit {
            it[syncBackendKey] = settings.backend
            if (settings.folder == null) it.remove(syncFolderKey) else it[syncFolderKey] = settings.folder
            it[syncWebDavUrlKey] = settings.webDavUrl.trim()
            it[syncWebDavUserKey] = settings.webDavUser.trim()
            it[syncWebDavPasswordKey] = secrets.seal(settings.webDavPassword)
            it[syncPassphraseKey] = secrets.seal(settings.passphrase)
            it[syncIntervalKey] = settings.intervalHours
        }
    }

    suspend fun setSyncStatus(status: SyncStatus) {
        context.privateDataStore.edit {
            it[syncLastAtKey] = status.lastAt
            if (status.error == null) it.remove(syncLastErrorKey) else it[syncLastErrorKey] = status.error
            it[syncDevicesKey] = status.devices
            it[syncChangesKey] = status.changes
        }
    }
}
