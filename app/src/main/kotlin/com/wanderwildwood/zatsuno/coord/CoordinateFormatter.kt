package com.wanderwildwood.zatsuno.coord

import java.util.Locale
import kotlin.math.abs

/**
 * Latitude and longitude as text, with no Android in it so it can be tested on its own.
 *
 * Taken from kCompass by Ondřej Koloničný, OK1CDJ (github.com/ok1cdj/kCompass, GPL-3.0).
 * Everything is written with [Locale.US], so the decimal separator is a dot on every phone:
 * a position read out to a dispatcher or pasted into a map must not come out as "35,59512".
 */
object CoordinateFormatter {

    /** Decimal degrees to five places, about a metre: `35.59512, -82.55123`. */
    fun decimal(lat: Double, lon: Double): String =
        String.format(Locale.US, "%.5f, %.5f", lat, lon)

    /** Degrees, minutes and seconds with hemisphere letters: `35°35'42.4"N  82°33'04.4"W`. */
    fun dms(lat: Double, lon: Double): String =
        "${dmsComponent(lat, isLat = true)}  ${dmsComponent(lon, isLat = false)}"

    internal fun dmsComponent(value: Double, isLat: Boolean): String {
        val hemisphere = when {
            isLat -> if (value >= 0) "N" else "S"
            else -> if (value >= 0) "E" else "W"
        }
        var deg = abs(value).toInt()
        val minutesFull = (abs(value) - deg) * 60.0
        var min = minutesFull.toInt()
        var sec = (minutesFull - min) * 60.0

        // 59.95 seconds and up would print as 60.0 at one decimal place.
        if (sec >= 59.95) {
            sec = 0.0
            min += 1
            if (min >= 60) {
                min = 0
                deg += 1
            }
        }
        return String.format(Locale.US, "%d°%02d'%04.1f\"%s", deg, min, sec, hemisphere)
    }
}
