package com.wanderwildwood.zatsuno.aid

import java.io.File

/**
 * The ticks on a checklist page, kept on the phone: one file per page under the app's own
 * storage, never backed up or sent, a ticked point's text to a line. Keyed by the text rather
 * than the position, so a page edit that moves a point keeps its tick, and a point rewritten
 * starts unticked. [dir] is the folder, so the tests write a real one.
 */
class TickStore(private val dir: File) {

    private fun file(page: String) = File(dir, "$page.txt")

    fun load(page: String): Set<String> =
        runCatching { file(page).takeIf { it.isFile }?.readLines()?.filter { it.isNotBlank() }?.toSet() }.getOrNull().orEmpty()

    fun save(page: String, ticked: Set<String>) {
        if (ticked.isEmpty()) { clear(page); return }
        dir.mkdirs()
        val f = file(page)
        val tmp = File(dir, f.name + ".new")
        tmp.writeText(ticked.joinToString("\n") { it.replace('\n', ' ') } + "\n")
        tmp.renameTo(f)
    }

    fun clear(page: String) {
        file(page).delete()
    }

    /** [point] ticked if it was not, unticked if it was, saved either way. */
    fun toggle(page: String, point: String): Set<String> {
        val now = load(page).let { if (point in it) it - point else it + point }
        save(page, now)
        return now
    }
}
