package com.lume.app.ui.screens

import com.lume.app.data.db.CodexAliasEntity
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexEntryWithAliases
import org.junit.Assert.assertEquals
import org.junit.Test

class CodexScreenTest {
    private fun entry(id: Long, name: String, note: String? = null, vararg aliases: String) = CodexEntryWithAliases(
        CodexEntryEntity(id = id, sourceId = "s", titleId = "t", name = name, note = note),
        aliases.map { CodexAliasEntity(id, it) },
    )

    private val all = listOf(
        entry(1, "Kirito", "Black swordsman", "Kirigaya"),
        entry(2, "Асуна", null, "Асуна Юки"),
    )

    @Test
    fun filtersByNameAliasAndNoteIgnoringCase() {
        assertEquals(listOf(1L), filterCodex(all, "KIRI").map { it.entry.id })
        assertEquals(listOf(1L), filterCodex(all, "swordsman").map { it.entry.id })
        assertEquals(listOf(2L), filterCodex(all, "юки").map { it.entry.id })
    }

    @Test
    fun blankQueryKeepsEverything() {
        assertEquals(2, filterCodex(all, "  ").size)
    }
}
