package com.wanderwildwood.zatsuno.search

import java.text.Normalizer
import java.util.Locale

/** Which part of the app something lives in. */
enum class Kind { AID, KNOT, COMPASS, CARD }

/** One thing that can be found: a first-aid page, a knot, the compass. */
data class Entry(
    val kind: Kind,
    val id: String,
    val title: String,
    val keywords: List<String>,
    val body: String,
)

data class Hit(val entry: Entry, val snippet: String?)

/**
 * Search across all three parts, with no index file: there are a few dozen entries, and
 * reading them all on each key press is quicker than the panel can redraw.
 *
 * Every word typed must start a word somewhere in the entry ("burn" finds "burns", "tick"
 * finds "ticks"). A word in the title counts most, then a keyword, then the body; the line
 * shown under a result is the first sentence of the body holding the first word typed.
 */
object Search {

    fun find(entries: List<Entry>, query: String): List<Hit> {
        val terms = words(query)
        if (terms.isEmpty()) return emptyList()
        return entries.mapNotNull { e ->
            val title = words(e.title)
            val keys = e.keywords.flatMap(::words)
            val body = words(e.body)
            var score = 0
            for (t in terms) {
                val s = when {
                    title.any { it.startsWith(t) } -> 10
                    keys.any { it.startsWith(t) } -> 4
                    body.any { it.startsWith(t) } -> 1
                    else -> return@mapNotNull null
                }
                score += s
            }
            Triple(e, score, snippet(e.body, terms.first()))
        }
            .sortedWith(compareByDescending<Triple<Entry, Int, String?>> { it.second }.thenBy { it.first.kind.ordinal })
            .map { (e, _, snip) -> Hit(e, snip) }
    }

    /**
     * Whether some word of [query] is found in [rest] and not in [head]: a page found by words
     * that are only under its More opens with More open.
     */
    fun onlyIn(rest: String, head: String, query: String): Boolean {
        val inHead = words(head)
        val inRest = words(rest)
        return words(query).any { t -> inHead.none { it.startsWith(t) } && inRest.any { it.startsWith(t) } }
    }

    /** Lower case, accents off, split on anything that is not a letter or digit. */
    fun words(text: String): List<String> =
        fold(text).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }

    private fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase(Locale.ROOT)

    private fun snippet(body: String, term: String): String? {
        val sentences = body.split(Regex("(?<=[.!?])\\s+|\\n"))
        return sentences.firstOrNull { s -> words(s).any { it.startsWith(term) } }?.trim()?.take(140)
    }
}
