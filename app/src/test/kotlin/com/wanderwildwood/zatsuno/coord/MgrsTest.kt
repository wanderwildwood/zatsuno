package com.wanderwildwood.zatsuno.coord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The grid reference against NGA's own conversion: every expected string here was produced by
 * GEOTRANS (through the `mgrs` Python package, NGA's library) at one-metre precision. Both
 * hemispheres, the Norway and Svalbard zone exceptions on both sides of their edges, the band
 * edges, the zone edges and the first and last zones.
 */
class MgrsTest {

    private data class Ref(val name: String, val lat: Double, val lon: Double, val mgrs: String)

    private val refs = listOf(
        Ref("equator, prime meridian", 0.0, 0.0, "31N AA 66021 00000"),
        Ref("Washington Monument", 38.889484, -77.035278, "18S UJ 23479 06481"),
        Ref("Honolulu", 21.3069, -157.8583, "04Q FJ 18417 56542"),
        Ref("Fairbanks", 64.8378, -147.7164, "06W VS 66012 90570"),
        Ref("Sydney", -33.8688, 151.2093, "56H LH 34368 50948"),
        Ref("Cape Town", -33.9249, 18.4241, "34H BH 61881 43182"),
        Ref("Ushuaia", -54.8019, -68.303, "19F EV 44805 27029"),
        Ref("Wellington", -41.2865, 174.7762, "60G UV 13781 27052"),
        Ref("Chatham Islands", -43.95, -176.55, "01G EM 36108 33582"),
        Ref("Bergen (Norway exception)", 60.3913, 5.3221, "32V KN 97353 00648"),
        Ref("Norway exception west edge", 57.0, 3.0, "32V JJ 35784 33402"),
        Ref("Norway exception east edge", 57.0, 11.999, "32V PJ 82149 21385"),
        Ref("just outside Norway exception", 57.0, 12.0, "33V UD 17790 21388"),
        Ref("just south of Norway exception", 55.999, 5.0, "31U FC 24729 07773"),
        Ref("Longyearbyen (Svalbard 33X)", 78.2232, 15.6267, "33X WG 14278 83355"),
        Ref("Svalbard 31X", 78.0, 7.0, "31X EG 92770 61538"),
        Ref("Svalbard 35X", 78.0, 25.0, "35X MG 53588 59161"),
        Ref("Svalbard 37X", 78.0, 38.0, "37X DG 76791 58567"),
        Ref("Svalbard 33X east edge", 78.0, 20.999, "33X XG 39003 65494"),
        Ref("band X top", 83.999, 15.0, "33X WP 00000 27982"),
        Ref("band X start", 72.0, 9.0, "33X TV 93363 99233"),
        Ref("band W top", 71.999, 9.0, "32W NE 00000 88820"),
        Ref("band C bottom", -80.0, -60.0, "21C VM 41867 16915"),
        Ref("band M just south of equator", -1e-05, 30.0, "36M SE 66021 99998"),
        Ref("band N/P edge", 8.0, 100.0, "47P PJ 10204 84431"),
        Ref("Yellowstone (SOAP example)", 44.428, -110.5885, "12T WQ 32753 19493"),
        Ref("Mount Washington", 44.27056, -71.30333, "19T CK 16172 04504"),
        Ref("zone 1 west edge", 10.0, -179.999, "01P AM 71181 06907"),
        Ref("zone 60 east edge", 10.0, 179.999, "60P ZS 28818 06907"),
        Ref("Quito", -0.1807, -78.4678, "17M QV 81861 80007"),
        Ref("Reykjavik", 64.1466, -21.9426, "27W VM 54138 13689"),
        Ref("Tokyo", 35.6762, 139.6503, "54S UE 77855 48874"),
        Ref("zone edge lon 6E", 50.0, 6.0, "32U KA 85015 42944"),
        Ref("far from CM", 50.0, 5.999, "31U GR 14912 42941"),
        Ref("kCompass example point", 35.5951, -82.5512, "17S LV 59477 40147"),
    )

    @Test
    fun everyReferencePointMatchesNga() {
        for (r in refs) {
            // GEOTRANS writes the zone with a leading zero; the app writes it as people say it.
            assertEquals(r.name, r.mgrs.trimStart('0'), Mgrs.format(r.lat, r.lon))
        }
    }

    @Test
    fun utmAgreesWithProjToTheCentimetre() {
        // PROJ (cs2cs +proj=utm): zone 18N, 56S and 33N, and the equator on the prime meridian
        // at 166021.443 E, the figure every UTM text quotes.
        fun check(lat: Double, lon: Double, zone: Int, e: Double, n: Double) {
            val u = Mgrs.utm(lat, lon)
            assertEquals(zone, u.zone)
            assertEquals(e, u.easting, 0.01)
            assertEquals(n, u.northing, 0.01)
        }
        check(38.889484, -77.035278, 18, 323479.932, 4306481.423)
        check(0.0, 0.0, 31, 166021.443, 0.0)
        check(-33.8688, 151.2093, 56, 334368.634, 6250948.345)
        check(78.2232, 15.6267, 33, 514278.715, 8683355.469)
    }

    @Test
    fun zonesHonourNorwayAndSvalbard() {
        assertEquals(32, Mgrs.zone(60.0, 5.0))
        assertEquals(31, Mgrs.zone(55.9, 5.0))
        // 64°N is the first row of band W, out of the exception.
        assertEquals(31, Mgrs.zone(64.0, 5.0))
        assertEquals(31, Mgrs.zone(75.0, 8.9))
        assertEquals(33, Mgrs.zone(75.0, 9.0))
        assertEquals(33, Mgrs.zone(75.0, 20.9))
        assertEquals(35, Mgrs.zone(75.0, 21.0))
        assertEquals(35, Mgrs.zone(75.0, 32.9))
        assertEquals(37, Mgrs.zone(75.0, 33.0))
        assertEquals(37, Mgrs.zone(75.0, 41.9))
        assertEquals(38, Mgrs.zone(75.0, 42.0))
        assertEquals(1, Mgrs.zone(0.0, -180.0))
        assertEquals(60, Mgrs.zone(0.0, 180.0))
    }

    @Test
    fun bandsRunFromCToXWithXTwelveDegreesTall() {
        assertEquals('C', Mgrs.band(-80.0))
        assertEquals('M', Mgrs.band(-0.001))
        assertEquals('N', Mgrs.band(0.0))
        assertEquals('S', Mgrs.band(39.0))
        assertEquals('W', Mgrs.band(71.999))
        assertEquals('X', Mgrs.band(72.0))
        assertEquals('X', Mgrs.band(84.0))
    }

    @Test
    fun beyondTheGridThereIsNoReference() {
        assertNull(Mgrs.format(84.001, 10.0))
        assertNull(Mgrs.format(-80.001, 10.0))
        assertNull(Mgrs.format(Double.NaN, 10.0))
    }

    @Test
    fun digitsAreCutNotRounded() {
        // The Washington Monument sits at 323479.93 E: rounding would write 23480, the grid says 23479.
        assertEquals("18S UJ 23479 06481", Mgrs.format(38.889484, -77.035278))
    }
}
