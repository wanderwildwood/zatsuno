package com.wanderwildwood.zatsuno.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class PlaceTest {
    @Test
    fun geoUriUsesDotsEvenInACommaLocale() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("geo:35.595120,-82.551230?q=35.595120,-82.551230(My%20position)", Place.geoUri(35.59512, -82.55123, "My position"))
            assertEquals("geo:35.59512,-82.55123", Place.geoText(35.59512, -82.55123))
        } finally {
            Locale.setDefault(before)
        }
    }

    @Test
    fun aLabelOutsideAsciiIsEncoded() {
        assertTrue(Place.geoUri(0.0, 0.0, "Pozycja ż").endsWith("(Pozycja%20%C5%BC)"))
    }

    @Test
    fun shareTextHasEveryForm() {
        val t = Place.shareText("My position at 14:05, to within 8 m:", 51.47793, -0.00148)
        assertEquals(
            listOf(
                "My position at 14:05, to within 8 m:",
                "51.47793, -0.00148",
                "51°28'40.5\"N  0°00'05.3\"W",
                "geo:51.47793,-0.00148",
                "https://www.openstreetmap.org/?mlat=51.47793&mlon=-0.00148#map=16/51.47793/-0.00148",
            ),
            t.lines(),
        )
    }

    @Test
    fun metresAreWholeAndAtLeastOne() {
        assertEquals("8", Place.metres(7.6f))
        assertEquals("1", Place.metres(0.2f))
    }
}
