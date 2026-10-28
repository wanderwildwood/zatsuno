package com.wanderwildwood.zatsuno.key

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The life of an assessment: start, end (keep or discard), and a restart with none open. All made-up data. */
class AssessmentsTest {

    private class Fake : AssessmentPlatform {
        val open = linkedMapOf<String, String>()
        var kept: String? = null
        var active: String? = null
        var trash: String? = null
        var alarmSet = false
        var reminded: List<Incident> = emptyList()
        var notificationShowing = false
        override fun openIds() = open.keys.toList()
        override fun readOpen(id: String) = open[id]
        override fun writeOpen(id: String, text: String) { open[id] = text }
        override fun deleteOpen(id: String) { open.remove(id) }
        override fun readKept() = kept
        override fun writeKept(text: String) { kept = text }
        override fun deleteKept() { kept = null }
        override fun readActive() = active
        override fun writeActive(id: String?) { active = id }
        override fun readTrash() = trash
        override fun writeTrash(text: String) { trash = text }
        override fun deleteTrash() { trash = null }
        override fun remind(patients: List<Incident>) { alarmSet = true; reminded = patients }
        override fun cancelReminder() { alarmSet = false; notificationShowing = false; reminded = emptyList() }
        override fun dismissNotification() { notificationShowing = false }
    }

    private val first = Incident(1_000L) + Incident.Answer(1_000L, "safe", "yes") + Incident.Text(2_000L, "patient", "Test patient, made up")

    @Test fun startingSetsTheReminder() {
        val p = Fake(); val a = Assessments(p)
        assertNull(a.open())
        a.save(first)
        assertEquals(first, a.open())
        assertTrue(p.alarmSet)
    }

    @Test fun endKeepKeepsTheNoteAndClearsEverythingElse() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); p.notificationShowing = true
        a.end(first.id, keep = true)
        assertNull(a.open())
        assertEquals(first, a.kept())
        assertFalse(p.alarmSet); assertFalse(p.notificationShowing)
    }

    @Test fun endDiscardKeepsNothing() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); p.notificationShowing = true
        a.end(first.id, keep = false)
        assertNull(a.open()); assertNull(a.kept()); assertTrue(p.open.isEmpty())
        assertFalse(p.alarmSet); assertFalse(p.notificationShowing)
    }

    @Test fun theNextAssessmentStartsFresh() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(first.id, keep = true)
        val second = Incident(9_000L) + Incident.Answer(9_500L, "safe", "no")
        a.save(second)
        assertEquals(second, a.open())
        assertEquals(1, a.open()!!.events.size)
        // Keeping the second replaces the first as the last note.
        a.end(second.id, keep = true)
        assertEquals(second, a.kept())
    }

    @Test fun discardLeavesAnEarlierKeptNoteAlone() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(first.id, keep = true)
        val other = Incident(9_000L) + Incident.Answer(9_500L, "safe", "no")
        a.save(other); a.end(other.id, keep = false)
        assertEquals(first, a.kept())
        a.deleteKept()
        assertNull(a.kept())
    }

    @Test fun restartWithNoAssessmentSweepsTheReminder() {
        val p = Fake(); val a = Assessments(p)
        p.alarmSet = true; p.notificationShowing = true   // left behind by whatever
        a.sweep()
        assertFalse(p.alarmSet); assertFalse(p.notificationShowing)
        assertNull(a.open())
    }

    @Test fun restartWithAnOpenAssessmentKeepsItAndItsReminder() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); p.alarmSet = false
        Assessments(p).sweep()
        assertNotNull(Assessments(p).open())
        assertTrue(p.alarmSet)
    }

    @Test fun anUnreadableFileIsNoAssessmentAndIsSwept() {
        val p = Fake(); p.open["5"] = "garbage"; p.alarmSet = true
        val a = Assessments(p)
        assertNull(a.open())
        a.sweep()
        assertTrue(p.open.isEmpty()); assertFalse(p.alarmSet)
    }

    @Test fun endingWithNothingOpenIsHarmless() {
        val p = Fake(); val a = Assessments(p)
        a.end("123", keep = true)
        assertNull(a.kept()); assertFalse(p.alarmSet)
    }

    private val second = Incident(5_000_000L) + Incident.Answer(5_000_000L, "safe", "yes") + Incident.Text(5_001_000L, "name", "Test Person B")

    @Test fun severalPatientsAreOpenAtOnceAndTheLatestShows() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.save(second)
        assertEquals(listOf(first, second), a.patients())
        assertEquals(second, a.open())
        a.show(first.id)
        assertEquals(first, a.open())
        // Saving a change to the shown patient leaves the other alone.
        val changed = first + Incident.Done(3_000L, "Splinted")
        a.save(changed)
        assertEquals(listOf(changed, second), a.patients())
        assertEquals(2, p.reminded.size)
    }

    @Test fun aNewPatientIsBlankUntilTheFirstAnswer() {
        val p = Fake(); val a = Assessments(p)
        a.save(first)
        a.show(null)
        assertNull(a.open())
        assertEquals(1, a.patients().size)
        a.save(second)
        assertEquals(second, a.open())
        assertEquals(2, a.patients().size)
    }

    @Test fun endingOnePatientLeavesTheOthersAndTheirReminder() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.save(second); p.notificationShowing = true
        a.end(second.id, keep = true)
        assertEquals(listOf(first), a.patients())
        assertEquals(first, a.open())
        assertEquals(second, a.kept())
        assertTrue(p.alarmSet); assertEquals(listOf(first), p.reminded); assertFalse(p.notificationShowing)
        a.end(first.id, keep = false)
        assertFalse(p.alarmSet)
    }

    @Test fun resetClearsThePatientAndLeavesANewOne() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.save(second)
        a.reset(second.id)
        assertEquals(listOf(first), a.patients())
        assertNull(a.open())
        assertEquals(Assessments.OPEN, a.undoable())
        assertEquals(Assessments.OPEN, a.undo())
        assertEquals(listOf(first, second), a.patients())
        assertEquals(second, a.open())
    }

    @Test fun aDiscardedNoteCanBePutBack() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(first.id, keep = false)
        assertNull(a.open())
        assertEquals(Assessments.OPEN, a.undo())
        assertEquals(first, a.open())
        assertTrue(p.alarmSet)
        assertNull(a.undoable())
    }

    @Test fun aDeletedKeptNoteCanBePutBack() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(first.id, keep = true)
        a.deleteKept()
        assertNull(a.kept())
        assertEquals(Assessments.KEPT, a.undo())
        assertEquals(first, a.kept())
        assertNull(a.undo())
    }

    @Test fun undoIsOnlyForTenMinutes() {
        val p = Fake(); var t = 1_000_000L
        val a = Assessments(p) { t }
        a.save(first); a.end(first.id, keep = false)
        t += Assessments.UNDO_MS - 1
        assertEquals(Assessments.OPEN, a.undoable())
        t += 2
        assertNull(a.undoable()); assertNull(p.trash)
        assertNull(a.undo())
    }

    @Test fun theLastDeleteIsTheOnlyOneKept() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(first.id, keep = true); a.deleteKept()
        a.save(second); a.end(second.id, keep = false)
        assertEquals(Assessments.OPEN, a.undo())
        assertNull(a.kept())
    }

    @Test fun theEarliestReminderAmongPatientsWins() {
        val key = KeyFixtures.key
        val min = 60_000L
        val early = Incident(0L); val late = Incident(10 * min)
        val (who, at) = ReminderPlan.next(key, listOf(late, early), 1 * min, 0L)!!
        assertEquals(early, who)
        assertEquals(early.nextCheck(key), at)
        assertNull(ReminderPlan.next(key, emptyList(), 0L, 0L))
        // The first has been reminded about; the second is still to come.
        assertEquals(late, ReminderPlan.next(key, listOf(early, late), early.nextCheck(key) + min, early.nextCheck(key))!!.first)
    }

    // The reminder is given once per due time, not on every tap after it.
    @Test fun reminderRules() {
        val min = 60_000L
        assertEquals(15 * min, ReminderPlan.alarmAt(15 * min, 0, 1 * min, 0))          // not due yet: at the due time
        assertEquals(20 * min, ReminderPlan.alarmAt(15 * min, 0, 20 * min, 0))         // overdue and not yet given: now
        assertNull(ReminderPlan.alarmAt(15 * min, 0, 21 * min, 15 * min))              // already given for this due time
        assertEquals(40 * min, ReminderPlan.alarmAt(35 * min, 20 * min, 40 * min, 15 * min)) // a new due time is reminded
        assertNull(ReminderPlan.alarmAt(15 * min, 0, ReminderPlan.STALE_MS + min, 0))  // abandoned for 12 hours: no nagging
    }
}
