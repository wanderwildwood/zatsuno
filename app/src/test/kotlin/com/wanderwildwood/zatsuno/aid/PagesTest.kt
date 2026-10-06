package com.wanderwildwood.zatsuno.aid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Every first-aid page parses, cites a source, and says what to do. */
class PagesTest {

    private val raw = listOf("src/main/res/raw", "app/src/main/res/raw").map(::File).first { it.isDirectory }
    private val pages = raw.listFiles { f -> f.name.startsWith("aid_") }!!.sortedBy { it.name }
        .map { Markup.parse(it.nameWithoutExtension.removePrefix("aid_"), it.readText()) }

    @Test
    fun allTwentyOnePagesAreThere() {
        assertEquals(21, pages.size)
    }

    @Test
    fun everyPageHasATitleASourceAndSteps() {
        for (p in pages) {
            assertTrue("${p.id} title", p.title.isNotBlank())
            assertTrue("${p.id} cites a US government source: ${p.source}",
                listOf("MedlinePlus", "CDC", "NIOSH", "NIH", "NPS", "NWS", "DHS", "911.gov", "FCC", "NIAMS", "NHLBI").any { it in p.source })
            assertTrue("${p.id} has steps or points", p.blocks.any { it is Block.Step || it is Block.Point })
        }
    }

    @Test
    fun onlyTheCallPageCarriesThePosition() {
        assertEquals(listOf("call"), pages.filter { Block.Position in it.blocks }.map { it.id })
    }

    @Test
    fun pagesStaySmallEnoughToReadAtAGlance() {
        for (p in pages) assertTrue("${p.id} has ${p.blocks.size} blocks", p.blocks.size <= 30)
    }

    @Test
    fun noBlockIsLeftAsStrayMarkup() {
        for (p in pages) for (b in p.blocks) {
            if (b is Block.Para) assertFalse("${p.id}: $b", b.text.startsWith("#") || b.text.startsWith("-") || b.text.startsWith("!"))
        }
    }

    @Test
    fun markupReadsEachKindOfLine() {
        val page = Markup.parse("x", """
            title: T
            keywords: a, b
            source: CDC
            ---
            ! Urgent
            # Head
            1. One
              continued
            - Point
            > Note
            @position
            Para
        """.trimIndent())
        assertEquals(listOf("a", "b"), page.keywords)
        assertEquals(
            listOf(Block.Urgent("Urgent"), Block.Heading("Head"), Block.Step(1, "One continued"), Block.Point("Point"),
                Block.Note("Note"), Block.Position, Block.Para("Para")),
            page.blocks,
        )
        val text = page.asText("Sources:", "Not training.")
        assertTrue(text.contains("1. One continued"))
        assertTrue(text.endsWith("Sources: CDC\nNot training."))
    }
}
