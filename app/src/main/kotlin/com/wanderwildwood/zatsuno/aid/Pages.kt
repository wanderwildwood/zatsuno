package com.wanderwildwood.zatsuno.aid

import android.content.Context
import com.wanderwildwood.zatsuno.R

/**
 * The first-aid pages, in the order the list shows them: what to do first, then what
 * threatens life soonest (no breath, no pulse, blood loss), then what can turn bad within the
 * hour, then the rest.
 */
object Pages {

    /** The page that carries the live position, and is reached from the home screen too. */
    const val CALL = "call"

    private val order = listOf(
        "scene" to R.raw.aid_scene,
        CALL to R.raw.aid_call,
        "cpr" to R.raw.aid_cpr,
        "choking" to R.raw.aid_choking,
        "bleeding" to R.raw.aid_bleeding,
        "drowning" to R.raw.aid_drowning,
        "anaphylaxis" to R.raw.aid_anaphylaxis,
        "breathing" to R.raw.aid_breathing,
        "chest" to R.raw.aid_chest,
        "stroke" to R.raw.aid_stroke,
        "shock" to R.raw.aid_shock,
        "seizure" to R.raw.aid_seizure,
        "sugar" to R.raw.aid_sugar,
        "poisoning" to R.raw.aid_poisoning,
        "head" to R.raw.aid_head,
        "heat" to R.raw.aid_heat,
        "hypothermia" to R.raw.aid_hypothermia,
        "altitude" to R.raw.aid_altitude,
        "lightning" to R.raw.aid_lightning,
        "burns" to R.raw.aid_burns,
        "snakebite" to R.raw.aid_snakebite,
        "allergy" to R.raw.aid_allergy,
        "fracture" to R.raw.aid_fracture,
        "frostbite" to R.raw.aid_frostbite,
        "dehydration" to R.raw.aid_dehydration,
        "sprain" to R.raw.aid_sprain,
        "wounds" to R.raw.aid_wounds,
        "ticks" to R.raw.aid_ticks,
        "blisters" to R.raw.aid_blisters,
    )

    /** Every page's id, without reading the pages: what another app may ask to open. */
    val ids: List<String> get() = order.map { it.first }

    @Volatile private var cache: Pair<String, List<Page>>? = null

    /** Every page, read once per language. */
    fun all(context: Context): List<Page> {
        val locale = context.resources.configuration.locales[0].toLanguageTag()
        cache?.let { (l, pages) -> if (l == locale) return pages }
        val pages = order.map { (id, res) ->
            val text = context.resources.openRawResource(res).bufferedReader().use { it.readText() }
            Markup.parse(id, text)
        }
        cache = locale to pages
        return pages
    }

    fun find(context: Context, id: String): Page? = all(context).firstOrNull { it.id == id }
}
