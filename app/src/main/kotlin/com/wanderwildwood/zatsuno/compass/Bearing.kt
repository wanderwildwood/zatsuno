package com.wanderwildwood.zatsuno.compass

import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** Angles on a compass card, with no Android in them. */
object Bearing {

    /** Any angle into 0 up to (not including) 360. */
    fun normalise(degrees: Float): Float = ((degrees % 360f) + 360f) % 360f

    /**
     * True north from magnetic: add the declination, east positive. Where the needle points
     * 6° east of true north, a magnetic 90 is a true 96.
     */
    fun toTrue(magnetic: Float, declination: Float): Float = normalise(magnetic + declination)

    /** The shorter way round between two headings, 0 to 180. */
    fun apart(a: Float, b: Float): Float {
        var d = abs(a - b) % 360f
        if (d > 180f) d = 360f - d
        return d
    }

    /** The whole degree a heading reads as, 0 to 359 (359.6 reads as 0, not 360). */
    fun whole(degrees: Float): Int = normalise(degrees).roundToInt() % 360

    /** Eight points, by index: N, NE, E, SE, S, SW, W, NW. */
    fun point(degrees: Float): Int = ((normalise(degrees) + 22.5f) / 45f).toInt() % 8

    /** A declination as people write it: "6.1° W", "0.0°". */
    fun declinationText(declination: Float, east: String, west: String): String = when {
        abs(declination) < 0.05f -> String.format(Locale.US, "%.1f°", 0f)
        declination > 0 -> String.format(Locale.US, "%.1f° %s", declination, east)
        else -> String.format(Locale.US, "%.1f° %s", -declination, west)
    }
}
