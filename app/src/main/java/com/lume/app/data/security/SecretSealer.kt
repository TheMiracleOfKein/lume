package com.lume.app.data.security

import com.lume.app.data.server.CredentialCipher
import com.lume.app.data.server.KeystoreCredentialCipher
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap

/**
 * Encrypts single secret strings (tracker tokens, site sign-ins, the sync password) before they
 * reach DataStore, with a key that never leaves the Android Keystore.
 *
 * A sealed value starts with [PREFIX]; anything else is a value written before v0.9.11 and is
 * read as it is, so [open] works before and after the one-time sealing on start.
 * When the Keystore fails to encrypt (rare, broken devices), [seal] keeps the plain value so the
 * sign-in still works. A value that cannot be decrypted (the key is gone, for example after a
 * device-to-device transfer) reads as null: the account is signed out, nothing crashes.
 */
class SecretSealer(private val cipher: CredentialCipher = KeystoreCredentialCipher(KEY_ALIAS)) {
    /** Decrypted values by their sealed text; decrypting through the Keystore costs milliseconds. */
    private val opened = ConcurrentHashMap<String, String>()

    fun seal(plain: String): String {
        if (plain.isEmpty() || isSealed(plain)) return plain
        val sealed = runCatching {
            PREFIX + Base64.getEncoder().encodeToString(cipher.encrypt(plain.toByteArray(Charsets.UTF_8)))
        }.getOrNull() ?: return plain
        opened[sealed] = plain
        return sealed
    }

    fun open(stored: String?): String? {
        if (stored == null || !isSealed(stored)) return stored
        opened[stored]?.let { return it }
        return runCatching {
            cipher.decrypt(Base64.getDecoder().decode(stored.removePrefix(PREFIX))).toString(Charsets.UTF_8)
        }.getOrNull()?.also { opened[stored] = it }
    }

    fun isSealed(stored: String): Boolean = stored.startsWith(PREFIX)

    companion object {
        const val PREFIX = "lume-sealed:1:"
        private const val KEY_ALIAS = "lume_secrets"
    }
}
