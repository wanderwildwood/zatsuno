package com.wanderwildwood.zatsuno.aid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SeeLinksTest {

    private val titles = mapOf(
        "Hypothermia" to "hypothermia", "Heat exhaustion and heat stroke" to "heat", "Dehydration" to "dehydration",
        "Heat" to "nothing", "Shock" to "shock", "Burns" to "burns", "Frostbite" to "frostbite",
    )

    @Test
    fun aListOfTitlesIsALinkEach() {
        val text = "Fully awake and cold, overheated or dried out: see Hypothermia, Heat exhaustion and heat stroke, or Dehydration."
        val links = SeeLinks.find(text, titles)
        assertEquals(listOf("hypothermia", "heat", "dehydration"), links.map { it.page })
        assertEquals(listOf("Hypothermia", "Heat exhaustion and heat stroke", "Dehydration"), links.map { text.substring(it.start, it.end) })
    }

    @Test
    fun onlyAfterSee_andNeverToItself() {
        assertEquals(listOf("burns", "frostbite"), SeeLinks.find("(see Burns or Frostbite)", titles).map { it.page })
        assertTrue(SeeLinks.find("Shock can follow. See a doctor.", titles).isEmpty())
        assertTrue(SeeLinks.find("See Shock.", titles, from = "shock").isEmpty())
    }

    @Test
    fun noPageSaysSeeThatPage() {
        val raw = listOf("src/main/res/raw", "app/src/main/res/raw").map(::File).first { it.isDirectory }
        for (f in raw.listFiles { f -> f.name.startsWith("aid_") }!!) {
            assertTrue(f.name, "see that page" !in f.readText().lowercase())
        }
    }

    @Test
    fun callingForHelpShowsNoSignalWithoutMore() {
        val raw = listOf("src/main/res/raw", "app/src/main/res/raw").map(::File).first { it.isDirectory }
        val call = Markup.parse("call", File(raw, "aid_call.txt").readText())
        assertTrue(call.now.contains(Block.Heading("No signal")))
        assertTrue(call.rest.isEmpty())
    }
}
