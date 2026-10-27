package com.wanderwildwood.zatsuno.help

import android.content.Context
import android.telephony.TelephonyManager
import java.util.Locale

/**
 * Which emergency number comes first. 911 answers across North America (and is the number
 * people there know); almost everywhere else 112 does, and in the EU it is the one that works
 * on any SIM. Both are always offered; only the order follows the phone's country, which is
 * read from the network, then the SIM, then the language setting. No permission needed.
 */
object Emergency {
    /** Countries where 911 is the emergency number: the US and its territories, Canada, Mexico. */
    private val NINE_ONE_ONE = setOf("US", "CA", "MX", "PR", "GU", "VI", "AS", "MP")

    /** [countries] are ISO codes in order of trust (network, SIM, locale); the first one known decides. */
    fun order(countries: List<String?>): List<String> {
        val country = countries.firstOrNull { !it.isNullOrBlank() }?.uppercase(Locale.ROOT)
        return if (country == null || country in NINE_ONE_ONE) listOf("911", "112") else listOf("112", "911")
    }

    fun order(context: Context): List<String> {
        val tm = context.getSystemService(TelephonyManager::class.java)
        return order(
            listOf(
                runCatching { tm?.networkCountryIso }.getOrNull(),
                runCatching { tm?.simCountryIso }.getOrNull(),
                Locale.getDefault().country,
            ),
        )
    }
}
