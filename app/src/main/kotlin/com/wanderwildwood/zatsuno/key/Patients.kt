package com.wanderwildwood.zatsuno.key

/** Telling the open patients apart, and cutting a long note into codes small enough to scan. */
object Patients {
    /** A patient's name as written, else "Patient 2" by their place among [all] (oldest first). */
    fun label(incident: Incident, all: List<Incident>, words: Words): String {
        val name = incident.texts()[Fields.NAME]?.text?.trim().orEmpty()
        if (name.isNotEmpty()) return name
        val n = all.sortedBy { it.started }.indexOfFirst { it.id == incident.id }.let { if (it < 0) all.size else it } + 1
        return words.get("patient_n", n.toString())
    }

    /** The same, for the reminder, which has only a context: the real strings.xml. */
    fun label(incident: Incident, all: List<Incident>, context: android.content.Context): String =
        label(incident, all, Keys.words(context))

    /** Most characters in one QR code: a phone camera reads this much from a small panel at arm's length. */
    const val QR_CHARS = 600

    /**
     * [text] as the pieces to put in QR codes, cut between lines. One piece is the text as it
     * is; several each begin with "(1/3)" so they can be put back in order. A single line longer
     * than [max] is cut where it is.
     */
    fun codes(text: String, max: Int = QR_CHARS): List<String> {
        if (text.length <= max) return listOf(text)
        // Leave room for the "(12/12)\n" at the head of each piece.
        val room = max - 8
        val pieces = mutableListOf<String>()
        val cur = StringBuilder()
        fun flush() { if (cur.isNotEmpty()) { pieces += cur.toString().trimEnd('\n'); cur.clear() } }
        for (line in text.lines()) {
            var l = line
            while (l.length > room) { flush(); pieces += l.take(room); l = l.drop(room) }
            if (cur.length + l.length + 1 > room) flush()
            cur.append(l).append('\n')
        }
        flush()
        return pieces.mapIndexed { i, p -> "(${i + 1}/${pieces.size})\n$p" }
    }
}
