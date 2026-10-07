package com.wanderwildwood.zatsuno.key

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneOffset
import java.time.ZonedDateTime

/** The SOAP note for a fixed scenario on a fixed clock, compared line by line. */
class NoteTest {

    private val day = ZonedDateTime.of(2026, 10, 7, 0, 0, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun at(h: Int, m: Int) = day + (h * 60L + m) * 60_000L

    private val incident = Incident(at(14, 2), listOf(
        Incident.Answer(at(14, 2), "safe", "yes"),
        Incident.Answer(at(14, 2), "what", "fall"),
        Incident.Where(at(14, 3), 44.428, -110.5885, 8f),
        Incident.Answer(at(14, 4), "avpu", "alert"),
        Incident.Answer(at(14, 4), "breathing", "normal"),
        Incident.Answer(at(14, 4), "bleeding", "none"),
        Incident.Answer(at(14, 5), "cold", "yes"),
        Incident.Answer(at(14, 6), "head_hit", Key.NOT_SURE),
        Incident.Answer(at(14, 10), "seen", "hurt_limb"),
        Incident.Answer(at(14, 10), "deformed", "yes"),
        Incident.Text(at(14, 11), "who", "Woman about 40"),
        Incident.Text(at(14, 11), "symptoms", "Right lower leg hurts, worse moving"),
        Incident.Text(at(14, 11), "allergies", "penicillin"),
        Incident.Check(at(14, 12), "alert", 104, 20, listOf("pale", "cool", "dry")),
        Incident.Done(at(14, 15), "Insulated from ground"),
        Incident.Answer(at(14, 16), "plan", "called"),
        Incident.Done(at(14, 20), "Splinted."),
        Incident.Check(at(14, 27), "alert", 96, 18, listOf("pink", "warm", "dry")),
    ))

    private val note = Note(KeyFixtures.key, KeyFixtures.words, KeyFixtures.titles, ZoneOffset.UTC)

    @Test
    fun theNoteReadsAsWritten() {
        val expected = listOf(
            "SOAP 14:02–14:27 · Field Kit",
            "Where: 44.42800, -110.58850 (±8 m, 14:03)",
            "",
            "S  Woman about 40 14:11",
            "   What happened: Fall 14:02",
            "   What they feel: Right lower leg hurts, worse moving 14:11",
            "   Allergies: penicillin 14:11",
            "O  Scene: Safe 14:02",
            "   Bleeding: None 14:04",
            "   Responds: Awake, talking 14:04",
            "   Breathing: Normal 14:04",
            "   Head or neck hit: Not sure 14:06",
            "   Cold, wet or on cold ground: Yes 14:05",
            "   Seen: Hurt arm or leg 14:10",
            "   Bent or badly swollen: Yes 14:10",
            "            14:12          14:27",
            "   AVPU     A              A",
            "   Pulse    104            96",
            "   Breaths  20             18",
            "   Skin     pale,cool,dry  pink,warm,dry",
            "A  Fits: Hypothermia. Fractures and splints. Sprains.",
            "   Still open: Stings and anaphylaxis (Pale, cool or clammy not checked). Chest pain (Chest pain or pressure not checked). " +
                "Stroke (Face not checked). Shock (Pale, cool or clammy not checked). Low blood sugar (Confused or odd not checked). " +
                "Head injury (Head or neck hit not checked). Heat exhaustion and heat stroke (Hot or worked hard in heat not checked).",
            "P  Insulated from ground 14:15.",
            "   Called for help 14:16.",
            "   Splinted 14:20.",
            "   Rechecking every 5 min.",
        )
        val got = note.text(incident).lines()
        for (i in 0 until maxOf(expected.size, got.size)) assertEquals("line ${i + 1}", expected.getOrNull(i), got.getOrNull(i))
    }

    @Test
    fun whatToReadOutFollowsWhatTheCallTakerAsks() {
        assertEquals(
            listOf(
                "Where: 44.42800, -110.58850 (±8 m, 14:03)",
                "Who: Woman about 40",
                "What happened: Fall 14:02",
                "How they are: Responds: Awake, talking. Breathing: Normal. Bleeding: None. Pulse 96. Breaths 18. Skin pink, warm, dry.",
                "Pages that fit: Hypothermia, Fractures and splints, Sprains",
                "Done: Insulated from ground 14:15. Called for help 14:16. Splinted 14:20.",
            ).joinToString("\n"),
            note.readOut(incident),
        )
    }

    @Test
    fun anEmptyNoteSaysSoInEachPart() {
        val text = note.text(Incident(at(9, 0))).lines()
        assertEquals("SOAP 09:00–09:00 · Field Kit", text[0])
        assertEquals("Where: not recorded", text[1])
        assertEquals("S  –", text[3])
        assertEquals("O  –", text[4])
        assertEquals("P  Rechecking every 15 min.", text.last())
    }

    @Test
    fun aRecheckThatChangesAnAnswerShowsBoth() {
        val changed = incident + Incident.Answer(at(14, 30), "breathing", "struggling")
        assertTrue(note.text(changed).lines().contains("   Breathing: Normal 14:04, Struggling 14:30"))
    }

    @Test
    fun theIncidentSurvivesBeingSavedAndRead() {
        val tricky = incident + Incident.Text(at(14, 40), "events", "slipped\ton wet rock\nthen \\ fell")
        assertEquals(tricky, Incident.decode(tricky.encode()))
    }

    @Test
    fun theRecheckComesEveryFifteenMinutesOrFiveWithADangerPage() {
        val key = KeyFixtures.key
        val calm = Incident(at(10, 0), listOf(Incident.Answer(at(10, 1), "seen", "blister")))
        assertEquals(15, calm.recheckMinutes(key))
        assertEquals(at(10, 15), calm.nextCheck(key))
        val danger = calm + Incident.Answer(at(10, 2), "bleeding", "heavy") + Incident.Check(at(10, 3), null, 80, null, emptyList())
        assertEquals(5, danger.recheckMinutes(key))
        assertEquals(at(10, 8), danger.nextCheck(key))
    }
}
