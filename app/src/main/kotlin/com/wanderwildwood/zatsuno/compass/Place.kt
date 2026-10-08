package com.wanderwildwood.zatsuno.compass

import com.wanderwildwood.zatsuno.coord.CoordinateFormatter
import com.wanderwildwood.zatsuno.coord.Mgrs
import java.util.Locale

/**
 * A position, written out the ways other apps and other people can use it. No Android in here,
 * so the exact text can be tested.
 *
 * Every number is written with [Locale.US]. A phone set to a comma-decimal language would
 * otherwise write `geo:35,59512,-82,55123`, which no map reads the way it was meant.
 */
object Place {

    /** For a map app on this phone: a point, with a label where the app shows one. */
    fun geoUri(lat: Double, lon: Double, label: String): String {
        val point = String.format(Locale.US, "%.6f,%.6f", lat, lon)
        return "geo:$point?q=$point(${encode(label)})"
    }

    /** The bare `geo:` form, for text a person may paste anywhere. */
    fun geoText(lat: Double, lon: Double): String = String.format(Locale.US, "geo:%.5f,%.5f", lat, lon)

    /**
     * A web link to the same point, for the person the text goes to: a `geo:` address is not
     * a link in most messaging apps, and theirs may not be this phone. Field Kit itself never
     * opens it.
     */
    fun webLink(lat: Double, lon: Double): String =
        String.format(Locale.US, "https://www.openstreetmap.org/?mlat=%.5f&mlon=%.5f#map=16/%.5f/%.5f", lat, lon, lat, lon)

    /**
     * The message sent from "Share position": a first line in the reader's language (given
     * already worded, with the time and accuracy in it), then the position three ways
     * (decimal, DMS, and the US National Grid / MGRS reference where there is one), then
     * the two addresses.
     */
    fun shareText(heading: String, lat: Double, lon: Double): String = listOfNotNull(
        heading,
        CoordinateFormatter.decimal(lat, lon),
        CoordinateFormatter.dms(lat, lon),
        Mgrs.format(lat, lon)?.let { "USNG $it" },
        geoText(lat, lon),
        webLink(lat, lon),
    ).joinToString("\n")

    /** Metres of accuracy as a whole number; under one metre still reads as 1. */
    fun metres(accuracy: Float): String = String.format(Locale.US, "%d", maxOf(1, Math.round(accuracy)))

    private fun encode(s: String): String = buildString {
        for (ch in s) {
            when {
                ch.isLetterOrDigit() && ch.code < 128 -> append(ch)
                ch == ' ' -> append("%20")
                else -> for (b in ch.toString().toByteArray(Charsets.UTF_8)) append(String.format(Locale.US, "%%%02X", b))
            }
        }
    }
}
