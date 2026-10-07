package com.wanderwildwood.zatsuno.key

/**
 * "Work through it": the response chain as questions, and which first-aid pages each answer
 * points to or rules out. Read from `assets/key/key.txt`, which holds ids only; the words
 * are in strings.xml.
 *
 * The shape is the mushroom key's (an [Answers] of sure answers and a not-sure set, and a
 * [rank] that returns what fits, what is still to check and what is ruled out), with its rule
 * turned round. That key will name candidates but never say something is edible; this one
 * names pages but never says someone is fine. Fit is yes or no, and the chain fixes the order
 * of asking, so none of the mushroom key's scoring is here.
 *
 * The rule that matters: **an answer that was not given never hides a danger page.** "Not
 * sure" is no answer at all. A danger page is ruled out only by a clause whose every part is a
 * sure answer, and an answer that points to it wins over one that rules it out. Nor is it ruled
 * out while a "not sure" might be the answer that points to it.
 */
enum class Stage { SCENE, THREATS, CALL, LOOK, TREAT, WATCH }

/** One `q=a`. A `*` in [answer] matches any part of a grid answer (`*.deformed`, `head.*`). */
data class Atom(val question: String, val answer: String) {
    fun holds(answers: Answers): Boolean {
        val given = answers.sure[question] ?: return false
        if ('*' !in answer) return answer in given
        val pattern = Regex(answer.split('*').joinToString("[^.]*") { Regex.escape(it) })
        return given.any { pattern.matches(it) }
    }

    override fun toString() = "$question=$answer"
}

/** Every [atoms] together. */
data class Clause(val atoms: List<Atom>) {
    fun holds(answers: Answers) = atoms.all { it.holds(answers) }
}

data class Question(
    val id: String,
    val stage: Stage,
    val answers: List<String>,
    val label: String? = null,
    val tag: String? = null,
    val many: Boolean = false,
    /** Shown only when one of these holds; empty for a question always shown. */
    val after: List<Clause> = emptyList(),
    /** Asked only to settle a page still to check. */
    val settle: Boolean = false,
    /** Head to toe: [answers] are `place.finding`. */
    val places: List<String> = emptyList(),
    val findings: List<String> = emptyList(),
) {
    val grid: Boolean get() = places.isNotEmpty()
}

data class KeyPage(
    val id: String,
    val danger: Boolean,
    val points: List<Clause>,
    val rules: List<Clause>,
)

data class Key(val questions: List<Question>, val pages: List<KeyPage>) {

    fun question(id: String): Question? = questions.firstOrNull { it.id == id }

    /** Where a question comes in the chain; derived answers (from the vitals) come last. */
    fun order(question: String): Int = questions.indexOfFirst { it.id == question }.let { if (it < 0) Int.MAX_VALUE else it }

    companion object {
        const val NOT_SURE = "not_sure"

        /** Answers that come from the vitals rather than a question. */
        val DERIVED = mapOf("pulse" to listOf("fast"), "vskin" to listOf("pale_cool"))

        fun parse(text: String): Key {
            val questions = mutableListOf<Question>()
            val pages = mutableListOf<KeyPage>()
            var stage: Stage? = null
            var page: KeyPage? = null
            fun flush() { page?.let { pages += it }; page = null }
            for ((n, raw) in text.lines().withIndex()) {
                val line = raw.substringBefore('#').trim()
                if (line.isEmpty()) continue
                val words = line.split(Regex("\\s+"))
                fun fail(why: String): Nothing = throw IllegalArgumentException("key.txt:${n + 1}: $why")
                when (words[0]) {
                    "stage" -> {
                        stage = Stage.entries.firstOrNull { it.name.equals(words.getOrNull(1), true) } ?: fail("no stage ${words.getOrNull(1)}")
                    }
                    "ask" -> {
                        val st = stage ?: fail("ask before any stage")
                        val head = line.substringBefore(" : ").split(Regex("\\s+"))
                        val answers = line.substringAfter(" : ", "").split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (answers.isEmpty()) fail("no answers")
                        val opts = head.drop(2)
                        fun opt(name: String) = opts.firstOrNull { it.startsWith("$name=") }?.substringAfter('=')
                        questions += Question(
                            id = head[1],
                            stage = st,
                            answers = answers,
                            label = opt("label"),
                            tag = opt("tag"),
                            many = "many" in opts,
                            after = opt("after")?.let(::clauses).orEmpty(),
                            settle = "settle" in opts,
                        )
                    }
                    "grid" -> {
                        val st = stage ?: fail("grid before any stage")
                        val p = words.indexOf("places"); val f = words.indexOf("findings")
                        if (p < 0 || f < p) fail("grid needs places then findings")
                        val places = words.subList(p + 1, f); val findings = words.drop(f + 1)
                        questions += Question(
                            id = words[1], stage = st, many = true,
                            answers = places.flatMap { pl -> findings.map { "$pl.$it" } },
                            places = places, findings = findings,
                        )
                    }
                    "page" -> {
                        flush()
                        page = KeyPage(words[1], danger = "danger" in words.drop(2), points = emptyList(), rules = emptyList())
                    }
                    "points" -> page = (page ?: fail("points outside a page")).let { it.copy(points = it.points + clauses(line.removePrefix("points"))) }
                    "rules" -> page = (page ?: fail("rules outside a page")).let { it.copy(rules = it.rules + clauses(line.removePrefix("rules"))) }
                    else -> fail("can't read \"$line\"")
                }
            }
            flush()
            return Key(questions, pages)
        }

        private fun clauses(text: String): List<Clause> = text.split('|').map { c ->
            Clause(c.split('&').map { a ->
                val t = a.trim()
                require('=' in t) { "\"$t\" is not q=a" }
                Atom(t.substringBefore('=').trim(), t.substringAfter('=').trim())
            })
        }
    }
}

/**
 * What has been answered. [sure] holds every real answer (a single answer is a set of one);
 * a question answered "not sure" is in [notSure] and not in [sure], so it counts for nothing.
 * [times] says when each was last answered, in milliseconds.
 */
data class Answers(
    val sure: Map<String, Set<String>> = emptyMap(),
    val notSure: Set<String> = emptySet(),
    val times: Map<String, Long> = emptyMap(),
) {
    fun single(question: String): String? = sure[question]?.singleOrNull()

    /** One answer to a single-answer question; [Key.NOT_SURE] puts it among the not sure. */
    fun with(question: String, answer: String, at: Long = 0L): Answers = when (answer) {
        Key.NOT_SURE -> copy(sure = sure - question, notSure = notSure + question, times = times + (question to at))
        else -> copy(sure = sure + (question to setOf(answer)), notSure = notSure - question, times = times + (question to at))
    }

    /** The whole set for a pick-all question; an empty set says nothing. */
    fun withMany(question: String, picked: Set<String>, at: Long = 0L): Answers =
        if (picked.isEmpty()) copy(sure = sure - question, times = times - question)
        else copy(sure = sure + (question to picked), notSure = notSure - question, times = times + (question to at))

    fun without(question: String): Answers = copy(sure = sure - question, notSure = notSure - question, times = times - question)
}

/** A page under Fits, and the answers that put it there. */
data class Fit(val page: String, val why: List<Atom>)

/** A danger page nothing has pointed to or ruled out, and the question that would settle it next. */
data class Still(val page: String, val ask: String?)

/** A danger page ruled out, and when the last of the answers that ruled it out was given. */
data class Ruled(val page: String, val by: List<Atom>, val at: Long)

data class Ranking(val fits: List<Fit>, val still: List<Still>, val ruledOut: List<Ruled>) {
    /** Every danger page not ruled out: what must stay in view. */
    val visible: Set<String> get() = (fits.map { it.page } + still.map { it.page }).toSet()
}

fun rank(key: Key, answers: Answers): Ranking {
    val fits = mutableListOf<Fit>()
    val still = mutableListOf<Still>()
    val ruled = mutableListOf<Ruled>()
    for (p in key.pages) {
        val pointing = p.points.filter { it.holds(answers) }
        if (pointing.isNotEmpty()) {
            // Points win over rules: "cold" and "warm and dry" both tapped keeps Hypothermia.
            fits += Fit(p.id, pointing.flatMap { it.atoms }.distinct())
            continue
        }
        if (!p.danger) continue
        // A "not sure" could be the answer that points here: then nothing rules the page out,
        // and that question is the one to settle. Without this, changing "Bee or wasp" to
        // "not sure" took away Anaphylaxis's pointer and let "breathing normal" rule it out.
        val maybe = p.points.firstOrNull { c -> c.atoms.all { it.holds(answers) || it.question in answers.notSure } }
        if (maybe != null) {
            still += Still(p.id, maybe.atoms.filter { it.question in answers.notSure }.minByOrNull { key.order(it.question) }?.question)
            continue
        }
        // Only clauses wholly made of sure answers rule anything out.
        val ruling = p.rules.firstOrNull { it.holds(answers) }
        if (ruling != null) {
            ruled += Ruled(p.id, ruling.atoms, ruling.atoms.maxOf { answers.times[it.question] ?: 0L })
        } else {
            still += Still(p.id, settle(key, p, answers))
        }
    }
    return Ranking(fits, still, ruled)
}

/**
 * The next question that would settle [page]: in the rules clause nearest to holding (none of
 * its parts answered otherwise, fewest still open), its first open part in chain order. Null
 * when no clause can rule the page out from here.
 */
private fun settle(key: Key, page: KeyPage, answers: Answers): String? {
    val open = page.rules.mapNotNull { clause ->
        val contradicted = clause.atoms.any { a -> answers.sure[a.question]?.let { !a.holds(answers) } ?: false }
        if (contradicted) null else clause.atoms.filterNot { it.holds(answers) }.map { it.question }
    }.filter { it.isNotEmpty() }
    // Fewest open first; between equals, the one asked earliest in the chain.
    val nearest = open.minWithOrNull(compareBy<List<String>>({ it.size }, { it.minOf(key::order) })) ?: return null
    return nearest.minByOrNull(key::order)
}
