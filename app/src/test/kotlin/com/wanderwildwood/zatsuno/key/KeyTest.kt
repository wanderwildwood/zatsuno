package com.wanderwildwood.zatsuno.key

import com.wanderwildwood.zatsuno.key.KeyFixtures.answers
import com.wanderwildwood.zatsuno.key.KeyFixtures.dangerIds
import com.wanderwildwood.zatsuno.key.KeyFixtures.key
import com.wanderwildwood.zatsuno.key.KeyFixtures.strings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** The key's data holds together: every id is real, worded, and used the way the rules allow. */
class KeyIntegrityTest {

    private fun valid(atom: Atom): Boolean {
        Key.DERIVED[atom.question]?.let { return atom.answer in it }
        val q = key.question(atom.question) ?: return false
        if ('*' in atom.answer) return q.answers.any { atom.holds(Answers(mapOf(q.id to setOf(it)))) }
        return atom.answer in q.answers
    }

    @Test
    fun everyPageAndAnswerIdExists() {
        // Not every page is reached from the assessment (signalling, water, the kit list): the
        // ones the key names it names once, in the list's order.
        val named = key.pages.map { it.id }
        assertEquals("the key names a page once", named.toSet().size, named.size)
        assertEquals("the key's pages in the list's order", KeyFixtures.pageIds.filter { it in named }, named)
        for (id in named) assertTrue("$id is no page", id in KeyFixtures.pageIds)
        for (p in key.pages) for (c in p.points + p.rules) for (a in c.atoms) assertTrue("${p.id}: $a", valid(a))
        for (q in key.questions) for (c in q.after) for (a in c.atoms) assertTrue("${q.id} after $a", valid(a))
    }

    @Test
    fun everyDangerPageHasSomethingPointingToIt_andSomethingThatCanRuleItOut() {
        for (p in key.pages) assertTrue("${p.id} has no points line", p.points.isNotEmpty())
        for (p in key.pages.filter { it.danger }) assertTrue("${p.id} has no rules line", p.rules.isNotEmpty())
        assertTrue(dangerIds.size >= 10)
    }

    @Test
    fun onlyDangerPagesRuleOut_andNeverOnNotSureOrAPickAll() {
        for (p in key.pages) {
            if (!p.danger) assertTrue("${p.id} is not a danger page but has rules", p.rules.isEmpty())
            for (c in p.rules) for (a in c.atoms) {
                assertFalse("${p.id}: rules on not sure", a.answer == Key.NOT_SURE)
                // A vital sign rules out only once it has been counted: pulse=normal is a sure answer, no count is none.
                val many = key.question(a.question)?.many ?: (a.question !in Key.DERIVED)
                assertFalse("${p.id}: rules on a pick-all ($a): not picking is not a no", many)
            }
        }
    }

    @Test
    fun noPageReadsADecision() {
        val decisions = key.questions.filter { it.decision }.map { it.id }.toSet()
        assertEquals(setOf("called", "plan"), decisions)
        for (p in key.pages) for (c in p.points + p.rules) for (a in c.atoms) assertFalse("${p.id} reads $a", a.question in decisions)
        for (q in key.questions) for (c in q.after) for (a in c.atoms) assertFalse("${q.id} follows $a", a.question in decisions)
    }

    @Test
    fun everyQuestionOffersNotSure_andNoneSpellsItOut() {
        // The screen adds "Not sure" (or "Not decided yet") to every single-answer question; the key must not list it as a real answer.
        for (q in key.questions) assertFalse(q.id, Key.NOT_SURE in q.answers)
        assertNotNull(strings["key_not_sure"])
        assertNotNull(strings["key_not_decided"])
    }

    @Test
    fun everyQuestionAndAnswerIsWorded() {
        val words = KeyFixtures.words
        for (q in key.questions) {
            words.question(q.id); words.short(q.id)
            if (q.grid) { q.places.forEach(words::place); q.findings.forEach(words::finding) }
            else q.answers.forEach { words.answer(q.id, it) }
        }
        for ((d, values) in Key.DERIVED) { words.question(d); words.short(d); values.forEach { words.answer(d, it) } }
        for (p in key.pages) for (c in p.points + p.rules) for (a in c.atoms) if (key.question(a.question)?.grid != true) words.answer(a.question, a.answer)
    }

    @Test
    fun chainOrderIsTheStageOrder() {
        val stages = key.questions.map { it.stage.ordinal }
        assertEquals(stages.sorted(), stages)
        // Responsiveness first, then X-ABCDE: severe bleeding, airway, breathing.
        val primary = key.questions.filter { it.stage == Stage.PRIMARY }.map { it.id }
        assertEquals(listOf("avpu", "bleeding", "airway", "breathing", "skin"), primary.take(5))
        assertEquals(listOf(null, "X", "A", "B", "C"), primary.take(5).map { key.question(it)!!.label })
        // Inside D the mechanism comes before the CSM check.
        assertTrue(primary.indexOf("head_hit") < primary.indexOf("move"))
    }

    @Test
    fun theSecondaryAssessmentIsInItsParts() {
        val parts = key.questions.filter { it.stage == Stage.SECONDARY }.map { it.part }
        assertEquals(listOf("chief", "exam", "sample", "focused"), parts.distinct())
        assertEquals("sample", key.question("you")!!.part)
    }

    @Test
    fun theHeadToToeExamOffersCsmOnlyWhereItIsChecked() {
        val body = key.question("body")!!
        assertEquals(listOf("deformed", "bleeding", "hurt", "swelling", "burned", "numb"), body.findings)
        assertEquals(listOf("deformed", "bleeding", "hurt", "swelling", "burned"), body.findingsAt("chest"))
        for (p in listOf("head", "arms", "legs")) assertTrue(p, "numb" in body.findingsAt(p))
        assertFalse("pelvis.numb" in body.answers)
    }
}

/**
 * The one that matters: an answer that wasn't given never hides a danger page. Uncertainty can
 * only ever add danger pages, never remove them.
 */
class NoFalseNegativeTest {

    @Test
    fun droppingAnyPartOfARulesClauseKeepsThePageVisible() {
        for (p in key.pages.filter { it.danger }) for (clause in p.rules) {
            val full = clause.atoms.fold(Answers()) { a, at -> a.with(at.question, at.answer) }
            assertFalse("${p.id}: the whole clause rules it out", p.id in rank(key, full).visible)
            for (drop in clause.atoms) {
                val without = full.without(drop.question)
                val notSure = full.with(drop.question, Key.NOT_SURE)
                // Other clauses may still rule it out on their own; only this clause is being tested.
                val othersHold = p.rules.any { it != clause && it.holds(without) }
                if (othersHold) continue
                assertTrue("${p.id}: hidden without $drop", p.id in rank(key, without).visible)
                assertTrue("${p.id}: hidden with $drop not sure", p.id in rank(key, notSure).visible)
            }
        }
    }

    /** A random set of answers, the vital signs included, some of them "not sure". */
    private fun randomAnswers(random: Random): Answers {
        var a = Answers()
        for (q in key.questions.filter { !it.many }) {
            when (random.nextInt(4)) {
                0 -> {}
                1 -> a = a.with(q.id, Key.NOT_SURE)
                else -> a = a.with(q.id, q.answers.random(random))
            }
        }
        for (q in key.questions.filter { it.many }) {
            val picked = q.answers.filter { random.nextInt(12) == 0 }.toSet()
            a = a.withMany(q.id, picked)
        }
        for ((d, values) in Key.DERIVED) if (random.nextBoolean()) a = a.with(d, values.random(random))
        return a
    }

    @Test
    fun turningAnyAnswerToNotSureNeverHidesADangerPage() {
        val random = Random(20261007)
        var tried = 0
        repeat(100_000) {
            val a = randomAnswers(random)
            val before = rank(key, a).visible.intersect(dangerIds)
            for (q in a.sure.keys.filter { key.question(it)?.many == false }) {
                tried++
                val after = rank(key, a.with(q, Key.NOT_SURE)).visible
                assertTrue("$q to not sure hid ${before - after} from $a", after.containsAll(before))
            }
        }
        assertTrue(tried > 500_000)
    }

    @Test
    fun takingBackAnyAnswerOrAVitalSignNeverHidesADangerPage() {
        // Missing data, not only "not sure": an answer never given, or vital signs never taken.
        // A page kept by the answer taken back may go, since that answer pointed to it (or might
        // have, beside a "not sure"); every other danger page in view must stay.
        val random = Random(1007)
        var tried = 0
        repeat(100_000) {
            val a = randomAnswers(random)
            val r = rank(key, a)
            for (q in a.sure.keys.filter { key.question(it)?.many == false || it in Key.DERIVED }) {
                tried++
                fun page(id: String) = key.pages.first { it.id == id }
                val keep = r.still.map { it.page }.filter { id ->
                    page(id).points.none { c -> c.atoms.any { it.question == q } && c.atoms.all { it.holds(a) || it.question in a.notSure } }
                } + r.fits.map { it.page }.filter { id ->
                    page(id).points.any { c -> c.holds(a) && c.atoms.none { it.question == q } }
                }
                val after = rank(key, a.without(q)).visible
                val lost = keep.intersect(dangerIds) - after
                assertTrue("$q taken back hid $lost from $a", lost.isEmpty())
            }
        }
        assertTrue(tried > 500_000)
    }

    @Test
    fun pointsWinOverRules() {
        // "Shivering" picked and "not cold" answered: Hypothermia stays under the problem list.
        assertTrue(rank(key, answers("seen=cold_shiver", "cold=no")).fits.any { it.page == "hypothermia" })
        assertTrue(rank(key, answers("seen=face_speech", "face=even", "arm=level", "speech=clear")).fits.any { it.page == "stroke" })
        assertTrue(rank(key, answers("seen=hard_breathe", "breathing=normal")).fits.any { it.page == "breathing" })
    }

    @Test
    fun aNotSureThatMightPointHereKeepsTheDangerPage() {
        // Found by the random run: "breathing normal, skin normal" rules Anaphylaxis out, but
        // with what stung them "not sure" and "short of breath" picked, it may be a bee.
        val sure = answers("breathing=normal", "skin=no", "avpu=alert", "confused=no", "seen=hard_breathe", "bite_kind=insect")
        assertTrue(rank(key, sure).fits.any { it.page == "anaphylaxis" })
        val unsure = rank(key, sure.with("bite_kind", Key.NOT_SURE))
        assertEquals("bite_kind", unsure.still.first { it.page == "anaphylaxis" }.ask)
    }

    @Test
    fun nothingAnsweredLeavesEveryDangerPageToCheck() {
        val r = rank(key, Answers())
        assertEquals(dangerIds, r.still.map { it.page }.toSet())
        assertTrue(r.fits.isEmpty())
        assertTrue(r.ruledOut.isEmpty())
    }

    private val calm = answers("bleeding=none", "avpu=alert", "airway=clear", "breathing=normal", "skin=no", "move=yes", "head_hit=no",
        "confused=no", "cold=no", "hot=no", "chest=no", "face=even", "arm=level", "speech=clear", "befast=no", "altitude=no", "pulse=normal")

    @Test
    fun clearingEveryAnswerBringsEveryDangerPageBack() {
        assertEquals(dangerIds, rank(key, calm).ruledOut.map { it.page }.toSet())
        val cleared = calm.sure.keys.fold(calm) { a, q -> a.without(q) }
        assertEquals(dangerIds, rank(key, cleared).still.map { it.page }.toSet())
    }

    @Test
    fun aRecheckThatChangesARulingAnswerBringsThePageBack() {
        val inc = Incident(0L, listOf(
            Incident.Answer(60_000, "breathing", "normal"),
            Incident.Answer(120_000, "breathing", "struggling"),
        ))
        val r = rank(key, inc.answers())
        assertTrue(r.fits.any { it.page == "breathing" })
        val calm = rank(key, Incident(0L, listOf(Incident.Answer(60_000, "breathing", "normal"))).answers())
        assertEquals(60_000L, calm.ruledOut.first { it.page == "breathing" }.at)
    }

    @Test
    fun aStillToCheckPageNamesTheQuestionThatSettlesIt() {
        val r = rank(key, answers("avpu=alert", "confused=no", "move=yes"))
        assertEquals("head_hit", r.still.first { it.page == "head" }.ask)
        // Chest is ruled out on no discomfort, normal breathing and normal skin: the earliest asked comes first.
        assertEquals("breathing", r.still.first { it.page == "chest" }.ask)
        assertEquals("chest", rank(key, answers("breathing=normal", "skin=no")).still.first { it.page == "chest" }.ask)
        assertEquals("face", r.still.first { it.page == "stroke" }.ask)
        assertEquals("arm", rank(key, answers("avpu=alert", "confused=no", "face=even")).still.first { it.page == "stroke" }.ask)
        assertEquals("altitude", r.still.first { it.page == "altitude" }.ask)
        // Shock waits for a heart rate once the rest is clear.
        assertEquals("pulse", rank(key, answers("skin=no", "avpu=alert", "confused=no")).still.first { it.page == "shock" }.ask)
    }

    // The holes the WFA review found, each closed.

    @Test
    fun heatIsNotRuledOutWhileAltered() {
        // Exertional heat stroke: "not hot" from a bystander, but drowsy or confused.
        assertTrue("heat" in rank(key, answers("hot=no", "avpu=voice")).visible)
        assertTrue("heat" in rank(key, answers("hot=no", "avpu=alert", "confused=yes")).visible)
        assertTrue("heat" in rank(key, answers("hot=no", "avpu=alert", "confused=not_sure")).visible)
        assertTrue("heat" in rank(key, answers("hot=no", "avpu=alert")).visible)
        assertTrue(rank(key, answers("hot=no", "avpu=alert", "confused=no")).ruledOut.any { it.page == "heat" })
    }

    @Test
    fun hypothermiaIsNotRuledOutWhileAltered() {
        // Already moved into a hut and stopped shivering, but drowsy.
        assertTrue("hypothermia" in rank(key, answers("cold=no", "avpu=voice", "confused=yes")).visible)
        assertTrue(rank(key, answers("cold=no", "avpu=alert", "confused=no")).ruledOut.any { it.page == "hypothermia" })
    }

    @Test
    fun anaphylaxisWithResponsivenessBelowAlert() {
        // Stung, breathing fine, skin fine, but only answers to voice: anaphylactic shock.
        for (lor in listOf("voice", "pain", "none")) {
            val r = rank(key, answers("what=bite_sting", "seen=bite_sting", "bite_kind=insect", "avpu=$lor", "breathing=normal", "skin=no", "confused=no"))
            assertTrue("avpu=$lor", r.fits.any { it.page == "anaphylaxis" })
        }
        // With nothing pointing, a drop in responsiveness still keeps it from being ruled out.
        assertTrue("anaphylaxis" in rank(key, answers("breathing=normal", "skin=no", "avpu=voice", "confused=no")).visible)
    }

    @Test
    fun gaspingWhileResponsiveBringsUpRespiratoryDistress() {
        for (lor in listOf("alert", "voice")) {
            val r = rank(key, answers("avpu=$lor", "breathing=gasping"))
            assertTrue("avpu=$lor", r.fits.any { it.page == "breathing" })
        }
        // Unresponsive and gasping is cardiac arrest, not breathing trouble.
        val arrest = rank(key, answers("avpu=none", "breathing=gasping"))
        assertTrue(arrest.fits.any { it.page == "cpr" })
        assertFalse(arrest.fits.any { it.page == "breathing" })
    }

    @Test
    fun laboredBreathingRulesOutCprOnlyWhileTheyRespond() {
        assertTrue(rank(key, answers("avpu=alert", "breathing=struggling")).ruledOut.any { it.page == "cpr" })
        // Agonal breathing is misread as labored: unresponsive, it must stay.
        for (lor in listOf("pain", "none", "not_sure")) assertTrue("avpu=$lor", "cpr" in rank(key, answers("avpu=$lor", "breathing=struggling")).visible)
        assertTrue("cpr" in rank(key, answers("breathing=struggling")).visible)
        // A choking patient who collapses: CPR.
        assertTrue(rank(key, answers("avpu=none", "airway=blocked", "breathing=struggling")).fits.any { it.page == "cpr" })
    }

    @Test
    fun anAxialLoadFallKeepsTheSpinePage() {
        // Landed on their feet from a height: no blow to the head, but a spine mechanism.
        val q = strings.getValue("key_q_head_hit")
        assertTrue(q, "hard landing" in q && "back" in q)
        val landed = rank(key, answers("what=fall", "head_hit=yes", "avpu=alert", "confused=no", "move=yes"))
        assertTrue(landed.fits.any { it.page == "head" })
        // Back pain found on the exam keeps it too, and raises a red flag.
        val back = rank(key, answers("what=fall", "head_hit=no", "avpu=alert", "confused=no", "move=yes", "body=back.hurt"))
        assertTrue(back.fits.any { it.page == "head" })
        assertTrue(back.fits.any { it.page == "call" })
        // Numb legs too.
        assertTrue(rank(key, answers("head_hit=no", "avpu=alert", "confused=no", "move=yes", "body=legs.numb")).fits.any { it.page == "head" })
    }

    @Test
    fun swollenLipsOrTongueAloneBringUpAnaphylaxis() {
        val alone = rank(key, answers("seen=lips_tongue"))
        assertTrue(alone.fits.any { it.page == "anaphylaxis" })
        assertTrue(alone.fits.any { it.page == "call" })
        // Even with every rule-out answer given.
        val calm = rank(key, answers("seen=lips_tongue", "breathing=normal", "skin=no", "avpu=alert", "confused=no"))
        assertTrue(calm.fits.any { it.page == "anaphylaxis" })
    }

    @Test
    fun aStrongCoughKeepsChokingInView() {
        // Breathing "normal" while coughing hard is a partial obstruction: not ruled out.
        assertTrue("choking" in rank(key, answers("breathing=normal")).visible)
        assertTrue("choking" in rank(key, answers("airway=noisy", "breathing=normal")).visible)
        assertTrue(rank(key, answers("airway=blocked")).fits.any { it.page == "choking" })
        assertTrue(rank(key, answers("airway=clear", "breathing=normal")).ruledOut.any { it.page == "choking" })
    }

    @Test
    fun compensatedShockWaitsForAHeartRate() {
        val early = answers("skin=no", "avpu=alert", "confused=no")
        assertTrue("shock" in rank(key, early).visible)
        assertTrue("shock" in rank(key, early.with("pulse", "fast")).visible)
        assertTrue(rank(key, early.with("pulse", "normal")).ruledOut.any { it.page == "shock" })
        // Late shock: falling responsiveness with a fast pulse.
        assertTrue(rank(key, answers("avpu=voice", "pulse=fast")).fits.any { it.page == "shock" })
    }

    @Test
    fun theVitalSignsBringPagesBack() {
        val inc = Incident(0L, listOf(
            Incident.Answer(1, "breathing", "normal"),
            Incident.Answer(1, "bleeding", "some"),
            Incident.Check(2, "alert", 120, 36, listOf("pale", "cool", "clammy")),
        ))
        val r = rank(key, inc.answers())
        assertTrue("RR over 30", r.fits.any { it.page == "breathing" })
        assertTrue("minor bleeding with shock signs", r.fits.any { it.page == "bleeding" })
        val blue = Incident(0L, listOf(Incident.Answer(1, "breathing", "normal"), Incident.Check(2, null, null, null, listOf("blue"))))
        assertTrue("blue lips", rank(key, blue.answers()).fits.any { it.page == "breathing" })
        assertEquals(setOf("blue"), blue.answers().sure["vskin"])
        val slow = Incident(0L, listOf(Incident.Check(2, null, 88, 16, listOf("pink"))))
        assertEquals(setOf("normal"), slow.answers().sure["pulse"])
        assertEquals(null, slow.answers().sure["resp"])
    }

    @Test
    fun lowBloodSugarIsNotRuledOutByANoToDiabetes() {
        // A bystander's "not diabetic" about a confused stranger: give sugar anyway, if they can swallow.
        assertTrue("sugar" in rank(key, answers("diabetic=no", "confused=yes")).visible)
        assertTrue(rank(key, answers("diabetic=yes", "seen=fit")).fits.any { it.page == "sugar" })
    }

    @Test
    fun strokeStaysWhileOtherSignsAreUnasked() {
        val fast = answers("face=even", "arm=level", "speech=clear")
        assertTrue("stroke" in rank(key, fast).visible)
        assertTrue(rank(key, answers("befast=yes")).fits.any { it.page == "stroke" })
        assertTrue(rank(key, fast.with("befast", "no").with("confused", "no").with("avpu", "alert")).ruledOut.any { it.page == "stroke" })
    }

    @Test
    fun altitudeIllnessIsADangerPageSettledByRecentAscent() {
        assertTrue("altitude" in dangerIds)
        assertTrue(rank(key, answers("altitude=yes", "walk=stumbles")).fits.any { it.page == "altitude" })
        assertTrue(rank(key, answers("altitude=yes", "confused=yes")).fits.any { it.page == "altitude" })
        assertTrue(rank(key, answers("altitude=no")).ruledOut.any { it.page == "altitude" })
    }

    @Test
    fun aSeriousBurnRaisesTheRedFlagsAndShock() {
        val r = rank(key, answers("what=burn", "burn_serious=yes"))
        assertTrue(r.fits.any { it.page == "burns" })
        assertTrue(r.fits.any { it.page == "call" })
        assertTrue(r.fits.any { it.page == "shock" })
        assertTrue(rank(key, answers("burn_kind=smoke")).fits.any { it.page == "call" })
        assertTrue(rank(key, answers("body=chest.burned")).fits.any { it.page == "burns" })
    }
}

/** Every page under the problem list is there because of an answer, and one tap brings in only what it maps to. */
class NoFalsePositiveTest {

    @Test
    fun everyFitNamesAnAnswerThatPointsToIt() {
        val random = Random(7)
        repeat(5_000) {
            var a = Answers()
            for (q in key.questions) if (random.nextInt(3) == 0) {
                a = if (q.many) a.withMany(q.id, setOf(q.answers.random(random))) else a.with(q.id, q.answers.random(random))
            }
            for ((d, values) in Key.DERIVED) if (random.nextInt(3) == 0) a = a.with(d, values.random(random))
            for (f in rank(key, a).fits) {
                assertTrue(f.why.isNotEmpty())
                assertTrue("${f.page}: ${f.why}", f.why.all { it.holds(a) })
            }
        }
    }

    @Test
    fun oneTapOnWhatYouSeeBringsInOnlyThePagesMappedFromIt() {
        val seen = key.question("seen")!!
        for (s in seen.answers) {
            val a = Answers().withMany("seen", setOf(s))
            val mapped = key.pages.filter { p -> p.points.any { c -> c.atoms == listOf(Atom("seen", s)) } }.map { it.id }.toSet()
            assertEquals("seen=$s", mapped, rank(key, a).fits.map { it.page }.toSet())
        }
        assertEquals(setOf("fracture", "sprain"), rank(key, Answers().withMany("seen", setOf("hurt_limb"))).fits.map { it.page }.toSet())
        assertTrue(rank(key, Answers().withMany("seen", setOf("wound"))).fits.none { it.page == "snakebite" })
        // A blister is a blister until it is cold.
        assertEquals(setOf("blisters"), rank(key, answers("seen=blister")).fits.map { it.page }.toSet())
    }

    @Test
    fun aNumbLimbFromAFallIsNotFrostbite() {
        assertTrue(rank(key, answers("what=fall", "body=legs.numb")).fits.none { it.page == "frostbite" })
        assertTrue(rank(key, answers("what=cold", "body=legs.numb")).fits.any { it.page == "frostbite" })
    }

    @Test
    fun unequalPupilsPointToHeadInjuryAndNothingRulesOnThem() {
        // Every answer that rules Head injury out, so only the pupils can bring it back.
        val calm = listOf("head_hit" to "no", "avpu" to "alert", "confused" to "no", "move" to "yes").map { (q, a) -> Incident.Answer(1, q, a) }
        fun with(pupils: String?) = rank(key, Incident(0L, calm + Incident.Check(2, "alert", 80, 16, listOf("pink"), pupils = pupils)).answers())
        assertFalse("head ruled out with equal pupils", "head" in with(Incident.EQUAL).visible)
        assertFalse("head ruled out with pupils not checked", "head" in with(null).visible)
        assertEquals("not checked is no answer", null, Incident(0L, calm + Incident.Check(2, null, null, null, emptyList())).answers().sure["pupils"])
        val unequal = with(Incident.UNEQUAL)
        assertTrue("unequal pupils bring Head injury up", unequal.fits.any { it.page == "head" && it.why == listOf(Atom("pupils", "unequal")) })
        // Points only: the pupils never rule a page out, so "not checked" and blank change nothing.
        for (p in key.pages) for (c in p.rules) for (a in c.atoms) assertFalse("${p.id} rules on $a", a.question == "pupils")
        assertEquals(with(null).ruledOut.map { it.page }.filter { it != "head" }, unequal.ruledOut.map { it.page })
    }

    @Test
    fun aHeadInjuryAloneIsNotShock() {
        assertTrue(rank(key, answers("avpu=voice", "head_hit=yes")).fits.none { it.page == "shock" })
    }
}

/** Every first-aid page can be reached through the key, by answers the screen actually asks. */
class ReachableTest {

    /** What the screen shows: a question with nothing to follow, a follow-up once it follows, a settle question while it settles. */
    private fun shown(q: Question, a: Answers): Boolean {
        if (q.grid || q.after.isEmpty() && !q.settle) return true
        if (q.after.any { it.holds(a) }) return true
        return q.settle && rank(key, a).still.any { it.ask == q.id }
    }

    private fun give(a: Answers, question: String, value: String): Answers =
        if (key.question(question)?.many == true) a.withMany(question, a.sure[question].orEmpty() + value) else a.with(question, value)

    @Test
    fun everyClauseOfEveryPageCanBeBroughtUnderTheProblemList() {
        assertTrue(KeyFixtures.pageIds.containsAll(key.pages.map { it.id }))
        for (p in key.pages) for (clause in p.points) {
            var a = Answers()
            for (atom in clause.atoms) {
                val q = key.question(atom.question)
                if (q != null) {
                    // A follow-up is only on screen once what it follows holds: answer that too.
                    if (!shown(q, a)) q.after.firstOrNull()?.atoms?.forEach { a = give(a, it.question, it.answer) }
                    assertTrue("${p.id} from $clause: ${q.id} is never on screen", shown(q, a))
                }
                val value = if ('*' in atom.answer) q!!.answers.first { atom.holds(Answers(mapOf(q.id to setOf(it)))) } else atom.answer
                a = give(a, atom.question, value)
            }
            assertTrue("${p.id} from $clause", rank(key, a).fits.any { it.page == p.id })
        }
    }

    @Test
    fun everyQuestionCanComeOnScreen() {
        for (q in key.questions) {
            var a = Answers()
            if (!shown(q, a)) q.after.firstOrNull()?.atoms?.forEach { a = give(a, it.question, it.answer) }
            assertTrue(q.id, shown(q, a))
        }
    }
}

/** WFA course cases: each set of answers lands on the pages a first-aider would open. */
class WalkthroughTest {

    private fun check(name: String, given: Array<String>, fits: Set<String>, still: Set<String> = emptySet()) {
        val r = rank(key, answers(*given))
        val got = r.fits.map { it.page }.toSet() - setOf("call", "scene")
        assertEquals("$name: problem list", fits, got)
        assertTrue("$name: not yet ruled out ${r.still.map { it.page }} missing $still", r.still.map { it.page }.containsAll(still))
    }

    private val calmThreats = arrayOf("bleeding=none", "avpu=alert", "airway=clear", "breathing=normal", "skin=no", "move=yes", "head_hit=no", "confused=no", "cold=no", "hot=no")

    @Test fun fallWithHeavyBleeding() = check("fall, heavy bleeding",
        arrayOf("safe=yes", "what=fall", "avpu=alert", "bleeding=heavy", "airway=clear", "breathing=normal", "head_hit=not_sure", "seen=hurt_limb,wound"),
        setOf("bleeding", "shock", "fracture", "sprain", "wounds"), setOf("head"))

    @Test fun coldConfusedAndWet() = check("cold, confused, wet",
        arrayOf("what=cold", "avpu=voice", "bleeding=none", "airway=clear", "breathing=normal", "confused=yes", "cold=yes", "seen=cold_shiver"),
        setOf("hypothermia", "head"), setOf("sugar", "stroke", "heat"))

    @Test fun beeStingWithBreathingTrouble() = check("bee sting, breathing",
        arrayOf("what=bite_sting", "avpu=alert", "bleeding=none", "airway=clear", "breathing=struggling", "seen=bite_sting,rash_swelling", "bite_kind=insect"),
        setOf("anaphylaxis", "breathing", "allergy"))

    @Test fun beeStingSwollenTongue() = check("bee sting, swollen tongue",
        arrayOf("what=bite_sting", *calmThreats, "seen=bite_sting,lips_tongue", "bite_kind=insect"),
        setOf("anaphylaxis", "allergy"))

    @Test fun chestPainOnAClimb() = check("chest pain on a climb",
        arrayOf("what=by_itself", *calmThreats, "seen=chest_pain,high_up", "skin=yes"),
        setOf("chest", "altitude", "shock"), setOf("stroke"))

    @Test fun childPulledFromWater() = check("child from water",
        arrayOf("what=water", "avpu=pain", "bleeding=none", "breathing=gasping", "cold=yes"),
        setOf("drowning", "cpr", "head", "hypothermia"))

    @Test fun diabeticActingDrunk() = check("diabetic acting drunk",
        arrayOf(*calmThreats, "confused=yes", "diabetic=yes"),
        setOf("sugar", "head"), setOf("stroke"))

    @Test fun foundAfterASeizure() = check("found after a seizure",
        arrayOf("what=by_itself", "avpu=voice", "bleeding=none", "airway=clear", "breathing=normal", "confused=yes", "seizure_hx=yes", "diabetic=no"),
        setOf("seizure", "head"), setOf("sugar", "stroke"))

    @Test fun snakebiteOnTheAnkle() = check("snakebite",
        arrayOf("what=bite_sting", *calmThreats, "seen=bite_sting", "bite_kind=snake"),
        setOf("snakebite"))

    @Test fun dogBite() = check("dog bite",
        arrayOf("what=bite_sting", *calmThreats, "seen=bite_sting", "bite_kind=animal"),
        setOf("wounds", "bites"))

    @Test fun carbonMonoxideInATent() = check("CO in a tent",
        arrayOf("what=swallowed", "avpu=voice", "bleeding=none", "breathing=normal", "confused=yes"),
        setOf("poisoning", "head"))

    @Test fun twoSickInACabin() = check("two sick in a cabin, no injury",
        arrayOf("what=by_itself", "count=more", *calmThreats),
        setOf("poisoning"))

    @Test fun lightningStrike() = check("lightning",
        arrayOf("what=lightning", "avpu=none", "bleeding=none", "breathing=gasping", "seen=burn"),
        setOf("lightning", "cpr", "head", "burns"))

    @Test fun foundCollapsedInAStorm() = check("collapsed in a storm",
        arrayOf("what=dont_know", "storm=yes", "avpu=none", "breathing=gasping"),
        setOf("lightning", "cpr", "head"))

    @Test fun deformedLegOnColdGround() = check("deformed leg, cold ground",
        arrayOf("what=fall", "avpu=alert", "bleeding=none", "breathing=normal", "cold=yes", "seen=hurt_limb", "deformed=yes", "body=legs.deformed"),
        setOf("fracture", "sprain", "hypothermia"), setOf("head", "shock"))

    @Test fun pelvisTenderAfterAFall() = check("pelvis tender after a fall",
        arrayOf("what=fall", *calmThreats, "body=pelvis.hurt"),
        setOf("fracture"), setOf("shock"))

    @Test fun strokeSigns() = check("stroke",
        arrayOf(*calmThreats, "seen=face_speech", "face=droops", "arm=drifts", "speech=slurred"),
        setOf("stroke"))

    @Test fun ataxicAtAltitude() = check("stumbling at 4,000 m",
        arrayOf(*calmThreats, "altitude=yes", "walk=stumbles"),
        setOf("altitude"))

    @Test fun heatOnAHardDay() = check("heat",
        arrayOf("what=heat", "avpu=voice", "bleeding=none", "breathing=normal", "confused=yes", "hot=yes"),
        setOf("heat", "dehydration", "head"))

    @Test fun calmAndCheckedLeavesOnlyWhatIsUnasked() {
        val r = rank(key, answers(*calmThreats))
        assertEquals(setOf("chest", "stroke", "shock", "altitude"), r.still.map { it.page }.toSet())
        assertTrue(r.fits.isEmpty())
    }
}
