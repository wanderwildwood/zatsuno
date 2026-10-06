package com.wanderwildwood.zatsuno.coord

// The tests kCompass ships with its formatter (github.com/ok1cdj/kCompass, GPL-3.0); the decimal
// form here drops the degree sign so it can be pasted straight into a map search.

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoordinateFormatterTest {

    @Test
    fun decimalUsesDotAndFiveDecimals_noDegreeSign() {
        assertEquals("50.12345, 14.56789", CoordinateFormatter.decimal(50.12345, 14.56789))
    }

    @Test
    fun dmsNorthEast() {
        // 50.123456°, 14.567890°
        // lat: 50°07'24.4"N   lon: 14°34'04.4"E
        val s = CoordinateFormatter.dms(50.123456, 14.567890)
        assertEquals("50°07'24.4\"N  14°34'04.4\"E", s)
    }

    @Test
    fun dmsSouthWestHemispheres() {
        val s = CoordinateFormatter.dms(-33.8688, -151.2093)
        assertTrue("expected S hemisphere, got: $s", s.contains("\"S"))
        assertTrue("expected W hemisphere, got: $s", s.contains("\"W"))
    }

    @Test
    fun dmsSecondsRollOverToNextMinute() {
        // 0.0166638° ≈ 0°59.98'… the seconds round to 60 and must carry.
        val s = CoordinateFormatter.dms(0.0166638, 0.0)
        // Should not contain "60.0" seconds.
        assertTrue("seconds must not be 60: $s", !s.contains("60.0\""))
    }
}
