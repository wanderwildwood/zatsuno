package com.wanderwildwood.zatsuno.aid

/**
 * The "See Hypothermia" in a page's text, found so the title can be tapped to open that page.
 * A capital after "see" starts a title; several are joined by ", " and "or", as in "see
 * Hypothermia, Heat exhaustion and heat stroke, or Dehydration". Only whole titles count, the
 * longest first, so "Heat exhaustion and heat stroke" is never cut to a shorter one.
 */
object SeeLinks {

    data class Link(val start: Int, val end: Int, val page: String)

    private val see = Regex("""\b[Ss]ee (?=[A-Z])""")
    private val joins = listOf(", or ", " or ", ", ")

    /** [titles] maps each page's title to its id. A page is never linked from itself ([from]). */
    fun find(text: String, titles: Map<String, String>, from: String? = null): List<Link> {
        val longestFirst = titles.keys.sortedByDescending { it.length }
        val links = mutableListOf<Link>()
        for (m in see.findAll(text)) {
            var at = m.range.last + 1
            while (true) {
                val title = longestFirst.firstOrNull { text.startsWith(it, at) } ?: break
                val id = titles.getValue(title)
                if (id != from) links += Link(at, at + title.length, id)
                at += title.length
                val join = joins.firstOrNull { text.startsWith(it, at) } ?: break
                at += join.length
            }
        }
        return links
    }
}
