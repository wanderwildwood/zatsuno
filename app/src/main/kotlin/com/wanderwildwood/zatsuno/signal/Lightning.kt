package com.wanderwildwood.zatsuno.signal

import java.util.Locale

/**
 * Flash to thunder. Sound takes about five seconds a mile, three a kilometre, so the seconds
 * between seeing the flash and hearing the thunder say how far off that strike was. The
 * National Weather Service counts it that way and adds the part that matters: lightning
 * reaches 10 to 12 miles out from a storm, farther than thunder carries, so thunder you can
 * hear at all means you are in range. The old 30-30 rule's first half (shelter under 30
 * seconds) is gone; the second half, 30 minutes after the last thunder, stays.
 */
object Lightning {

    /** One count: when it was taken (wall clock, ms) and the seconds from flash to thunder. */
    data class Count(val at: Long, val seconds: Double)

    /** How many counts the page keeps, so a storm coming closer shows as a column of numbers. */
    const val KEEP = 6

    fun miles(seconds: Double): Double = seconds / 5
    fun km(seconds: Double): Double = seconds / 3

    /** "12.3 s", one decimal, with a dot whatever the phone's language. */
    fun seconds(seconds: Double): String = String.format(Locale.US, "%.1f", seconds)

    /** Distance to one decimal, "2.5"; under a tenth still reads 0.1 rather than 0.0. */
    fun distance(value: Double): String = String.format(Locale.US, "%.1f", maxOf(0.1, value))

    /** The counts with [count] added, newest last, the oldest dropped past [KEEP]. */
    fun record(counts: List<Count>, count: Count): List<Count> = (counts + count).takeLast(KEEP)

    /** The seconds between two elapsed-time readings, never negative, cut at a tenth. */
    fun elapsed(flashAt: Long, thunderAt: Long): Double = maxOf(0L, thunderAt - flashAt) / 100 / 10.0
}
