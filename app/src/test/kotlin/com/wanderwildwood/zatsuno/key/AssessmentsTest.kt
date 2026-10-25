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
        var open: String? = null
        var kept: String? = null
        var alarmSet = false
        var notificationShowing = false
        override fun readOpen() = open
        override fun writeOpen(text: String) { open = text }
        override fun deleteOpen() { open = null }
        override fun readKept() = kept
        override fun writeKept(text: String) { kept = text }
        override fun deleteKept() { kept = null }
        override fun remind(incident: Incident) { alarmSet = true }
        override fun cancelReminder() { alarmSet = false; notificationShowing = false }
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
        a.end(keep = true)
        assertNull(a.open())
        assertEquals(first, a.kept())
        assertFalse(p.alarmSet); assertFalse(p.notificationShowing)
    }

    @Test fun endDiscardKeepsNothing() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); p.notificationShowing = true
        a.end(keep = false)
        assertNull(a.open()); assertNull(a.kept()); assertNull(p.open)
        assertFalse(p.alarmSet); assertFalse(p.notificationShowing)
    }

    @Test fun theNextAssessmentStartsFresh() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(keep = true)
        val second = Incident(9_000L) + Incident.Answer(9_500L, "safe", "no")
        a.save(second)
        assertEquals(second, a.open())
        assertEquals(1, a.open()!!.events.size)
        // Keeping the second replaces the first as the last note.
        a.end(keep = true)
        assertEquals(second, a.kept())
    }

    @Test fun discardLeavesAnEarlierKeptNoteAlone() {
        val p = Fake(); val a = Assessments(p)
        a.save(first); a.end(keep = true)
        a.save(Incident(9_000L) + Incident.Answer(9_500L, "safe", "no")); a.end(keep = false)
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
        val p = Fake(); p.open = "garbage"; p.alarmSet = true
        val a = Assessments(p)
        assertNull(a.open())
        a.sweep()
        assertNull(p.open); assertFalse(p.alarmSet)
    }

    @Test fun endingWithNothingOpenIsHarmless() {
        val p = Fake(); val a = Assessments(p)
        a.end(keep = true)
        assertNull(a.kept()); assertFalse(p.alarmSet)
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
