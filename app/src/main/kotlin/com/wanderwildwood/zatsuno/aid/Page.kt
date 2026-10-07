package com.wanderwildwood.zatsuno.aid

/**
 * A first-aid page: a few lines about itself, then its body in a markup small enough to write
 * by hand and to translate. The pages live in `res/raw/aid_*.txt`, so a translation is a copy
 * of the folder (`raw-de/`) and Android picks the reader's language by itself.
 *
 * ```
 * title: Severe bleeding
 * keywords: blood, tourniquet
 * source: DHS, "Applying a Tourniquet" · MedlinePlus, "Bleeding"
 * ---
 * ! Call 911 or your local emergency number.     a line that must not be missed
 * # Press on it                                   a heading
 * 1. Press hard with a cloth or your hand.        a step
 * - a point                                       a point under a step or heading
 * > Older advice kept the tourniquet for last.    a note set apart: what changed, and who says so
 * @position                                       the live position block ("Calling for help")
 * +++                                             where More starts
 * Anything else is a paragraph.
 * ```
 *
 * A page opens on what to do now: the lines that must not be missed and the first steps. The
 * rest, from the `+++` line on (why, when to get care, what not to do, what changed, the
 * sources), waits under a More row the reader taps open. A page without the line shows whole.
 */
data class Page(
    val id: String,
    val title: String,
    val keywords: List<String>,
    val source: String,
    val blocks: List<Block>,
    /** Where More starts in [blocks]: the size of [blocks] when everything shows at once. */
    val more: Int = blocks.size,
) {
    /** What the page opens on. */
    val now: List<Block> get() = blocks.subList(0, more)

    /** What waits under More. */
    val rest: List<Block> get() = blocks.subList(more, blocks.size)

    /** The page as plain text, for sending to Notes or a message. */
    fun asText(sourceLabel: String, disclaimer: String): String = buildString {
        appendLine(title)
        appendLine()
        for (b in blocks) {
            when (b) {
                is Block.Heading -> { appendLine(); appendLine(b.text) }
                is Block.Step -> appendLine("${b.number}. ${b.text}")
                is Block.Point -> appendLine("• ${b.text}")
                is Block.Urgent -> appendLine(b.text)
                is Block.Note -> appendLine(b.text)
                is Block.Para -> appendLine(b.text)
                Block.Position -> {}
            }
        }
        appendLine()
        appendLine("$sourceLabel $source")
        append(disclaimer)
    }

    /** Every word on the page a search can find it by. */
    fun searchText(): String = text(blocks)

    /** The title and what the page opens on, as words: a search that needs more opens More. */
    fun nowText(): String = title + "\n" + text(now)

    /** The words under More. */
    fun restText(): String = text(rest)

    private fun text(of: List<Block>): String = of.joinToString("\n") {
        when (it) {
            is Block.Heading -> it.text
            is Block.Step -> it.text
            is Block.Point -> it.text
            is Block.Urgent -> it.text
            is Block.Note -> it.text
            is Block.Para -> it.text
            Block.Position -> ""
        }
    }
}

sealed interface Block {
    data class Heading(val text: String) : Block
    data class Step(val number: Int, val text: String) : Block
    data class Point(val text: String) : Block
    data class Urgent(val text: String) : Block
    data class Note(val text: String) : Block
    data class Para(val text: String) : Block
    data object Position : Block
}

object Markup {

    private const val MORE = "+++"

    private val step = Regex("""^(\d+)\.\s+(.*)$""")

    /**
     * Reads one page. A line that continues the one above it (indented by two spaces) joins
     * it, so long steps can be wrapped in the source file.
     */
    fun parse(id: String, source: String): Page {
        val lines = source.replace("\r\n", "\n").lines()
        val split = lines.indexOfFirst { it.trim() == "---" }
        require(split >= 0) { "$id: no --- after the header" }
        val header = lines.take(split)
            .filter { ':' in it }
            .associate { it.substringBefore(':').trim() to it.substringAfter(':').trim() }

        val joined = mutableListOf<String>()
        for (raw in lines.drop(split + 1)) {
            if (raw.startsWith("  ") && raw.isNotBlank() && joined.isNotEmpty() && joined.last().isNotBlank() && joined.last() != MORE) {
                joined[joined.lastIndex] = joined.last() + " " + raw.trim()
            } else {
                joined += raw.trimEnd()
            }
        }

        val body = joined.filter { it.isNotBlank() }
        val cut = body.indexOf(MORE)
        require(cut < 0 || body.lastIndexOf(MORE) == cut) { "$id: more than one $MORE" }
        val blocks = body.filter { it != MORE }.map { line ->
            val m = step.matchEntire(line)
            when {
                m != null -> Block.Step(m.groupValues[1].toInt(), m.groupValues[2].trim())
                line == "@position" -> Block.Position
                line.startsWith("# ") -> Block.Heading(line.drop(2).trim())
                line.startsWith("- ") -> Block.Point(line.drop(2).trim())
                line.startsWith("! ") -> Block.Urgent(line.drop(2).trim())
                line.startsWith("> ") -> Block.Note(line.drop(2).trim())
                else -> Block.Para(line.trim())
            }
        }
        return Page(
            id = id,
            title = requireNotNull(header["title"]) { "$id: no title" },
            keywords = header["keywords"].orEmpty().split(',').map { it.trim() }.filter { it.isNotEmpty() },
            source = requireNotNull(header["source"]) { "$id: no source" },
            blocks = blocks,
            more = if (cut < 0) blocks.size else cut,
        )
    }
}
