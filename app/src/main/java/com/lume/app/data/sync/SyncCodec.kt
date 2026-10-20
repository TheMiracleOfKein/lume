package com.lume.app.data.sync

import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** The passphrase is missing or doesn't open another device's file. */
class SyncPassphraseException(val missing: Boolean) : Exception(if (missing) "passphrase missing" else "wrong passphrase")

/**
 * Turns a sync file into bytes and back: gzipped JSON, and with a passphrase AES-256-GCM on top.
 * The key comes from the passphrase with PBKDF2 and a new random salt for every file, so the
 * passphrase itself is never stored in the shared place.
 *
 * Encrypted layout: `LUMESYNC1` · salt (16) · nonce (12) · ciphertext with tag.
 */
object SyncCodec {
    private val MAGIC = "LUMESYNC1".toByteArray()
    private const val SALT = 16
    private const val NONCE = 12
    private const val ITERATIONS = 120_000

    fun encode(text: String, passphrase: String?): ByteArray {
        val zipped = gzip(text.toByteArray())
        if (passphrase.isNullOrEmpty()) return zipped
        val random = SecureRandom()
        val salt = ByteArray(SALT).also(random::nextBytes)
        val nonce = ByteArray(NONCE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce))
        return MAGIC + salt + nonce + cipher.doFinal(zipped)
    }

    fun decode(bytes: ByteArray, passphrase: String?): String {
        val zipped = if (isEncrypted(bytes)) {
            if (passphrase.isNullOrEmpty()) throw SyncPassphraseException(missing = true)
            val salt = bytes.copyOfRange(MAGIC.size, MAGIC.size + SALT)
            val nonce = bytes.copyOfRange(MAGIC.size + SALT, MAGIC.size + SALT + NONCE)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, nonce))
            try {
                cipher.doFinal(bytes, MAGIC.size + SALT + NONCE, bytes.size - MAGIC.size - SALT - NONCE)
            } catch (_: AEADBadTagException) {
                throw SyncPassphraseException(missing = false)
            }
        } else {
            bytes
        }
        val plain = if (zipped.size > 1 && zipped[0] == 0x1f.toByte() && zipped[1] == 0x8b.toByte()) {
            GZIPInputStream(zipped.inputStream()).use { it.readBytes() }
        } else {
            zipped
        }
        return plain.decodeToString()
    }

    fun isEncrypted(bytes: ByteArray): Boolean =
        bytes.size > MAGIC.size && bytes.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)

    private fun key(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, 256)
        val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }
}
