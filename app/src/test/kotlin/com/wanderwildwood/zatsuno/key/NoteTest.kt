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
        Incident.Answer(at(14, 16), "called", "called"),
        Incident.Done(at(14, 20), "Splinted."),
        Incident.Answer(at(14, 24), "plan", Key.NOT_SURE),
        Incident.Answer(at(14, 25), "plan", "carry"),
        Incident.Check(at(14, 27), "alert", 96, 18, listOf("pink", "warm", "dry"), "regular", "easy"),
    ))

    private val note = Note(KeyFixtures.key, KeyFixtures.words, KeyFixtures.titles, ZoneOffset.UTC)

    @Test
    fun theNoteReadsAsWritten() {
        val expected = listOf(
            "SOAP note 14:02–14:27 · Field Kit",
            "Where: 44.42800, -110.58850 (±8 m, 14:03)",
            "",
            "S  Patient: Woman about 40 14:11",
            "   MOI/NOI: Fall 14:02",
            "   Chief complaint and signs: Hurt arm or leg 14:10",
            "   S: Signs and symptoms: Right lower leg hurts, worse moving 14:11",
            "   A: Allergies: penicillin 14:11",
            "O  Scene safety: Safe 14:02",
            "   LOR (AVPU): A: Alert 14:04",
            "   Severe bleeding: None 14:04",
            "   Breathing: Normal 14:04",
            "   Head or spine mechanism: Not sure 14:06",
            "   Environment: cold exposure: Yes 14:05",
            "   Deformity or swelling: Yes 14:10",
            "            14:12          14:27",
            "   LOR      A              A",
            "   HR       104            96",
            "   rhythm   –              regular",
            "   RR       20             18",
            "   quality  –              easy",
            "   SCTM     pale,cool,dry  pink,warm,dry",
            "A  Assessment (problem list): Hypothermia. Fractures and splinting. Sprains and strains.",
            "   Not yet ruled out: Choking: Airway not checked. Stings and anaphylaxis: Skin signs of shock not checked. " +
                "Chest pain: Skin signs of shock not checked. Stroke: Altered mental status not checked. " +
                "Shock: Skin signs of shock not checked. Low blood sugar (hypoglycemia): Altered mental status not checked. " +
                "Head injury: Head or spine mechanism not checked. Heat exhaustion and heat stroke: Altered mental status not checked. " +
                "Altitude illness (AMS, HACE, HAPE): Recent altitude not checked.",
            "P  Insulated from ground 14:15.",
            "   Called (911 / SAR) 14:16.",
            "   Splinted 14:20.",
            "   Carry out (litter) 14:25.",
            "   Monitoring: vital signs every 5 min.",
        )
        val got = note.text(incident).lines()
        for (i in 0 until maxOf(expected.size, got.size)) assertEquals("line ${i + 1}", expected.getOrNull(i), got.getOrNull(i))
    }

    @Test
    fun whatToReadOutFollowsWhatTheCallTakerAsks() {
        assertEquals(
            listOf(
                "Where: 44.42800, -110.58850 (±8 m, 14:03)",
                "Patient: Woman about 40",
                "MOI/NOI: Fall 14:02",
                "Condition: LOR (AVPU): A: Alert. Breathing: Normal. Severe bleeding: None. HR 96. RR 18. SCTM pink, warm, dry.",
                "Problem list: Hypothermia, Fractures and splinting, Sprains and strains",
                "Treatment and plan: Insulated from ground 14:15. Called (911 / SAR) 14:16. Splinted 14:20. Carry out (litter) 14:25.",
            ).joinToString("\n"),
            note.readOut(incident),
        )
    }

    @Test
    fun anEmptyNoteSaysSoInEachPart() {
        val text = note.text(Incident(at(9, 0))).lines()
        assertEquals("SOAP note 09:00–09:00 · Field Kit", text[0])
        assertEquals("Where: not recorded", text[1])
        assertEquals("S  –", text[3])
        assertEquals("O  –", text[4])
        assertEquals("P  Monitoring: vital signs every 15 min.", text.last())
    }

    @Test
    fun aRecheckThatChangesAnAnswerShowsBoth() {
        val changed = incident + Incident.Answer(at(14, 30), "breathing", "struggling")
        assertTrue(note.text(changed).lines().contains("   Breathing: Normal 14:04, Labored: working hard, noisy, or can't speak a full sentence 14:30"))
    }

    @Test
    fun notDecidedYetWritesNothingToThePlan() {
        val undecided = Incident(at(9, 0), listOf(Incident.Answer(at(9, 1), "plan", Key.NOT_SURE), Incident.Answer(at(9, 2), "called", Key.NOT_SURE)))
        val text = note.text(undecided)
        assertTrue(text, text.lines().last() == "P  Monitoring: vital signs every 15 min.")
        assertTrue(text, "Not decided" !in text && "Not sure" !in text)
        assertEquals("Not decided yet", KeyFixtures.words.decided("plan", Key.NOT_SURE))
    }

    @Test
    fun aSoapLineForEachPartOfTheHistory() {
        val hx = Incident(at(9, 0), listOf(
            Incident.Answer(at(9, 1), "what", "by_itself"),
            Incident.Answer(at(9, 2), "allergy_known", "yes"),
            Incident.Text(at(9, 3), "who", "Man about 60"),
            Incident.Text(at(9, 3), "medicines", "metformin"),
            Incident.Text(at(9, 4), "last", "Lunch at noon, little water"),
            Incident.Text(at(9, 5), "onset", "Sudden, while climbing"),
            Incident.Text(at(9, 6), "resources", "Two of us, a sleeping bag"),
            Incident.Answer(at(9, 7), "body", "chest.hurt,pelvis.swelling"),
        )).note()
        assertEquals(listOf(
            "S  Patient: Man about 60 09:03",
            "   MOI/NOI: Illness: came on without an injury 09:01",
            "   Known allergy, exposed: Yes 09:02",
            "   M: Medications: metformin 09:03",
            "   L: Last intake and output (ate, drank, urine, stool): Lunch at noon, little water 09:04",
            "   O: Onset (how it started): Sudden, while climbing 09:05",
            "   Resources (who and what you have): Two of us, a sleeping bag 09:06",
            "O  Head-to-toe exam: Chest tender, Pelvis swollen 09:07",
        ), hx.lines().subList(3, 11))
    }

    private fun Incident.note() = note.text(this)

    @Test
    fun anOlderSavedSetOfVitalSignsStillReads() {
        val old = Incident.decode("S\t0\nC\t60000\talert\t88\t16\tpink,warm\n")!!
        assertEquals(Incident.Check(60000, "alert", 88, 16, listOf("pink", "warm")), old.checks().single())
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
