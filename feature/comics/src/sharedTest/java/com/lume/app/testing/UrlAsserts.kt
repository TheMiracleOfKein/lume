package com.lume.app.testing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Every URL is absolute http(s) with a host (spaces are allowed: some sites serve unescaped file names). */
fun assertHttpUrls(urls: List<String>) {
    assertTrue("no URLs", urls.isNotEmpty())
    for (url in urls) {
        assertTrue("not http(s): $url", url.startsWith("http://") || url.startsWith("https://"))
        assertFalse("no host: $url", url.substringAfter("://").substringBefore('/').isBlank())
    }
}

/** Page indexes run 0, 1, 2, … without gaps. */
fun assertSequentialIndexes(indexes: List<Int>) {
    assertEquals(indexes.indices.toList(), indexes)
}

/** The ids are not blank and not repeated. */
fun assertUniqueIds(ids: List<String>) {
    assertTrue(ids.all { it.isNotBlank() })
    assertEquals(ids.size, ids.toSet().size)
}
