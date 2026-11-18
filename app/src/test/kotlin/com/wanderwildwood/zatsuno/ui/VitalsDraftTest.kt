package com.wanderwildwood.zatsuno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

/** The vitals being entered: kept for their patient, and through the process being stopped. */
class VitalsDraftTest {

    @Test
    fun theSamePatientKeepsTheDraft() {
        val holder = DraftHolder()
        val d = holder.forPatient("1", null)
        d.pulse = "18"
        assertSame(d, holder.forPatient("1", null))
        assertEquals("18", holder.forPatient("1", null).pulse)
    }

    @Test
    fun anotherPatientStartsAfresh() {
        val holder = DraftHolder()
        holder.forPatient("1", null).pulse = "18"
        assertEquals("", holder.forPatient("2", null).pulse)
    }

    @Test
    fun aPatientBegunByTheFirstAnswerKeepsWhatWasTypedBefore() {
        val holder = DraftHolder()
        val d = holder.forPatient(null, null)
        d.breaths = "4"
        assertSame(d, holder.forPatient("7", "7"))
        assertNotSame(d, holder.forPatient("8", null))
    }

    @Test
    fun theDraftComesBackAfterTheProcessIsStopped() {
        val holder = DraftHolder()
        holder.forPatient("1", null).apply {
            avpu = "voice"; pulse = "21"; rhythm = "irregular"; breaths = "5"; quality = "labored"
            skin = listOf("pale", "cool", "clammy"); pupils = "equal"; counting = true
        }
        val back = DraftHolder.restore(holder.save()).forPatient("1", null)
        assertEquals("voice", back.avpu)
        assertEquals("21", back.pulse)
        assertEquals("irregular", back.rhythm)
        assertEquals("5", back.breaths)
        assertEquals("labored", back.quality)
        assertEquals(listOf("pale", "cool", "clammy"), back.skin)
        assertEquals("equal", back.pupils)
        assertEquals(false, back.counting)
    }

    @Test
    fun anEmptyDraftComesBackEmpty() {
        val back = DraftHolder.restore(DraftHolder().save()).forPatient(null, null)
        assertNull(back.avpu)
        assertEquals("", back.pulse)
        assertEquals(emptyList<String>(), back.skin)
        assertNull(back.pupils)
    }
}
