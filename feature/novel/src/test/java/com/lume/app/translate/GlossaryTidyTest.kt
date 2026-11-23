package com.lume.app.translate

import com.lume.app.data.db.GLOSSARY_CONFIRMED
import com.lume.app.data.db.GLOSSARY_SUGGESTED
import com.lume.app.data.db.GlossaryTermEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class GlossaryTidyTest {
    private fun term(source: String, status: String) = GlossaryTermEntity("s", "t", source, source.uppercase(), 0, status)

    @Test
    fun suggestionsRepeatingANameAreDropped() {
        val terms = listOf(
            term("Yu San A", GLOSSARY_SUGGESTED),
            term("Yu San A-san", GLOSSARY_SUGGESTED), // the base form stays instead
            term("Lin", GLOSSARY_CONFIRMED),
            term("Lin's", GLOSSARY_SUGGESTED), // the user's own form wins
            term("Mo-sama", GLOSSARY_SUGGESTED),
            term("Mo's", GLOSSARY_SUGGESTED), // no base form: the shorter one stays
            term("Kirito", GLOSSARY_SUGGESTED),
        )
        assertEquals(listOf("Yu San A-san", "Lin's", "Mo-sama"), redundantSuggestions(terms).map { it.source })
    }

    @Test
    fun theUsersOwnDuplicatesAreOnlyOffered() {
        val terms = listOf(term("Yu San A-san", GLOSSARY_CONFIRMED), term("Yu San A", GLOSSARY_CONFIRMED), term("Lin", GLOSSARY_CONFIRMED))
        assertEquals(emptyList<GlossaryTermEntity>(), redundantSuggestions(terms))
        // The base form comes first: it is the one a merge keeps.
        assertEquals(listOf(listOf("Yu San A", "Yu San A-san")), confirmedDuplicates(terms).map { g -> g.map { it.source } })
    }
}
