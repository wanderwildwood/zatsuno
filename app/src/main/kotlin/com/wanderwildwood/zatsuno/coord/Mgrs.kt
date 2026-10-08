package com.wanderwildwood.zatsuno.coord

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt

/**
 * The position as a US National Grid / MGRS reference, `17S LV 59477 40147`: the grid zone
 * (UTM zone and latitude band), the 100 km square, then easting and northing to the metre.
 * It is what search and rescue teams and NGA maps use, and a call-taker can take it down
 * without a decimal point going astray. No Android in here, so it can be tested on its own.
 *
 * WGS84 to UTM is Krüger's series as GeographicLib and the Wikipedia UTM article give it,
 * good to well under a millimetre inside a zone. The zone exceptions around Norway and
 * Svalbard are honoured, and the digits are cut, not rounded, as the grid defines them.
 * Beyond 84°N and 80°S the grid changes to polar stereographic squares, which nobody walks
 * with this phone, so there it answers null and the position shows without a grid line.
 */
object Mgrs {

    private const val A = 6378137.0
    private const val F = 1 / 298.257223563
    private const val K0 = 0.9996
    private const val FALSE_EASTING = 500_000.0
    private const val FALSE_NORTHING_SOUTH = 10_000_000.0

    /** Latitude bands, 8° each from 80°S; X runs 12°, to 84°N. */
    private const val BANDS = "CDEFGHJKLMNPQRSTUVWX"

    /** The 100 km column letters, three sets of eight, used in turn by zones 1, 2, 3, 1, 2, 3 … */
    private val COLUMNS = listOf("ABCDEFGH", "JKLMNPQR", "STUVWXYZ")

    /** The 100 km row letters, repeating every 2,000 km; even zones start five letters along. */
    private const val ROWS = "ABCDEFGHJKLMNPQRSTUV"

    /** A UTM position: the zone and whether it is north of the equator, then metres. */
    data class Utm(val zone: Int, val north: Boolean, val easting: Double, val northing: Double)

    /** The grid reference, or null beyond the grid's reach (84°N, 80°S). */
    fun format(lat: Double, lon: Double): String? {
        if (lat.isNaN() || lon.isNaN() || lat < -80.0 || lat > 84.0) return null
        val utm = utm(lat, lon)
        val e100 = floor(utm.easting / 100_000).toInt()
        val n100 = floor(utm.northing / 100_000).toInt()
        val column = COLUMNS[(utm.zone - 1) % 3][e100 - 1]
        val row = ROWS[(n100 + if (utm.zone % 2 == 0) 5 else 0) % 20]
        val e = floor(utm.easting).toLong() % 100_000
        val n = floor(utm.northing).toLong() % 100_000
        return String.format(Locale.US, "%d%s %s%s %05d %05d", utm.zone, band(lat), column, row, e, n)
    }

    /** The latitude band letter for [lat], which must be within the grid. */
    fun band(lat: Double): Char = BANDS[minOf(19, floor((lat + 80) / 8).toInt())]

    /**
     * The UTM zone a point is in. Norway's southwest (56°–64°N, 3°–12°E) is all zone 32, and
     * Svalbard (72°–84°N) uses only the odd zones 31, 33, 35 and 37, each widened.
     */
    fun zone(lat: Double, lon: Double): Int {
        var zone = (floor((lon + 180) / 6).toInt() + 1).coerceIn(1, 60)
        if (lat >= 56.0 && lat < 64.0 && lon >= 3.0 && lon < 12.0) zone = 32
        if (lat >= 72.0 && lat <= 84.0) {
            zone = when {
                lon < 0.0 -> zone
                lon < 9.0 -> 31
                lon < 21.0 -> 33
                lon < 33.0 -> 35
                lon < 42.0 -> 37
                else -> zone
            }
        }
        return zone
    }

    /** WGS84 to UTM in the zone [zone] would pick. */
    fun utm(lat: Double, lon: Double): Utm {
        val zone = zone(lat, lon)
        val lon0 = (zone - 1) * 6 - 180 + 3
        val phi = Math.toRadians(lat)
        val lambda = Math.toRadians(lon - lon0)

        val n = F / (2 - F)
        val n2 = n * n; val n3 = n2 * n; val n4 = n3 * n; val n5 = n4 * n; val n6 = n5 * n
        val a = A / (1 + n) * (1 + n2 / 4 + n4 / 64 + n6 / 256)
        val alpha = doubleArrayOf(
            n / 2 - 2 * n2 / 3 + 5 * n3 / 16 + 41 * n4 / 180 - 127 * n5 / 288 + 7891 * n6 / 37800,
            13 * n2 / 48 - 3 * n3 / 5 + 557 * n4 / 1440 + 281 * n5 / 630 - 1983433 * n6 / 1935360,
            61 * n3 / 240 - 103 * n4 / 140 + 15061 * n5 / 26880 + 167603 * n6 / 181440,
            49561 * n4 / 161280 - 179 * n5 / 168 + 6601661 * n6 / 7257600,
            34729 * n5 / 80640 - 3418889 * n6 / 1995840,
            212378941 * n6 / 319334400,
        )

        val twoSqrtNOver1PlusN = 2 * sqrt(n) / (1 + n)
        val sinPhi = sin(phi)
        val t = sinh(atanh(sinPhi) - twoSqrtNOver1PlusN * atanh(twoSqrtNOver1PlusN * sinPhi))
        val xiPrime = atan2(t, cos(lambda))
        val etaPrime = atanh(sin(lambda) / sqrt(1 + t * t))

        var xi = xiPrime
        var eta = etaPrime
        for (j in 1..6) {
            xi += alpha[j - 1] * sin(2 * j * xiPrime) * cosh(2 * j * etaPrime)
            eta += alpha[j - 1] * cos(2 * j * xiPrime) * sinh(2 * j * etaPrime)
        }

        val easting = FALSE_EASTING + K0 * a * eta
        var northing = K0 * a * xi
        val north = lat >= 0.0
        if (!north) northing += FALSE_NORTHING_SOUTH
        return Utm(zone, north, easting, northing)
    }

    private fun atanh(x: Double): Double = 0.5 * Math.log((1 + x) / (1 - x))
}
