package com.wanderwildwood.zatsuno.key

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset

/** Telling patients apart, the name in the note, and cutting a note into QR codes. Made-up names only. */
class PatientsTest {
    private val words = KeyFixtures.words
    private val a = Incident(1_000L) + Incident.Answer(1_000L, "safe", "yes")
    private val b = Incident(2_000L) + Incident.Text(2_100L, "name", "  Test Person B ")

    @Test fun aPatientWithoutANameIsNumberedByAge() {
        assertEquals("Patient 1", Patients.label(a, listOf(b, a), words))
        assertEquals("Test Person B", Patients.label(b, listOf(a, b), words))
        val c = Incident(3_000L)
        assertEquals("Patient 3", Patients.label(c, listOf(c, a, b), words))
    }

    @Test fun theNameGoesInTheNoteAndTheVerbalReportOnlyWhenGiven() {
        val note = Note(KeyFixtures.key, words, KeyFixtures.titles, ZoneOffset.UTC)
        assertTrue(note.text(b).lines().any { it == "Name: Test Person B" })
        assertTrue(note.readOut(b).lines().first() == "Name: Test Person B")
        assertTrue(note.text(a).lines().none { it.startsWith("Name:") })
    }

    @Test fun aShortNoteIsOneCodeAsItStands() {
        assertEquals(listOf("hello\nthere"), Patients.codes("hello\nthere"))
    }

    @Test fun aLongNoteIsNumberedPiecesCutBetweenLinesThatPutBackTogether() {
        val lines = (1..120).map { "Line $it of the made-up note, with some words in it" }
        val text = lines.joinToString("\n")
        val pieces = Patients.codes(text, 600)
        assertTrue(pieces.size > 1)
        pieces.forEachIndexed { i, p ->
            assertTrue(p.length <= 600)
            assertTrue(p.startsWith("(${i + 1}/${pieces.size})\n"))
        }
        val back = pieces.joinToString("\n") { it.substringAfter('\n') }
        assertEquals(text, back)
    }

    @Test fun aSingleVeryLongLineIsCutRatherThanLost() {
        val text = "x".repeat(2_000)
        val pieces = Patients.codes(text, 600)
        assertEquals(text, pieces.joinToString("") { it.substringAfter('\n') })
        assertTrue(pieces.all { it.length <= 600 })
    }

    @Test fun theIncidentRoundTripsWithItsNameAndId() {
        val back = Incident.decode(b.encode())!!
        assertEquals(b, back)
        assertEquals("2000", back.id)
    }
}
