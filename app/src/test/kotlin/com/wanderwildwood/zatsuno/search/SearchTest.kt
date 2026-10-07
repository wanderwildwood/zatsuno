package com.wanderwildwood.zatsuno.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val entries = listOf(
        Entry(Kind.AID, "burns", "Burns", listOf("scald"), "Cool the burn under running water. No butter."),
        Entry(Kind.AID, "bleeding", "Severe bleeding", listOf("tourniquet"), "Press hard on the wound."),
        Entry(Kind.KNOT, "bowline", "Bowline", emptyList(), "A fixed loop that won't slip."),
        Entry(Kind.COMPASS, "compass", "Compass", listOf("north", "position"), ""),
    )

    @Test
    fun aWordStartFindsTheTitle() {
        assertEquals("burns", Search.find(entries, "burn").first().entry.id)
    }

    @Test
    fun titleBeatsKeywordBeatsBody() {
        val e = listOf(
            Entry(Kind.AID, "body", "A", emptyList(), "loop"),
            Entry(Kind.AID, "key", "B", listOf("loop"), ""),
            Entry(Kind.AID, "title", "Loop", emptyList(), ""),
        )
        assertEquals(listOf("title", "key", "body"), Search.find(e, "loop").map { it.entry.id })
    }

    @Test
    fun everyWordMustMatch() {
        assertTrue(Search.find(entries, "burn tourniquet").isEmpty())
        assertEquals("bleeding", Search.find(entries, "press wound").single().entry.id)
    }

    @Test
    fun keywordsFindTheCompass() {
        assertEquals(Kind.COMPASS, Search.find(entries, "North").single().entry.kind)
    }

    @Test
    fun theSnippetIsTheSentenceWithTheWord() {
        assertEquals("No butter.", Search.find(entries, "butter").single().snippet)
    }

    @Test
    fun blankAndPunctuationFindNothing() {
        assertTrue(Search.find(entries, "  ").isEmpty())
        assertTrue(Search.find(entries, "?!").isEmpty())
    }

    @Test
    fun accentsAndCaseAreIgnored() {
        assertEquals(listOf("cafe"), Search.words("CAFÉ"))
    }

    @Test
    fun aWordMissingFromTheOpeningIsFoundUnderMore() {
        val head = "Severe bleeding\nPress hard on the wound."
        val rest = "Tourniquets now go on early."
        assertTrue(Search.onlyIn(rest, head, "press tourniquet"))
        assertTrue(!Search.onlyIn(rest, head, "Wound pres"))
        assertTrue(!Search.onlyIn(rest, head, "windlass"))
    }
}
