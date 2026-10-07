package com.wanderwildwood.zatsuno.key

/**
 * One incident worked through: everything the person answered, wrote or checked, each with its
 * time, in the order it happened. The answers, the vitals and the note are all read off this
 * list, so nothing is filled in twice and every line of the note has a time.
 *
 * There is one open incident at a time. It is saved after every change, so it outlives the
 * app being stopped, and it is cleared only by "Start over".
 */
data class Incident(val started: Long, val events: List<Event> = emptyList()) {

    sealed interface Event { val at: Long }

    /** A question answered: an answer id, [Key.NOT_SURE], several ids joined by commas for a pick-all, or "" for none. */
    data class Answer(override val at: Long, val question: String, val value: String) : Event

    /** A line written in: who they are, SAMPLE, OPQRST. */
    data class Text(override val at: Long, val field: String, val text: String) : Event

    /** One check of the vitals. Pulse and breaths are per minute. */
    data class Check(
        override val at: Long,
        val avpu: String?,
        val pulse: Int?,
        val breaths: Int?,
        val skin: List<String>,
    ) : Event

    /** Something done for them: "Splinted the leg". */
    data class Done(override val at: Long, val text: String) : Event

    /** Where the phone was. */
    data class Where(override val at: Long, val lat: Double, val lon: Double, val accuracy: Float?) : Event

    operator fun plus(e: Event) = copy(events = events + e)

    /** The answers as they stand now, with the two read off the latest vitals. */
    fun answers(): Answers {
        var a = Answers()
        for (e in events) if (e is Answer) {
            val q = e.question
            a = when {
                e.value.isEmpty() -> a.without(q)
                e.value == Key.NOT_SURE -> a.with(q, Key.NOT_SURE, e.at)
                ',' in e.value -> a.withMany(q, e.value.split(',').toSet(), e.at)
                else -> a.copy(sure = a.sure + (q to setOf(e.value)), notSure = a.notSure - q, times = a.times + (q to e.at))
            }
        }
        val last = checks().lastOrNull() ?: return a
        last.pulse?.let { if (it > FAST_PULSE) a = a.copy(sure = a.sure + ("pulse" to setOf("fast")), times = a.times + ("pulse" to last.at)) }
        if (last.skin.any { it in PALE_COOL }) a = a.copy(sure = a.sure + ("vskin" to setOf("pale_cool")), times = a.times + ("vskin" to last.at))
        return a
    }

    /** The picks of a pick-all question as they stand. */
    fun picked(question: String): Set<String> = answers().sure[question].orEmpty()

    /** Every answer a question has had, in order, each with its time: a recheck that changes one shows both. */
    fun history(question: String): List<Answer> {
        val all = events.filterIsInstance<Answer>().filter { it.question == question }
        // Taken back and given again is one answer, kept at its first time.
        val given = all.filter { it.value.isNotEmpty() }
        val h = given.filterIndexed { i, e -> i == 0 || e.value != given[i - 1].value }
        return if (all.lastOrNull()?.value?.isEmpty() == true) emptyList() else h
    }

    /** The latest of each written line. */
    fun texts(): Map<String, Text> = events.filterIsInstance<Text>().associateBy { it.field }.filterValues { it.text.isNotBlank() }

    fun checks(): List<Check> = events.filterIsInstance<Check>()

    fun done(): List<Done> = events.filterIsInstance<Done>()

    fun where(): Where? = events.filterIsInstance<Where>().lastOrNull()

    /** When anything last happened: the end of the note's time span. */
    val last: Long get() = events.maxOfOrNull { it.at } ?: started

    /** Every 5 minutes while a danger page fits, every 15 otherwise. */
    fun recheckMinutes(key: Key): Int {
        val danger = key.pages.filter { it.danger }.map { it.id }.toSet()
        return if (rank(key, answers()).fits.any { it.page in danger }) 5 else 15
    }

    /** When the next recheck is due: from the last check, or from the start. */
    fun nextCheck(key: Key): Long = (checks().lastOrNull()?.at ?: started) + recheckMinutes(key) * 60_000L

    /** As text, a line to an event, for keeping on the phone. */
    fun encode(): String = buildString {
        append("S\t").append(started).append('\n')
        for (e in events) {
            when (e) {
                is Answer -> append("A\t${e.at}\t${esc(e.question)}\t${esc(e.value)}")
                is Text -> append("T\t${e.at}\t${esc(e.field)}\t${esc(e.text)}")
                is Check -> append("C\t${e.at}\t${e.avpu.orEmpty()}\t${e.pulse ?: ""}\t${e.breaths ?: ""}\t${e.skin.joinToString(",")}")
                is Done -> append("D\t${e.at}\t${esc(e.text)}")
                is Where -> append("W\t${e.at}\t${e.lat}\t${e.lon}\t${e.accuracy ?: ""}")
            }
            append('\n')
        }
    }

    companion object {
        const val FAST_PULSE = 100
        /** Skin words from the vitals that point to Shock. */
        val PALE_COOL = setOf("pale", "grey", "cool", "clammy")

        /** Back from [encode]; a line that can't be read is left out rather than losing the rest. */
        fun decode(text: String): Incident? {
            val lines = text.split('\n').filter { it.isNotEmpty() }
            val start = lines.firstOrNull()?.split('\t')?.takeIf { it[0] == "S" }?.getOrNull(1)?.toLongOrNull() ?: return null
            val events = lines.drop(1).mapNotNull { line ->
                val f = line.split('\t')
                val at = f.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                runCatching {
                    when (f[0]) {
                        "A" -> Answer(at, unesc(f[2]), unesc(f[3]))
                        "T" -> Text(at, unesc(f[2]), unesc(f[3]))
                        "C" -> Check(at, f[2].ifEmpty { null }, f[3].toIntOrNull(), f[4].toIntOrNull(), f[5].split(',').filter { it.isNotEmpty() })
                        "D" -> Done(at, unesc(f[2]))
                        "W" -> Where(at, f[2].toDouble(), f[3].toDouble(), f[4].toFloatOrNull())
                        else -> null
                    }
                }.getOrNull()
            }
            return Incident(start, events)
        }

        private fun esc(s: String) = s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n")

        private fun unesc(s: String) = buildString {
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    append(when (s[i + 1]) { 't' -> '\t'; 'n' -> '\n'; else -> s[i + 1] }); i += 2
                } else { append(c); i++ }
            }
        }
    }
}
