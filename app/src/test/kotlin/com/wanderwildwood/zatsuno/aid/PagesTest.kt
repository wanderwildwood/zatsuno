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

    private val titles = pages.map { it.title }.toSet()

    @Test
    fun allTwentyNinePagesAreThere() {
        assertEquals(29, pages.size)
        for (id in listOf("chest", "breathing", "sugar", "seizure", "stroke", "drowning", "altitude", "poisoning")) {
            assertTrue("$id is there", pages.any { it.id == id })
        }
    }

    @Test
    fun theListNamesEveryPageOnce() {
        val src = listOf("src/main/kotlin", "app/src/main/kotlin").map { File(it, "com/wanderwildwood/zatsuno/aid/Pages.kt") }.first { it.isFile }
        val listed = Regex("""R\.raw\.aid_(\w+)""").findAll(src.readText()).map { it.groupValues[1] }.toList()
        assertEquals(listed.toSet().size, listed.size)
        assertEquals(pages.map { it.id }.toSet(), listed.toSet())
    }

    @Test
    fun everySeeNamesARealPage() {
        // "See Severe bleeding", "see Burns or Frostbite": a capital after "see" is a page title.
        val see = Regex("""\b[Ss]ee ([A-Z][^.;:)\n]*)""")
        var checked = 0
        val byTitle = pages.associate { it.title to it.id }
        for (p in pages) for (m in see.findAll(p.searchText())) {
            checked++
            // A title with a gloss in brackets, "Low blood sugar (hypoglycemia)": the regex stops
            // at its ")", so it is checked the way the screen links it, whole.
            val whole = SeeLinks.find(p.searchText(), byTitle).firstOrNull { it.start == m.groups[1]!!.range.first }
            if (whole != null && '(' in p.searchText().substring(whole.start, whole.end)) continue
            val named = m.groupValues[1].trim()
            val parts = if (named in titles) listOf(named) else named.split(", or ", " or ", ", ").map { it.trim() }
            for (t in parts) assertTrue("${p.id}: \"See $t\" is no page's title", t in titles)
        }
        assertTrue("only $checked cross-references found", checked > 40)

    }

    @Test
    fun everyPageOpensOnWhatToDoNowWithEveryWarningAboveMore() {
        for (p in pages) {
            // Calling for help shows whole: other pages send readers to its No signal part.
            if (p.id == "call") assertTrue("call shows whole", p.rest.isEmpty())
            else assertTrue("${p.id} has a More part", p.rest.isNotEmpty())
            assertTrue("${p.id} opens on steps or warnings", p.now.any { it is Block.Step || it is Block.Urgent })
            assertTrue("${p.id}: a ! line under More", p.rest.none { it is Block.Urgent })
            assertTrue("${p.id}: a note left above More", p.now.none { it is Block.Note })
        }
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
        assertEquals(listOf("call"), pages.filter { Block.Sos in it.blocks }.map { it.id })
        assertEquals(listOf("lightning"), pages.filter { Block.Lightning in it.blocks }.map { it.id })
    }

    @Test
    fun theOpeningPartStaysShort() {
        for (p in pages) {
            val words = p.now.sumOf { b ->
                when (b) {
                    is Block.Heading -> b.text; is Block.Step -> b.text; is Block.Point -> b.text
                    is Block.Urgent -> b.text; is Block.Note -> b.text; is Block.Para -> b.text
                    Block.Position, Block.Sos, Block.Lightning -> ""
                }.split(Regex("\\s+")).count { it.isNotEmpty() }
            }
            assertTrue("${p.id} opens on $words words", words <= 400)
        }
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
            @sos
            @lightning
            Para
        """.trimIndent())
        assertEquals(listOf("a", "b"), page.keywords)
        assertEquals(
            listOf(Block.Urgent("Urgent"), Block.Heading("Head"), Block.Step(1, "One continued"), Block.Point("Point"),
                Block.Note("Note"), Block.Position, Block.Sos, Block.Lightning, Block.Para("Para")),
            page.blocks,
        )
        assertEquals(page.blocks.size, page.more)
        assertEquals(page.blocks, page.now)
        val text = page.asText("Sources:", "Not training.")
        assertTrue(text.contains("1. One continued"))
        assertTrue(text.endsWith("Sources: CDC\nNot training."))
    }

    @Test
    fun theMoreLineSplitsThePage() {
        val page = Markup.parse("x", """
            title: T
            source: CDC
            ---
            ! Urgent
            1. One
            +++
              not a continuation of the line above
            - Why
            > Note
        """.trimIndent())
        assertEquals(listOf(Block.Urgent("Urgent"), Block.Step(1, "One")), page.now)
        assertEquals(listOf(Block.Para("not a continuation of the line above"), Block.Point("Why"), Block.Note("Note")), page.rest)
        assertTrue(page.nowText().contains("One"))
        assertFalse(page.nowText().contains("Why"))
        assertTrue(page.restText().contains("Why"))
        assertTrue(page.searchText().contains("Why"))
        assertTrue(page.asText("Sources:", "").contains("Why"))
    }
}
