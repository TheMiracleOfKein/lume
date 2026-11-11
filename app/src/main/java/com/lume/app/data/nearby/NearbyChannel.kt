package com.lume.app.data.nearby

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * The wire between two phones: length-prefixed frames, plain during the handshake, AES-GCM after.
 *
 * Pairing (the receiver connects, the sender listens):
 * 1. receiver → sender: its name and a hash of its new public key (a commitment);
 * 2. sender → receiver: its name and its new public key;
 * 3. receiver → sender: its public key, which must match the commitment.
 *
 * Both sides then share an ECDH secret and show the same six-digit [Handshake.code], made from both
 * public keys. A device in the middle would have to swap keys, which changes the code on one of
 * the two screens; the commitment stops it from trying keys until the codes happen to match.
 * Keys are made for this connection only and never stored.
 */
class NearbyChannel private constructor(
    private val input: DataInputStream,
    private val output: DataOutputStream,
) {
    private var sendKey: SecretKeySpec? = null
    private var receiveKey: SecretKeySpec? = null
    private var sendCounter = 0L
    private var receiveCounter = 0L

    /** Sends one frame; encrypted once the handshake is done. */
    @Synchronized
    fun write(type: Int, body: ByteArray, offset: Int = 0, length: Int = body.size) {
        require(length <= MAX_FRAME) { "Frame too large" }
        val key = sendKey
        val payload = ByteArray(length + 1).also {
            it[0] = type.toByte()
            System.arraycopy(body, offset, it, 1, length)
        }
        val out = if (key == null) payload else cipher(Cipher.ENCRYPT_MODE, key, sendCounter++).doFinal(payload)
        output.writeInt(out.size)
        output.write(out)
        output.flush()
    }

    /** Reads one frame: its type and body. Fails on a broken, tampered or replayed frame. */
    fun read(): Frame {
        val size = input.readInt()
        // Before the handshake finishes only small hello frames are expected.
        val limit = if (receiveKey == null) MAX_HANDSHAKE_FRAME else MAX_FRAME + 1 + TAG_BYTES
        if (size !in 1..limit) throw IOException("Bad frame size $size")
        val raw = ByteArray(size)
        input.readFully(raw)
        val key = receiveKey
        val payload = if (key == null) {
            raw
        } else {
            try {
                cipher(Cipher.DECRYPT_MODE, key, receiveCounter++).doFinal(raw)
            } catch (e: java.security.GeneralSecurityException) {
                throw IOException("Frame failed its check", e)
            }
        }
        if (payload.isEmpty()) throw IOException("Empty frame")
        return Frame(payload[0].toInt() and 0xff, payload.copyOfRange(1, payload.size))
    }

    private fun cipher(mode: Int, key: SecretKeySpec, counter: Long): Cipher {
        val nonce = ByteArray(12)
        for (i in 0 until 8) nonce[4 + i] = (counter ushr (56 - 8 * i)).toByte()
        return Cipher.getInstance("AES/GCM/NoPadding").apply { init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce)) }
    }

    class Frame(val type: Int, val body: ByteArray)

    /** What both sides know after pairing. */
    class Handshake(val channel: NearbyChannel, val peerName: String, val code: String)

    companion object {
        const val MAX_FRAME = 4 * 1024 * 1024
        private const val MAX_HANDSHAKE_FRAME = 4096
        private const val TAG_BYTES = 16
        private const val VERSION = 1
        private const val TYPE_HELLO = 0

        /** The receiver's side of pairing. */
        fun connect(input: InputStream, output: OutputStream, myName: String): Handshake {
            val channel = NearbyChannel(DataInputStream(input.buffered()), DataOutputStream(output.buffered()))
            val mine = newKeyPair()
            val myKey = mine.public.encoded
            channel.write(TYPE_HELLO, encodeHello(myName, sha256(myKey)))
            val (peerName, peerKey) = decodeHello(channel.read())
            channel.write(TYPE_HELLO, encodeHello(myName, myKey))
            return finish(channel, mine, senderKey = peerKey, receiverKey = myKey, isSender = false, peerName = peerName)
        }

        /** The sender's side of pairing. */
        fun accept(input: InputStream, output: OutputStream, myName: String): Handshake {
            val channel = NearbyChannel(DataInputStream(input.buffered()), DataOutputStream(output.buffered()))
            val (peerName, commitment) = decodeHello(channel.read())
            val mine = newKeyPair()
            val myKey = mine.public.encoded
            channel.write(TYPE_HELLO, encodeHello(myName, myKey))
            val (_, peerKey) = decodeHello(channel.read())
            if (!MessageDigest.isEqual(sha256(peerKey), commitment)) throw IOException("Pairing failed")
            return finish(channel, mine, senderKey = myKey, receiverKey = peerKey, isSender = true, peerName = peerName)
        }

        private fun finish(
            channel: NearbyChannel,
            mine: KeyPair,
            senderKey: ByteArray,
            receiverKey: ByteArray,
            isSender: Boolean,
            peerName: String,
        ): Handshake {
            val peer = if (isSender) receiverKey else senderKey
            val secret = KeyAgreement.getInstance("ECDH").run {
                init(mine.private)
                doPhase(publicKeyOf(peer), true)
                generateSecret()
            }
            val transcript = sha256("lume-nearby-v$VERSION".toByteArray() + senderKey + receiverKey)
            val toReceiver = SecretKeySpec(hkdf(secret, transcript, "sender-to-receiver"), "AES")
            val toSender = SecretKeySpec(hkdf(secret, transcript, "receiver-to-sender"), "AES")
            channel.sendKey = if (isSender) toReceiver else toSender
            channel.receiveKey = if (isSender) toSender else toReceiver
            return Handshake(channel, peerName, pairingCode(transcript))
        }

        /** Six digits from the handshake, shown on both screens. */
        internal fun pairingCode(transcript: ByteArray): String {
            val h = sha256("code".toByteArray() + transcript)
            val n = ((h[0].toLong() and 0xff) shl 24) or ((h[1].toLong() and 0xff) shl 16) or
                ((h[2].toLong() and 0xff) shl 8) or (h[3].toLong() and 0xff)
            return "%06d".format(n % 1_000_000)
        }

        private fun newKeyPair(): KeyPair =
            KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()

        private fun publicKeyOf(encoded: ByteArray): PublicKey =
            try {
                KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
            } catch (e: java.security.GeneralSecurityException) {
                throw IOException("Bad key", e)
            }

        private fun sha256(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes)

        /** HKDF-SHA256 (RFC 5869), 32 bytes. */
        private fun hkdf(secret: ByteArray, salt: ByteArray, info: String): ByteArray {
            val prk = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(salt, "HmacSHA256")); doFinal(secret) }
            return Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(prk, "HmacSHA256"))
                update(info.toByteArray())
                update(1)
                doFinal()
            }
        }

        private fun encodeHello(name: String, key: ByteArray): ByteArray {
            val nameBytes = name.take(64).toByteArray(Charsets.UTF_8)
            val out = java.io.ByteArrayOutputStream()
            DataOutputStream(out).apply {
                writeInt(MAGIC)
                writeByte(VERSION)
                writeShort(nameBytes.size)
                write(nameBytes)
                writeShort(key.size)
                write(key)
            }
            return out.toByteArray()
        }

        private fun decodeHello(frame: Frame): Pair<String, ByteArray> {
            if (frame.type != TYPE_HELLO) throw IOException("Expected a hello")
            val data = DataInputStream(frame.body.inputStream())
            if (data.readInt() != MAGIC) throw IOException("Not a Lume device")
            if (data.readUnsignedByte() != VERSION) throw IOException("Other Lume version")
            val name = ByteArray(data.readUnsignedShort().coerceAtMost(256)).also(data::readFully).toString(Charsets.UTF_8)
            val key = ByteArray(data.readUnsignedShort().coerceAtMost(1024)).also(data::readFully)
            return name to key
        }

        private const val MAGIC = 0x4c554d45 // "LUME"
    }
}
