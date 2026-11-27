package com.lume.app.ui.screens

import com.lume.app.domain.TitleSummary
import com.lume.app.ui.screens.SearchViewModel.SourceRow
import com.lume.app.ui.screens.SearchViewModel.SourceState
import org.junit.Assert.assertEquals
import org.junit.Test

class SourceRowRankingTest {
    private fun t(title: String) = TitleSummary(id = title, title = title)
    private fun row(id: String, state: SourceState) = SourceRow(id, id, state)
    private fun done(vararg titles: String) = SourceState.Done(titles.map(::t))

    @Test
    fun sourceWithTheNameComesBeforeOneWithout() {
        val rows = listOf(
            row("english", done("Tower of God", "Omniscient Reader")),
            row("failed", SourceState.Failed("timeout")),
            row("russian", done("Башня бога", "Поднятие уровня в одиночку")),
            row("empty", done()),
        )
        val ranked = SearchViewModel.rankSourceRows("поднятие уровня", rows).map { it.sourceId }
        assertEquals(listOf("russian", "english", "empty", "failed"), ranked)
    }

    @Test
    fun tiesKeepTheFirstOrder() {
        val rows = listOf(
            row("active", done("Герой")),
            row("other", done("Герой")),
        )
        assertEquals(listOf("active", "other"), SearchViewModel.rankSourceRows("герой", rows).map { it.sourceId })
    }
}
