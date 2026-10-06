package com.wanderwildwood.zatsuno.coord

// The tests kCompass ships with its locator (github.com/ok1cdj/kCompass, GPL-3.0).

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MaidenheadTest {

    @Test
    fun encodesFullEightChars() {
        assertEquals(8, Maidenhead.encode(50.0, 14.0).length)
    }

    @Test
    fun newingtonCt_ArrlHq_isFN31pr() {
        // Classic ARRL reference point: 41.714 N, 72.727 W -> FN31pr…
        val loc = Maidenhead.encode(41.714, -72.727)
        assertEquals("FN31pr", loc.substring(0, 6))
    }

    @Test
    fun munich_isJN58td() {
        // Wikipedia worked example: 48.14666 N, 11.60833 E -> JN58td…
        val loc = Maidenhead.encode(48.14666, 11.60833)
        assertEquals("JN58td", loc.substring(0, 6))
    }

    @Test
    fun fieldIsUppercase_subsquareIsLowercase() {
        val loc = Maidenhead.encode(50.08, 14.42) // Prague
        assertTrue(loc[0].isUpperCase() && loc[1].isUpperCase())
        assertTrue(loc[4].isLowerCase() && loc[5].isLowerCase())
        assertTrue(loc[2].isDigit() && loc[3].isDigit() && loc[6].isDigit() && loc[7].isDigit())
    }

    @Test
    fun handlesExtremeCoordinatesWithoutOverflow() {
        // Just inside the poles/antimeridian must not throw or index past the field.
        assertEquals(8, Maidenhead.encode(89.999, 179.999).length)
        assertEquals(8, Maidenhead.encode(-89.999, -179.999).length)
    }
}
