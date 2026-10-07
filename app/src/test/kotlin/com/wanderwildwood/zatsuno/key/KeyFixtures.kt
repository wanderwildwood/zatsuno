package com.wanderwildwood.zatsuno.key

import java.io.File

/** The real key and the real strings, read from the source tree, so the tests check what ships. */
object KeyFixtures {
    private fun file(path: String) = listOf(File(path), File("app/$path")).first { it.exists() }

    val key: Key by lazy { Key.parse(file("src/main/assets/key/key.txt").readText()) }

    val strings: Map<String, String> by lazy {
        Regex("""<string name="(\w+)">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(file("src/main/res/values/strings.xml").readText())
            .associate { it.groupValues[1] to it.groupValues[2].replace("\\'", "'") }
    }

    val words = Words { name, args ->
        var s = strings[name] ?: error("no string $name")
        args.forEachIndexed { i, a -> s = s.replace("%${i + 1}\$s", a) }
        s
    }

    /** Page id to title, from the pages themselves. */
    val titles: Map<String, String> by lazy {
        file("src/main/res/raw").listFiles { f -> f.name.startsWith("aid_") }!!.associate {
            it.nameWithoutExtension.removePrefix("aid_") to it.readLines().first().removePrefix("title:").trim()
        }
    }

    /** The page ids in the order Pages.kt lists them. */
    val pageIds: List<String> by lazy {
        Regex("""R\.raw\.aid_(\w+)""").findAll(file("src/main/kotlin/com/wanderwildwood/zatsuno/aid/Pages.kt").readText())
            .map { it.groupValues[1] }.toList()
    }

    val dangerIds: Set<String> by lazy { key.pages.filter { it.danger }.map { it.id }.toSet() }

    /** Answers from "q=a" strings; "q=a,b" for a pick-all. */
    fun answers(vararg given: String): Answers = given.fold(Answers()) { acc, g ->
        val q = g.substringBefore('='); val a = g.substringAfter('=')
        if (key.question(q)?.many == true) acc.withMany(q, a.split(',').toSet()) else acc.with(q, a)
    }
}
