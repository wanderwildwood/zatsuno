package com.wanderwildwood.zatsuno.aid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

/** Ticks on a checklist page are kept on disk and come back to a fresh store, as after a restart. */
class TicksTest {

    private val dir = Files.createTempDirectory("ticks").toFile().also { it.deleteOnExit() }

    private val page = Markup.parse("kit", """
        title: First-aid kit checklist
        source: CDC
        checklist: yes
        ---
        # Dressings
        - Adhesive bandages, assorted
        - Gauze pads, 4 x 4 in
        1. A step is not a tick box.
        +++
        - Under More too
    """.trimIndent())

    @Test
    fun theFlagAndThePointsAreRead() {
        assertTrue(page.checklist)
        assertFalse(Markup.parse("x", "title: T\nsource: CDC\n---\n- a").checklist)
        assertEquals(listOf("Adhesive bandages, assorted", "Gauze pads, 4 x 4 in", "Under More too"), page.points)
    }

    @Test
    fun ticksSurviveANewStore() {
        val store = TickStore(dir)
        assertEquals(emptySet<String>(), store.load("kit"))
        assertEquals(setOf("Gauze pads, 4 x 4 in"), store.toggle("kit", "Gauze pads, 4 x 4 in"))
        store.toggle("kit", "Under More too")
        // A fresh store reads the file the way the app does after being killed and restarted.
        assertEquals(setOf("Gauze pads, 4 x 4 in", "Under More too"), TickStore(dir).load("kit"))
        // Untick one; the other stays.
        assertEquals(setOf("Under More too"), TickStore(dir).toggle("kit", "Gauze pads, 4 x 4 in"))
        assertEquals(setOf("Under More too"), TickStore(dir).load("kit"))
    }

    @Test
    fun pagesKeepTheirOwnTicks() {
        val store = TickStore(dir)
        store.toggle("kit", "a")
        store.toggle("other", "b")
        assertEquals(setOf("a"), store.load("kit"))
        assertEquals(setOf("b"), store.load("other"))
    }

    @Test
    fun clearTicksEmptiesThePageAndNothingElse() {
        val store = TickStore(dir)
        store.toggle("kit", "a")
        store.toggle("kit", "b")
        store.toggle("other", "c")
        store.clear("kit")
        assertEquals(emptySet<String>(), TickStore(dir).load("kit"))
        assertEquals(setOf("c"), TickStore(dir).load("other"))
        // Unticking the last one leaves no file behind either.
        store.toggle("other", "c")
        assertEquals(emptySet<String>(), TickStore(dir).load("other"))
        assertFalse(dir.resolve("other.txt").exists())
    }

    @Test
    fun theSentTextIsANotesChecklist() {
        val text = page.asText("Sources:", "Not training.", setOf("Gauze pads, 4 x 4 in"))
        assertTrue(text.contains("- [ ] Adhesive bandages, assorted\n- [x] Gauze pads, 4 x 4 in\n"))
        assertTrue(text.contains("1. A step is not a tick box."))
        // An ordinary page's points stay bullets.
        assertTrue(Markup.parse("x", "title: T\nsource: CDC\n---\n- a").asText("S", "D").contains("• a"))
    }
}
