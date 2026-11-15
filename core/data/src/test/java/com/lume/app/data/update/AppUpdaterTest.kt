package com.lume.app.data.update

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdaterTest {
    @Test
    fun comparesVersionsByNumber() {
        assertTrue(isNewer("0.7.5", "0.7.4"))
        assertTrue(isNewer("0.10.0", "0.9.9"))
        assertTrue(isNewer("v1.0", "0.9.12"))
        assertTrue(isNewer("0.7.4.1", "0.7.4"))
        assertFalse(isNewer("0.7.4", "0.7.4"))
        assertFalse(isNewer("0.7.3", "0.7.4"))
        assertFalse(isNewer("0.7", "0.7.0"))
    }

    @Test
    fun readsTagNotesAndApkAsset() {
        val release = parseRelease(
            json(
                """
                {"tag_name":"v0.7.5","body":"Notes","html_url":"https://github.com/x/releases/tag/v0.7.5",
                 "draft":false,"prerelease":false,
                 "assets":[{"name":"checksums.txt","browser_download_url":"https://e/c","size":10},
                           {"name":"lume-v0.7.5.apk","browser_download_url":"https://e/a.apk","size":12345}]}
                """,
            ),
        )!!
        assertEquals("0.7.5", release.version)
        assertEquals("Notes", release.notes)
        assertEquals("https://e/a.apk", release.apkUrl)
        assertEquals(12345L, release.apkSize)
    }

    @Test
    fun finalVersionIsNewerThanItsPreRelease() {
        assertTrue(isNewer("1.0.0", "1.0.0-rc1"))
        assertFalse(isNewer("1.0.0-rc1", "1.0.0"))
        assertFalse(isNewer("1.0.0-rc2", "1.0.0-rc1"))
    }

    @Test
    fun readsAssetDigest() {
        val sha = "a".repeat(64)
        val release = parseRelease(
            json(
                """{"tag_name":"v0.7.10","assets":[{"name":"lume-v0.7.10.apk",
                "browser_download_url":"https://github.com/TheMiracleOfKein/lume/releases/download/v0.7.10/lume-v0.7.10.apk",
                "size":5,"digest":"sha256:$sha"}]}""",
            ),
        )!!
        assertEquals(sha, release.apkSha256)
        assertNull(parseRelease(json("""{"tag_name":"v1","assets":[{"name":"a.apk","digest":"md5:x"}]}"""))!!.apkSha256)
    }

    @Test
    fun downloadsOnlyFromTheRepositoryReleases() {
        assertTrue(isTrustedApkUrl("https://github.com/TheMiracleOfKein/lume/releases/download/v0.7.10/lume-v0.7.10.apk"))
        assertFalse(isTrustedApkUrl("http://github.com/TheMiracleOfKein/lume/releases/download/v0.7.10/a.apk"))
        assertFalse(isTrustedApkUrl("https://github.com/someone/lume/releases/download/v0.7.10/a.apk"))
        assertFalse(isTrustedApkUrl("https://github.com.evil.example/TheMiracleOfKein/lume/releases/download/a.apk"))
        assertFalse(isTrustedApkUrl("not a url"))
    }

    @Test
    fun releaseWithoutApkHasNoDownload() {
        val release = parseRelease(json("""{"tag_name":"v0.8.0","assets":[]}"""))!!
        assertNull(release.apkUrl)
    }

    @Test
    fun skipsPreReleasesAndDrafts() {
        assertNull(parseRelease(json("""{"tag_name":"v0.8.0","prerelease":true}""")))
        assertNull(parseRelease(json("""{"tag_name":"v0.8.0","draft":true}""")))
    }

    private fun json(text: String) = Json.parseToJsonElement(text.trimIndent()).jsonObject
}
