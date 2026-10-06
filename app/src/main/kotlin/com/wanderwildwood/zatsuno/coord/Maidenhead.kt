package com.wanderwildwood.zatsuno.coord

/**
 * The Maidenhead grid locator radio operators give for a place, eight characters long:
 *
 *   pair 1  field           A–R   20° of longitude / 10° of latitude
 *   pair 2  square          0–9    2° / 1°
 *   pair 3  subsquare       a–x    5′ / 2.5′
 *   pair 4  extended square 0–9   30″ / 15″
 *
 * Longitude takes the first character of each pair, latitude the second.
 *
 * Taken from kCompass by Ondřej Koloničný, OK1CDJ (github.com/ok1cdj/kCompass, GPL-3.0).
 */
object Maidenhead {

    fun encode(lat: Double, lon: Double): String {
        // Into positive ranges, held just inside the top so a pole or the antimeridian
        // does not run off the last letter.
        val adjLon = (lon + 180.0).coerceIn(0.0, 360.0 - 1e-9)
        val adjLat = (lat + 90.0).coerceIn(0.0, 180.0 - 1e-9)

        val sb = StringBuilder(8)
        sb.append('A' + (adjLon / 20.0).toInt())
        sb.append('A' + (adjLat / 10.0).toInt())
        sb.append('0' + ((adjLon % 20.0) / 2.0).toInt())
        sb.append('0' + (adjLat % 10.0).toInt())
        sb.append('a' + ((adjLon % 2.0) / (2.0 / 24.0)).toInt())
        sb.append('a' + ((adjLat % 1.0) / (1.0 / 24.0)).toInt())
        sb.append('0' + ((adjLon % (2.0 / 24.0)) / (2.0 / 240.0)).toInt())
        sb.append('0' + ((adjLat % (1.0 / 24.0)) / (1.0 / 240.0)).toInt())
        return sb.toString()
    }
}
