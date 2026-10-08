package com.wanderwildwood.zatsuno.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The flash plays · · · – – – · · · with Morse's gaps, and ends on the word gap. */
class SosTest {

    private val unit = 100L
    private val steps = Sos.sequence(unit)

    @Test
    fun nineFlashesThreeShortThreeLongThreeShort() {
        val on = steps.filter { it.on }.map { it.millis / unit }
        assertEquals(listOf(1L, 1, 1, 3, 3, 3, 1, 1, 1), on)
    }

    @Test
    fun theGapsAreMorsesGaps() {
        val off = steps.filter { !it.on }.map { it.millis / unit }
        // One between marks, three between letters, seven after the word.
        assertEquals(listOf(1L, 1, 3, 1, 1, 3, 1, 1, 7), off)
    }

    @Test
    fun onAndOffAlternateAndTheWholeTakes34Units() {
        for (i in 1 until steps.size) assertTrue(steps[i].on != steps[i - 1].on)
        assertTrue(steps.first().on)
        assertFalse(steps.last().on)
        assertEquals(34 * unit, steps.sumOf { it.millis })
    }

    @Test
    fun theDefaultDotIsAQuarterSecond() {
        assertEquals(250L, Sos.UNIT)
        assertEquals(34 * 250L, Sos.sequence().sumOf { it.millis })
    }
}
