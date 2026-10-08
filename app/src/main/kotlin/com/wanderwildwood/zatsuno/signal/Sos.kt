package com.wanderwildwood.zatsuno.signal

/**
 * SOS in Morse, · · · – – – · · ·, as the flash plays it: on for a dot or a dash, off for the
 * gaps between. One unit is a dot; a dash is three; the gap inside a letter is one, between
 * letters three, and after the whole word seven, so the pattern reads as SOS and not as a
 * flicker. No Android in here: the sequence is what the test checks.
 */
object Sos {

    /** The flash on or off, and for how long. */
    data class Step(val on: Boolean, val millis: Long)

    /** A dot, in milliseconds. At 250 one SOS takes eight and a half seconds. */
    const val UNIT = 250L

    private const val DOT = 1
    private const val DASH = 3
    private const val GAP = 1
    private const val LETTER_GAP = 3
    private const val WORD_GAP = 7

    /** One SOS, ending in the word gap, so played on repeat it keeps its rhythm. */
    fun sequence(unit: Long = UNIT): List<Step> = buildList {
        val letters = listOf(DOT, DASH, DOT)
        letters.forEachIndexed { i, mark ->
            repeat(3) { j ->
                add(Step(true, mark * unit))
                if (j < 2) add(Step(false, GAP * unit))
            }
            add(Step(false, (if (i < letters.lastIndex) LETTER_GAP else WORD_GAP) * unit))
        }
    }
}
