package com.lume.app.data.server

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.KeyStore
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private val Context.serverCredentialStore by preferencesDataStore("server_credentials")

/** How a server is signed into. Either a user name with a password, or an API key; or neither. */
@Serializable
data class ServerCredentials(
    val username: String = "",
    val password: String = "",
    val apiKey: String = "",
) {
    val hasPassword: Boolean get() = username.isNotBlank()
    val hasApiKey: Boolean get() = apiKey.isNotBlank()
    val isEmpty: Boolean get() = !hasPassword && !hasApiKey

    /** Never prints the secrets (it ends up in logs and crash reports otherwise). */
    override fun toString(): String = "ServerCredentials(user=${username.isNotBlank()}, apiKey=$hasApiKey)"
}

/** Encrypts the credentials at rest. A seam so JVM tests can run without the Android Keystore. */
interface CredentialCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(sealed: ByteArray): ByteArray
}

/** AES-256-GCM with a key that never leaves the Android Keystore. Output is `iv || ciphertext`. */
class KeystoreCredentialCipher(private val alias: String = KEY_ALIAS) : CredentialCipher {
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(PROVIDER).apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, PROVIDER)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return generator.generateKey()
    }

    override fun encrypt(plain: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key())
        return cipher.iv + cipher.doFinal(plain)
    }

    override fun decrypt(sealed: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, sealed, 0, IV_BYTES))
        return cipher.doFinal(sealed, IV_BYTES, sealed.size - IV_BYTES)
    }

    private companion object {
        const val PROVIDER = "AndroidKeyStore"
        const val KEY_ALIAS = "lume_server_credentials"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/**
 * Sign-ins of the user's servers, one entry per server id, encrypted with [cipher] before they
 * reach DataStore. [load] fills a memory copy that network code reads without suspending.
 * Secrets are never logged.
 */
class ServerCredentialStore(
    private val context: Context,
    private val cipher: CredentialCipher = KeystoreCredentialCipher(),
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, ServerCredentials>()

    /** Reads and decrypts everything stored. An entry that cannot be decrypted is skipped. */
    suspend fun load() {
        cache.clear()
        for ((key, value) in context.serverCredentialStore.data.first().asMap()) {
            val id = key.name.removePrefix(KEY_PREFIX).takeIf { key.name.startsWith(KEY_PREFIX) } ?: continue
            val sealed = value as? String ?: continue
            runCatching { decode(sealed) }.getOrNull()?.let { cache[id] = it }
        }
    }

    fun get(serverId: String): ServerCredentials? = cache[serverId]

    suspend fun put(serverId: String, credentials: ServerCredentials) {
        if (credentials.isEmpty) return remove(serverId)
        val sealed = encode(credentials)
        context.serverCredentialStore.edit { it[key(serverId)] = sealed }
        cache[serverId] = credentials
    }

    suspend fun remove(serverId: String) {
        context.serverCredentialStore.edit { it.remove(key(serverId)) }
        cache.remove(serverId)
    }

    internal fun encode(credentials: ServerCredentials): String {
        val plain = json.encodeToString(ServerCredentials.serializer(), credentials).toByteArray(Charsets.UTF_8)
        return Base64.encodeToString(cipher.encrypt(plain), Base64.NO_WRAP)
    }

    internal fun decode(sealed: String): ServerCredentials {
        val plain = cipher.decrypt(Base64.decode(sealed, Base64.NO_WRAP))
        return json.decodeFromString(ServerCredentials.serializer(), plain.toString(Charsets.UTF_8))
    }

    private fun key(serverId: String) = stringPreferencesKey(KEY_PREFIX + serverId)

    private companion object {
        const val KEY_PREFIX = "cred_"
    }
}
