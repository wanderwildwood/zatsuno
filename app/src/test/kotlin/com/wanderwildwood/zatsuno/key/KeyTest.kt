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
        assertEquals("the key names every page once, in the list's order", KeyFixtures.pageIds, key.pages.map { it.id })
        for (p in key.pages) for (c in p.points + p.rules) for (a in c.atoms) assertTrue("${p.id}: $a", valid(a))
        for (q in key.questions) for (c in q.after) for (a in c.atoms) assertTrue("${q.id} after $a", valid(a))
    }

    @Test
    fun everyDangerPageHasSomethingPointingToIt() {
        for (p in key.pages) assertTrue("${p.id} has no points line", p.points.isNotEmpty())
        assertTrue(dangerIds.size >= 10)
    }

    @Test
    fun onlyDangerPagesRuleOut_andNeverOnNotSureAPickAllOrTheVitals() {
        for (p in key.pages) {
            if (!p.danger) assertTrue("${p.id} is not a danger page but has rules", p.rules.isEmpty())
            for (c in p.rules) for (a in c.atoms) {
                assertFalse("${p.id}: rules on not sure", a.answer == Key.NOT_SURE)
                assertFalse("${p.id}: rules on a pick-all ($a): not picking is not a no", key.question(a.question)?.many ?: true)
            }
        }
    }

    @Test
    fun everyQuestionOffersNotSure_andNoneSpellsItOut() {
        // The screen adds "Not sure" to every single-answer question; the key must not list it as a real answer.
        for (q in key.questions) assertFalse(q.id, Key.NOT_SURE in q.answers)
        assertNotNull(strings["key_not_sure"])
    }

    @Test
    fun everyQuestionAndAnswerIsWorded() {
        val words = KeyFixtures.words
        for (q in key.questions) {
            words.question(q.id); words.short(q.id)
            if (q.grid) { q.places.forEach(words::place); q.findings.forEach(words::finding) }
            else q.answers.forEach { words.answer(q.id, it) }
        }
    }

    @Test
    fun chainOrderIsTheStageOrder() {
        val stages = key.questions.map { it.stage.ordinal }
        assertEquals(stages.sorted(), stages)
        // Heavy bleeding is asked first among the life threats.
        assertEquals("bleeding", key.questions.first { it.stage == Stage.THREATS }.id)
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

    @Test
    fun turningAnyAnswerToNotSureNeverHidesADangerPage() {
        val random = Random(20261007)
        val singles = key.questions.filter { !it.many }
        val many = key.questions.filter { it.many }
        var tried = 0
        repeat(100_000) {
            var a = Answers()
            for (q in singles) {
                when (random.nextInt(4)) {
                    0 -> {}
                    1 -> a = a.with(q.id, Key.NOT_SURE)
                    else -> a = a.with(q.id, q.answers.random(random))
                }
            }
            for (q in many) {
                val picked = q.answers.filter { random.nextInt(12) == 0 }.toSet()
                a = a.withMany(q.id, picked)
            }
            for ((d, values) in Key.DERIVED) if (random.nextBoolean()) a = a.with(d, values.first())
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
    fun pointsWinOverRules() {
        // "Shivering" picked and "not cold" answered: Hypothermia stays under Fits.
        assertTrue(rank(key, answers("seen=cold_shiver", "cold=no")).fits.any { it.page == "hypothermia" })
        assertTrue(rank(key, answers("seen=face_speech", "face=even", "arm=level", "speech=clear")).fits.any { it.page == "stroke" })
        assertTrue(rank(key, answers("seen=hard_breathe", "breathing=normal")).fits.any { it.page == "breathing" })
    }

    @Test
    fun aNotSureThatMightPointHereKeepsTheDangerPage() {
        // Found by the random run: "breathing normal, skin normal" rules Anaphylaxis out, but
        // with what stung them "not sure" and "hard to breathe" picked, it may be a bee.
        val sure = answers("breathing=normal", "skin=no", "seen=hard_breathe", "bite_kind=insect")
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

    @Test
    fun clearingEveryAnswerBringsEveryDangerPageBack() {
        val calm = answers("bleeding=none", "avpu=alert", "breathing=normal", "skin=no", "move=yes", "head_hit=no",
            "confused=no", "cold=no", "hot=no", "chest=no", "diabetic=no", "face=even", "arm=level", "speech=clear")
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
        assertEquals("chest", r.still.first { it.page == "chest" }.ask)
        assertEquals("face", r.still.first { it.page == "stroke" }.ask)
        assertEquals("arm", rank(key, answers("face=even")).still.first { it.page == "stroke" }.ask)
    }
}

/** Every page under Fits is there because of an answer, and one tap brings in only what it maps to. */
class NoFalsePositiveTest {

    @Test
    fun everyFitNamesAnAnswerThatPointsToIt() {
        val random = Random(7)
        repeat(5_000) {
            var a = Answers()
            for (q in key.questions) if (random.nextInt(3) == 0) {
                a = if (q.many) a.withMany(q.id, setOf(q.answers.random(random))) else a.with(q.id, q.answers.random(random))
            }
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
    }
}

/** Every first-aid page can be reached through the key, by answers the screen actually asks. */
class ReachableTest {

    @Test
    fun everyPageCanBeBroughtUnderFits() {
        assertEquals(KeyFixtures.pageIds.toSet(), key.pages.map { it.id }.toSet())
        for (p in key.pages) {
            val clause = p.points.first()
            var a = Answers()
            for (atom in clause.atoms) {
                val q = key.question(atom.question)
                // A follow-up is only on screen once what it follows holds: answer that too.
                q?.after?.firstOrNull()?.atoms?.forEach { a = if (key.question(it.question)!!.many) a.withMany(it.question, a.sure[it.question].orEmpty() + it.answer) else a.with(it.question, it.answer) }
                val value = if ('*' in atom.answer) q!!.answers.first { atom.holds(Answers(mapOf(q.id to setOf(it)))) } else atom.answer
                a = if (q?.many == true) a.withMany(atom.question, a.sure[atom.question].orEmpty() + value) else a.with(atom.question, value)
            }
            assertTrue("${p.id} from $clause", rank(key, a).fits.any { it.page == p.id })
        }
    }
}

/** WFA course cases: each set of answers lands on the pages a first-aider would open. */
class WalkthroughTest {

    private fun check(name: String, given: Array<String>, fits: Set<String>, still: Set<String> = emptySet()) {
        val r = rank(key, answers(*given))
        val got = r.fits.map { it.page }.toSet() - setOf("call", "scene")
        assertEquals("$name: fits", fits, got)
        assertTrue("$name: still ${r.still.map { it.page }} missing $still", r.still.map { it.page }.containsAll(still))
    }

    private val calmThreats = arrayOf("bleeding=none", "avpu=alert", "breathing=normal", "skin=no", "move=yes", "head_hit=no", "confused=no", "cold=no", "hot=no")

    @Test fun fallWithHeavyBleeding() = check("fall, heavy bleeding",
        arrayOf("safe=yes", "what=fall", "bleeding=heavy", "avpu=alert", "breathing=normal", "head_hit=not_sure", "seen=hurt_limb,wound"),
        setOf("bleeding", "shock", "fracture", "sprain", "wounds"), setOf("head"))

    @Test fun coldConfusedAndWet() = check("cold, confused, wet",
        arrayOf("what=cold", "bleeding=none", "avpu=voice", "breathing=normal", "confused=yes", "cold=yes", "seen=cold_shiver"),
        setOf("hypothermia", "head"), setOf("sugar", "stroke"))

    @Test fun beeStingWithBreathingTrouble() = check("bee sting, breathing",
        arrayOf("what=bite_sting", "bleeding=none", "avpu=alert", "breathing=struggling", "seen=bite_sting,rash_swelling", "bite_kind=insect"),
        setOf("anaphylaxis", "breathing", "allergy"))

    @Test fun chestPainOnAClimb() = check("chest pain on a climb",
        arrayOf("what=by_itself", *calmThreats, "seen=chest_pain,high_up", "skin=yes"),
        setOf("chest", "altitude", "shock"), setOf("stroke"))

    @Test fun childPulledFromWater() = check("child from water",
        arrayOf("what=water", "bleeding=none", "avpu=pain", "breathing=gasping", "cold=yes"),
        setOf("drowning", "cpr", "head", "hypothermia"))

    @Test fun diabeticActingDrunk() = check("diabetic acting drunk",
        arrayOf(*calmThreats, "confused=yes", "diabetic=yes"),
        setOf("sugar", "head"), setOf("stroke"))

    @Test fun snakebiteOnTheAnkle() = check("snakebite",
        arrayOf("what=bite_sting", *calmThreats, "seen=bite_sting", "bite_kind=snake"),
        setOf("snakebite"))

    @Test fun carbonMonoxideInATent() = check("CO in a tent",
        arrayOf("what=swallowed", "bleeding=none", "avpu=voice", "breathing=normal", "confused=yes"),
        setOf("poisoning", "head"))

    @Test fun lightningStrike() = check("lightning",
        arrayOf("what=lightning", "bleeding=none", "avpu=none", "breathing=gasping", "seen=burn"),
        setOf("lightning", "cpr", "head", "burns"))

    @Test fun deformedLegOnColdGround() = check("deformed leg, cold ground",
        arrayOf("what=fall", "bleeding=none", "avpu=alert", "breathing=normal", "cold=yes", "seen=hurt_limb", "deformed=yes", "body=legs.deformed"),
        setOf("fracture", "sprain", "hypothermia"), setOf("head", "shock"))

    @Test fun strokeSigns() = check("stroke",
        arrayOf(*calmThreats, "seen=face_speech", "face=droops", "arm=drifts", "speech=slurred"),
        setOf("stroke"))

    @Test fun heatOnAHardDay() = check("heat",
        arrayOf("what=heat", "bleeding=none", "avpu=voice", "breathing=normal", "confused=yes", "hot=yes"),
        setOf("heat", "dehydration", "head"))

    @Test fun calmAndCheckedLeavesOnlyWhatIsUnasked() {
        val r = rank(key, answers(*calmThreats))
        assertEquals(setOf("chest", "stroke"), r.still.map { it.page }.toSet())
        assertTrue(r.fits.isEmpty())
    }
}
