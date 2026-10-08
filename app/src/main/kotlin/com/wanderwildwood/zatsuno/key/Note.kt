package com.wanderwildwood.zatsuno.key

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The words the key and its note are written in, by string name: `key_q_breathing`,
 * `key_a_breathing_normal`, `note_fits`. On the phone these are strings.xml; the tests read the
 * same file.
 */
fun interface Words {
    /** The string named [name], with [args] filled in as `%1$s`, `%2$s` …; the name itself when there is none. */
    fun get(name: String, vararg args: String): String

    fun question(q: String) = get("key_q_$q")
    /** The short name a question goes under in the note: "Breathing". */
    fun short(q: String) = get("key_n_$q")
    fun answer(q: String, a: String) = if (a == Key.NOT_SURE) get("key_not_sure") else get("key_a_${q}_$a")
    /** A decision's answer: "Not decided yet" where another question has "Not sure". */
    fun decided(q: String, a: String) = if (a == Key.NOT_SURE) get("key_not_decided") else get("key_a_${q}_$a")
    fun place(p: String) = get("key_place_$p")
    fun finding(f: String) = get("key_find_$f")
}

/** The written fields, in the order the note lists them, and the letter each goes beside. */
object Fields {
    const val WHO = "who"
    /** Scene size-up: who and what you have to work with. */
    const val RESOURCES = "resources"
    /** SAMPLE: signs and symptoms, allergies, medications, pertinent history, last intake and output, events. */
    val SAMPLE = listOf("symptoms" to "S", "allergies" to "A", "medicines" to "M", "past" to "P", "last" to "L", "events" to "E")
    val OPQRST = listOf("onset" to "O", "provokes" to "P", "quality" to "Q", "radiates" to "R", "severity" to "S", "time" to "T")
}

/**
 * The SOAP note, built from the incident as it stands. Nobody fills in a form: every line
 * comes from an answer, a set of vital signs or a line written in, with its time. S is the
 * patient, the mechanism, the chief complaint and the history; O the exam and the vital signs;
 * A the problem list and what is not yet ruled out, named by page, which is what the person
 * actually knows; P the treatment given, the call, the evacuation decision and the monitoring.
 */
class Note(
    private val key: Key,
    private val words: Words,
    private val titles: Map<String, String>,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val clock = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)

    fun time(at: Long): String = clock.format(Instant.ofEpochMilli(at).atZone(zone))

    /** Pages the key reaches from its own stages, so the A line leaves them out. */
    private val stagePages = setOf("call", "scene")

    fun text(incident: Incident): String {
        val out = mutableListOf<String>()
        out += words.get("note_head", time(incident.started), time(incident.last))
        out += where(incident)
        out += ""
        out += part("S", subjective(incident))
        out += part("O", objective(incident))
        out += part("A", assessment(incident))
        out += part("P", plan(incident))
        return out.joinToString("\n").trimEnd()
    }

    /** The verbal report, in the order a call-taker asks: where, the patient, the mechanism, their condition, the problem list, the treatment. */
    fun readOut(incident: Incident): String {
        val a = incident.answers()
        val out = mutableListOf<String>()
        out += where(incident)
        incident.texts()[Fields.WHO]?.let { out += words.get("note_who", it.text) }
        what(incident)?.let { out += it }
        val how = buildList {
            for (q in listOf("avpu", "airway", "breathing", "bleeding")) a.single(q)?.let { add("${words.short(q)}: ${words.answer(q, it)}") }
            incident.checks().lastOrNull()?.let { c ->
                c.pulse?.let { add(words.get("note_pulse", it.toString())) }
                c.breaths?.let { add(words.get("note_breaths", it.toString())) }
                if (c.skin.isNotEmpty()) add(words.get("note_skin_row") + " " + c.skin.joinToString(", ") { words.get("key_skin_$it") })
            }
        }
        if (how.isNotEmpty()) out += words.get("note_how", how.joinToString(". "))
        val fits = rank(key, a).fits.map { it.page }.filter { it !in stagePages }
        if (fits.isNotEmpty()) out += words.get("note_pages", fits.joinToString(", ") { titles[it] ?: it })
        val done = doneLines(incident)
        if (done.isNotEmpty()) out += words.get("note_done", done.joinToString(" "))
        return out.joinToString("\n")
    }

    private fun where(incident: Incident): String {
        val w = incident.where() ?: return words.get("note_where_none")
        val pos = String.format(Locale.ROOT, "%.5f, %.5f", w.lat, w.lon)
        return if (w.accuracy != null) words.get("note_where", pos, Math.round(w.accuracy).toString(), time(w.at))
        else words.get("note_where_no_accuracy", pos, time(w.at))
    }

    private fun part(letter: String, lines: List<String>): List<String> =
        lines.ifEmpty { listOf(words.get("note_nothing")) }.mapIndexed { i, l -> (if (i == 0) "$letter  " else "   ") + l }

    private fun what(incident: Incident): String? {
        val h = incident.history("what").lastOrNull() ?: return null
        return words.get("note_what", h.value.split(',').joinToString(", ") { words.answer("what", it) }, time(h.at))
    }

    /** Subjective: what the patient and bystanders say happened, the chief complaint, and the history. */
    private fun isSubjective(q: Question) = q.id == "storm" || q.id == "seen" || q.part == "sample"

    private fun subjective(incident: Incident): List<String> = buildList {
        val texts = incident.texts()
        texts[Fields.WHO]?.let { add(words.get("note_who", it.text) + " " + time(it.at)) }
        what(incident)?.let { add(it) }
        addAll(answered(incident) { isSubjective(it) })
        for ((f, _) in Fields.SAMPLE + Fields.OPQRST) texts[f]?.let { add("${words.get("key_field_$f")}: ${it.text} ${time(it.at)}") }
        texts[Fields.RESOURCES]?.let { add("${words.get("key_field_resources")}: ${it.text} ${time(it.at)}") }
    }

    private fun objective(incident: Incident): List<String> = buildList {
        addAll(answered(incident) { !isSubjective(it) })
        addAll(vitals(incident.checks()))
    }

    /** Each answered question that [take] keeps, in assessment order, one line to a question. */
    private fun answered(incident: Incident, take: (Question) -> Boolean): List<String> = buildList {
        for (q in key.questions) {
            if (q.decision || q.id == "what" || q.id == "you" || !take(q)) continue
            // A pick-all keeps only what stands now; a single answer keeps each change, so a reassessment shows.
            val h = incident.history(q.id).let { if (q.many) it.takeLast(1) else it }
            if (h.isEmpty()) continue
            val said = h.joinToString(", ") { e ->
                val v = when {
                    q.grid -> e.value.split(',').joinToString(", ") { g -> words.place(g.substringBefore('.')) + " " + words.finding(g.substringAfter('.')) }
                    q.many -> e.value.split(',').joinToString(", ") { words.answer(q.id, it) }
                    else -> words.answer(q.id, e.value)
                }
                "$v ${time(e.at)}"
            }
            add("${words.short(q.id)}: $said")
        }
    }

    /** The sets of vital signs as a table, one column per set, the newest on the right. */
    private fun vitals(checks: List<Incident.Check>): List<String> {
        if (checks.isEmpty()) return emptyList()
        val rows = vitalRows(words, checks) { time(it) }
        val label = rows.maxOf { it.first.length }
        val widths = checks.indices.map { i -> rows.maxOf { it.second[i].length } }
        return rows.map { (name, cells) ->
            (name.padEnd(label) + "  " + cells.mapIndexed { i, c -> c.padEnd(widths[i]) }.joinToString("  ")).trimEnd()
        }
    }

    private fun assessment(incident: Incident): List<String> = buildList {
        val r = rank(key, incident.answers())
        val fits = r.fits.map { it.page }.filter { it !in stagePages }
        add(if (fits.isEmpty()) words.get("note_fits_none") else words.get("note_fits", fits.joinToString(". ") { titles[it] ?: it } + "."))
        if (r.still.isNotEmpty()) {
            add(words.get("note_still", r.still.joinToString(". ") { s ->
                val t = titles[s.page] ?: s.page
                if (s.ask != null) words.get("note_still_one", t, words.short(s.ask)) else t
            } + "."))
        }
    }

    /** The treatment given and what was decided, in the order it happened. "Not decided yet" writes nothing. */
    private fun doneLines(incident: Incident): List<String> =
        (incident.done().map { it.at to it.text.trim().trimEnd('.') } +
            key.questions.filter { it.decision }.flatMap { q ->
                incident.history(q.id).filter { it.value != Key.NOT_SURE }.map { it.at to words.decided(q.id, it.value) }
            })
            .sortedBy { it.first }
            .map { (at, text) -> "$text ${time(at)}." }

    private fun plan(incident: Incident): List<String> = buildList {
        addAll(doneLines(incident))
        add(words.get("note_recheck", incident.recheckMinutes(key).toString()))
    }
}

/**
 * The vital signs as rows of a table, a name and one cell per set: the time, LOR, HR, RR,
 * SCTM, and the HR rhythm and RR quality when any set has them. The note and the Monitoring
 * screen both show it.
 */
fun vitalRows(words: Words, checks: List<Incident.Check>, time: (Long) -> String): List<Pair<String, List<String>>> = buildList {
    add("" to checks.map { time(it.at) })
    add(words.get("note_avpu") to checks.map { c -> c.avpu?.let { words.get("key_avpu_short_$it") } ?: "–" })
    add(words.get("note_pulse_row") to checks.map { it.pulse?.toString() ?: "–" })
    if (checks.any { it.rhythm != null }) add(words.get("note_rhythm_row") to checks.map { c -> c.rhythm?.let { words.get("key_rhythm_$it") } ?: "–" })
    add(words.get("note_breaths_row") to checks.map { it.breaths?.toString() ?: "–" })
    if (checks.any { it.quality != null }) add(words.get("note_quality_row") to checks.map { c -> c.quality?.let { words.get("key_quality_$it") } ?: "–" })
    add(words.get("note_skin_row") to checks.map { c -> c.skin.joinToString(",") { words.get("key_skin_$it") }.ifEmpty { "–" } })
}
