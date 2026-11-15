package com.lume.app.data.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncCodecTest {
    private val text = """{"format":1,"deviceId":"a","collections":{"library":[]}}"""

    @Test
    fun plainFilesRoundTrip() {
        val bytes = SyncCodec.encode(text, null)
        assertFalse(SyncCodec.isEncrypted(bytes))
        assertEquals(text, SyncCodec.decode(bytes, null))
        // A passphrase set here doesn't stop reading a plain file from another device.
        assertEquals(text, SyncCodec.decode(bytes, "secret"))
    }

    @Test
    fun encryptedFilesNeedTheSamePassphrase() {
        val bytes = SyncCodec.encode(text, "correct horse")
        assertTrue(SyncCodec.isEncrypted(bytes))
        assertFalse(bytes.decodeToString().contains("deviceId"))
        assertEquals(text, SyncCodec.decode(bytes, "correct horse"))

        val missing = assertThrows(SyncPassphraseException::class.java) { SyncCodec.decode(bytes, null) }
        assertTrue(missing.missing)
        val wrong = assertThrows(SyncPassphraseException::class.java) { SyncCodec.decode(bytes, "battery staple") }
        assertFalse(wrong.missing)
    }

    @Test
    fun everyFileGetsItsOwnSalt() {
        assertFalse(SyncCodec.encode(text, "p").contentEquals(SyncCodec.encode(text, "p")))
    }

    @Test
    fun plainJsonWithoutGzipStillReads() {
        assertEquals(text, SyncCodec.decode(text.toByteArray(), null))
    }

    @Test
    fun webDavListingsWithAnyPrefix() {
        val nextcloud = """<?xml version="1.0"?>
            <d:multistatus xmlns:d="DAV:">
              <d:response><d:href>/remote.php/dav/files/me/Lume/</d:href><d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/me/Lume/lume-sync-a.bin</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
              <d:response><d:href>/remote.php/dav/files/me/Lume/my%20notes.txt</d:href><d:propstat><d:prop><d:resourcetype/></d:prop></d:propstat></d:response>
            </d:multistatus>"""
        assertEquals(listOf("lume-sync-a.bin", "my notes.txt"), WebDavSyncBackend.fileNames(nextcloud))

        val apache = """<?xml version="1.0"?>
            <D:multistatus xmlns:D="DAV:">
              <D:response><D:href>/dav/</D:href><D:propstat><D:prop><D:resourcetype><D:collection/></D:resourcetype></D:prop></D:propstat></D:response>
              <D:response><D:href>/dav/lume-sync-b.bin</D:href><D:propstat><D:prop><D:resourcetype/></D:prop></D:propstat></D:response>
            </D:multistatus>"""
        assertEquals(listOf("lume-sync-b.bin"), WebDavSyncBackend.fileNames(apache))
    }
}
