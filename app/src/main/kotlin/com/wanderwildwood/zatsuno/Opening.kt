package com.wanderwildwood.zatsuno

/**
 * How other apps open Field Kit at a page: a first-aid page by its id, or "Calling for help"
 * with a position they already have (a point on a map, say). Sky opens the heat or lightning
 * page beside its warnings; Topo hands over the point it was asked about. The README lists
 * the same names for anyone else.
 *
 * Both are plain intents to MainActivity, with no permission asked of the caller: neither
 * changes anything, they only choose what the screen shows. Whatever arrives is checked here,
 * and anything that does not fit is dropped, not guessed at.
 */
object Opening {
    const val ACTION_FIRST_AID = "com.wanderwildwood.zatsuno.action.FIRST_AID"
    const val ACTION_CALL_FOR_HELP = "com.wanderwildwood.zatsuno.action.CALL_FOR_HELP"

    /** The page's id: `heat`, `lightning`, `hypothermia` and so on, as [aid.Pages] names them. */
    const val EXTRA_PAGE = "com.wanderwildwood.zatsuno.extra.PAGE"

    /** Degrees, as doubles; both or neither. */
    const val EXTRA_LATITUDE = "com.wanderwildwood.zatsuno.extra.LATITUDE"
    const val EXTRA_LONGITUDE = "com.wanderwildwood.zatsuno.extra.LONGITUDE"

    /** A few words saying what the position is, shown over it and sent with it. Optional. */
    const val EXTRA_LABEL = "com.wanderwildwood.zatsuno.extra.LABEL"

    /** A label longer than this is cut: it is a heading, not a message. */
    const val LABEL_MAX = 80

    /** What the screen should open to. */
    sealed interface Request {
        data class Aid(val page: String) : Request
        data class Call(val given: Given?) : Request
    }

    /**
     * Reads one opening. [pages] are the ids that exist; a page that is not one of them opens
     * nothing. A position outside the globe, or half a position, is left out and the call page
     * opens with the phone's own.
     */
    fun read(
        action: String?,
        page: String?,
        lat: Double,
        lon: Double,
        label: String?,
        pages: Collection<String>,
    ): Request? = when (action) {
        ACTION_FIRST_AID -> page?.trim()?.lowercase()?.takeIf { it in pages }?.let { Request.Aid(it) }
        ACTION_CALL_FOR_HELP -> Request.Call(given(lat, lon, label))
        else -> null
    }

    private fun given(lat: Double, lon: Double, label: String?): Given? {
        if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        val words = label
            ?.replace(Regex("\\s+"), " ")
            ?.trim()
            ?.take(LABEL_MAX)
            ?.takeIf { it.isNotEmpty() }
        return Given(lat, lon, words)
    }
}

/** A position another app handed over, with what it called it. */
data class Given(val lat: Double, val lon: Double, val label: String?) {
    /** As one string, so it survives the activity being stopped and remade. */
    fun save(): String = "$lat,$lon,${label.orEmpty()}"

    companion object {
        fun restore(saved: String?): Given? {
            val parts = saved?.split(',', limit = 3) ?: return null
            if (parts.size < 3) return null
            val lat = parts[0].toDoubleOrNull() ?: return null
            val lon = parts[1].toDoubleOrNull() ?: return null
            return Given(lat, lon, parts[2].ifEmpty { null })
        }
    }
}
