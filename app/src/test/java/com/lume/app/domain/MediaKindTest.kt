package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaKindTest {
    @Test
    fun knownIdsResolve() {
        assertEquals(MediaKind.Comics, MediaKind.fromId("comics"))
        assertEquals(MediaKind.Novel, MediaKind.fromId("novel"))
        assertEquals(MediaKind.Video, MediaKind.fromId("video"))
    }

    @Test
    fun unknownOrMissingFallsBackToComics() {
        assertEquals(MediaKind.Comics, MediaKind.fromId(null))
        assertEquals(MediaKind.Comics, MediaKind.fromId(""))
        assertEquals(MediaKind.Comics, MediaKind.fromId("audiobook"))
    }

    @Test
    fun manifestKindFollowsMediaKind() {
        val manifest = SourceManifest(id = "x", name = "X", engine = "madara", baseUrl = "https://x.test", mediaKind = "novel")
        assertEquals(MediaKind.Novel, manifest.kind)
        assertEquals(MediaKind.Comics, manifest.copy(mediaKind = "weird").kind)
    }
}
