package com.wanderwildwood.zatsuno.compass

import org.junit.Assert.assertEquals
import org.junit.Test

class BearingTest {
    @Test
    fun trueAddsEastDeclination() {
        assertEquals(96f, Bearing.toTrue(90f, 6f), 0.001f)
        assertEquals(354f, Bearing.toTrue(2f, -8f), 0.001f)
        assertEquals(1f, Bearing.toTrue(355f, 6f), 0.001f)
    }

    @Test
    fun wholeDegreesNeverReach360() {
        assertEquals(0, Bearing.whole(359.6f))
        assertEquals(359, Bearing.whole(359.4f))
        assertEquals(0, Bearing.whole(-0.2f))
    }

    @Test
    fun eightPoints() {
        assertEquals(0, Bearing.point(0f))
        assertEquals(0, Bearing.point(22f))
        assertEquals(1, Bearing.point(23f))
        assertEquals(4, Bearing.point(180f))
        assertEquals(7, Bearing.point(315f))
        assertEquals(0, Bearing.point(350f))
    }

    @Test
    fun apartTakesTheShortWay() {
        assertEquals(4f, Bearing.apart(358f, 2f), 0.001f)
        assertEquals(180f, Bearing.apart(0f, 180f), 0.001f)
    }

    @Test
    fun declinationReadsAsPeopleWriteIt() {
        assertEquals("6.1° W", Bearing.declinationText(-6.1f, "E", "W"))
        assertEquals("3.0° E", Bearing.declinationText(3f, "E", "W"))
        assertEquals("0.0°", Bearing.declinationText(0.01f, "E", "W"))
    }
}
