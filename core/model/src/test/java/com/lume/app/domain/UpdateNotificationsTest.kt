package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UpdateNotificationsTest {
    private fun result(id: String) = LibraryUpdateResult("s", id, "Title $id", 1)

    private val cats = mapOf(
        "s/a" to setOf(1L),
        "s/b" to setOf(1L, 2L),
        "s/c" to emptySet<Long>(),
    )
    private val all = listOf(result("a"), result("b"), result("c"))

    private fun ids(muted: Set<Long>) =
        notifiable(all, { cats[it].orEmpty() }, muted).map { it.titleId }

    @Test
    fun emptyMutedSetLetsEverythingThrough() {
        assertEquals(listOf("a", "b", "c"), ids(emptySet()))
    }

    @Test
    fun mutedOnlyWhenEveryCategoryIsMuted() {
        assertEquals(listOf("b", "c"), ids(setOf(1L)))
        assertEquals(listOf("c"), ids(setOf(1L, 2L)))
    }

    @Test
    fun uncategorizedCountsAsZero() {
        assertEquals(listOf("a", "b"), ids(setOf(0L)))
        assertEquals(setOf(0L), effectiveCategories(emptySet()))
    }
}
