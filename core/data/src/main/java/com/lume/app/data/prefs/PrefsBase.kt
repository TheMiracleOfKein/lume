package com.lume.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lume.app.data.security.SecretSealer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

internal val Context.appDataStore by preferencesDataStore("lume_prefs")

/**
 * Values that belong to this install only: tracker tokens and app keys, and the automatic backup
 * folder (a permission only this install holds). Kept apart from the settings so Android's cloud
 * backup can keep the settings and leave this file out (see `res/xml/data_extraction_rules.xml`).
 */
internal val Context.privateDataStore by preferencesDataStore("lume_private")

internal val prefsJson = Json { ignoreUnknownKeys = true }

internal const val TRACKER_PREFIX = "tracker_"

private val PRIVATE_KEY_PARTS = listOf("token", "secret", "client_id", "user_id", "password", "verifier")
private val SECRET_KEY_PARTS = listOf("token", "secret", "password", "verifier", "passphrase")

/**
 * The shared base of the preference classes, which [AppPreferences] stacks into one: the two
 * stores, the helpers that read and write them, the move of old values and the backup of settings.
 */
abstract class PrefsBase(
    protected val context: Context,
    private val scope: CoroutineScope,
    /** Encrypts tokens, passwords and app secrets in [privateDataStore]; see [isSecretKey]. */
    protected val secrets: SecretSealer,
) {
    init {
        runBlocking {
            movePrivateValues()
            sealPrivateValues()
        }
    }

    private val initial: Preferences = runBlocking { context.appDataStore.data.first() }
    protected val current: StateFlow<Preferences> =
        context.appDataStore.data.stateIn(scope, SharingStarted.Eagerly, initial)
    private val initialPrivate: Preferences = runBlocking { context.privateDataStore.data.first() }
    protected val currentPrivate: StateFlow<Preferences> =
        context.privateDataStore.data.stateIn(scope, SharingStarted.Eagerly, initialPrivate)

    protected fun <T> state(read: (Preferences) -> T): StateFlow<T> =
        current.map(read).stateIn(scope, SharingStarted.Eagerly, read(initial))

    protected fun <T> privateState(read: (Preferences) -> T): StateFlow<T> =
        currentPrivate.map(read).stateIn(scope, SharingStarted.Eagerly, read(initialPrivate))

    protected suspend fun <T> put(key: Preferences.Key<T>, value: T) {
        context.appDataStore.edit { it[key] = value }
    }

    /** Up to v0.6.1 tokens lived in the settings file; they move to [privateDataStore] once. */
    private suspend fun movePrivateValues() {
        val moving = context.appDataStore.data.first().asMap().filterKeys { isPrivateKey(it.name) }
        if (moving.isEmpty()) return
        context.privateDataStore.edit { target ->
            @Suppress("UNCHECKED_CAST")
            moving.forEach { (key, value) -> target[key as Preferences.Key<Any>] = value }
        }
        context.appDataStore.edit { prefs -> moving.keys.forEach { prefs.remove(it) } }
    }

    /** Up to v0.9.10 the private values were plain text; the secret ones are encrypted once. */
    private suspend fun sealPrivateValues() {
        val plain = context.privateDataStore.data.first().asMap().mapNotNull { (key, value) ->
            if (value !is String || !isSecretKey(key.name) || secrets.isSealed(value)) return@mapNotNull null
            val sealed = secrets.seal(value)
            if (secrets.isSealed(sealed)) Pair(stringPreferencesKey(key.name), sealed) else null
        }
        if (plain.isEmpty()) return
        context.privateDataStore.edit { target -> plain.forEach { (key, sealed) -> target[key] = sealed } }
    }

    /** A secret value of the private store, decrypted. */
    protected fun Preferences.secret(key: Preferences.Key<String>): String? = secrets.open(this[key])

    /** Writes [value] to the private store, encrypted, or removes [key] when it is blank. */
    protected suspend fun putSecret(key: Preferences.Key<String>, value: String?) {
        context.privateDataStore.edit { if (value.isNullOrBlank()) it.remove(key) else it[key] = secrets.seal(value) }
    }

    /**
     * Every stored preference as (key, type, value) for backups. Account tokens, app keys and
     * the backup folder live in [privateDataStore] and stay out; the filter guards against strays.
     */
    fun exportSettings(): List<Triple<String, String, String>> = current.value.asMap().mapNotNull { (key, value) ->
        if (isPrivateKey(key.name)) return@mapNotNull null
        when (value) {
            is Boolean -> Triple(key.name, "bool", value.toString())
            is Int -> Triple(key.name, "int", value.toString())
            is Long -> Triple(key.name, "long", value.toString())
            is Double -> Triple(key.name, "double", value.toString())
            is Float -> Triple(key.name, "float", value.toString())
            is String -> Triple(key.name, "string", value)
            is Set<*> -> Triple(key.name, "set", prefsJson.encodeToString(value.map { it.toString() }))
            else -> null
        }
    }

    /** Writes settings from a backup back; unknown types and private keys are skipped. */
    suspend fun importSettings(values: List<Triple<String, String, String>>) {
        context.appDataStore.edit { prefs ->
            for ((name, type, raw) in values) {
                if (isPrivateKey(name)) continue
                runCatching {
                    when (type) {
                        "bool" -> prefs[booleanPreferencesKey(name)] = raw.toBooleanStrict()
                        "int" -> prefs[intPreferencesKey(name)] = raw.toInt()
                        "long" -> prefs[longPreferencesKey(name)] = raw.toLong()
                        "double" -> prefs[doublePreferencesKey(name)] = raw.toDouble()
                        "float" -> prefs[androidx.datastore.preferences.core.floatPreferencesKey(name)] = raw.toFloat()
                        "string" -> prefs[stringPreferencesKey(name)] = raw
                        "set" -> prefs[stringSetPreferencesKey(name)] = prefsJson.decodeFromString<List<String>>(raw).toSet()
                    }
                }
            }
        }
    }

    private fun isPrivateKey(name: String): Boolean =
        PRIVATE_KEY_PARTS.any { name.contains(it) } || name == "auto_backup_folder" || name.startsWith("sync_") ||
            (name.startsWith(TRACKER_PREFIX) && name != "tracker_auto_link_enabled")

    /** Private values that are encrypted at rest: tokens, passwords, app secrets, tracker values. */
    private fun isSecretKey(name: String): Boolean =
        SECRET_KEY_PARTS.any { name.contains(it) } || (name.startsWith(TRACKER_PREFIX) && name != "tracker_auto_link_enabled")
}
