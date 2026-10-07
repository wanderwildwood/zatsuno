package com.wanderwildwood.zatsuno.card

import android.content.Context

/** Someone to call, by name and number. */
data class Contact(val name: String, val number: String)

/** The parts of the card, each of which can be shown on the lock screen or kept off it. */
enum class Field { NAME, BLOOD, ALLERGIES, MEDICATIONS, CONDITIONS, CONTACTS, NOTE }

/**
 * The "In case of emergency" card: a few lines the person fills in for whoever finds them
 * hurt. Every line is optional. It is kept on this phone only, in the app's own storage.
 *
 * It goes on the lock screen (through Glance) only when [onLockScreen] is switched on, which
 * it is not until the person does it, and then only the fields they leave ticked in [shown].
 */
data class Card(
    val name: String = "",
    val blood: String = "",
    val allergies: String = "",
    val medications: String = "",
    val conditions: String = "",
    val contacts: List<Contact> = emptyList(),
    val note: String = "",
    val onLockScreen: Boolean = false,
    val shown: Set<Field> = DEFAULT_SHOWN,
) {
    val isEmpty: Boolean
        get() = listOf(name, blood, allergies, medications, conditions, note).all { it.isBlank() } && contacts.isEmpty()

    /** The filled lines, in order, as (field, text); several contacts are several lines. */
    fun lines(): List<Pair<Field, String>> = buildList {
        if (name.isNotBlank()) add(Field.NAME to name.trim())
        if (blood.isNotBlank()) add(Field.BLOOD to blood.trim())
        if (allergies.isNotBlank()) add(Field.ALLERGIES to allergies.trim())
        if (medications.isNotBlank()) add(Field.MEDICATIONS to medications.trim())
        if (conditions.isNotBlank()) add(Field.CONDITIONS to conditions.trim())
        contacts.forEach { add(Field.CONTACTS to listOf(it.name.trim(), it.number.trim()).filter { s -> s.isNotEmpty() }.joinToString(" ")) }
        if (note.isNotBlank()) add(Field.NOTE to note.trim())
    }

    /** Whether someone with this number is already one of the contacts; digits alone are compared. */
    fun hasNumber(number: String): Boolean {
        val digits = number.filter(Char::isDigit).takeLast(10)
        return digits.isNotEmpty() && contacts.any { it.number.filter(Char::isDigit).takeLast(10) == digits }
    }

    /** What may go on the lock screen: nothing unless switched on, then only the ticked fields. */
    fun lockScreenLines(): List<Pair<Field, String>> =
        if (!onLockScreen) emptyList() else lines().filter { it.first in shown }

    companion object {
        /** A note can hold anything, so it starts off the lock screen; the rest start on. */
        val DEFAULT_SHOWN: Set<Field> = Field.entries.toSet() - Field.NOTE

        /**
         * A contact another app handed over, cleaned as one picked by hand would be; null
         * when there is no number to call.
         */
        fun given(name: String?, number: String?): Contact? {
            val n = number?.replace(Regex("\\s+"), " ")?.trim()?.take(40).orEmpty()
            if (n.none { it.isDigit() }) return null
            return Contact(name?.replace(Regex("\\s+"), " ")?.trim()?.take(100).orEmpty(), n)
        }

        /** Contacts as text, one to a line, name and number split by a tab. */
        fun encodeContacts(contacts: List<Contact>): String =
            contacts.joinToString("\n") { "${clean(it.name)}\t${clean(it.number)}" }

        fun decodeContacts(text: String): List<Contact> =
            text.split('\n').filter { it.isNotBlank() }.map {
                Contact(it.substringBefore('\t'), it.substringAfter('\t', ""))
            }

        private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').trim()
    }
}

/** Where the card is kept: the app's private preferences, never backed up, never sent. */
object CardStore {
    private const val PREFS = "card"

    fun load(context: Context): Card {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Card(
            name = p.getString("name", "").orEmpty(),
            blood = p.getString("blood", "").orEmpty(),
            allergies = p.getString("allergies", "").orEmpty(),
            medications = p.getString("medications", "").orEmpty(),
            conditions = p.getString("conditions", "").orEmpty(),
            contacts = Card.decodeContacts(p.getString("contacts", "").orEmpty()),
            note = p.getString("note", "").orEmpty(),
            onLockScreen = p.getBoolean("lock", false),
            shown = p.getStringSet("shown", null)
                ?.mapNotNull { n -> Field.entries.firstOrNull { it.name == n } }?.toSet()
                ?: Card.DEFAULT_SHOWN,
        )
    }

    fun save(context: Context, card: Card) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("name", card.name)
            .putString("blood", card.blood)
            .putString("allergies", card.allergies)
            .putString("medications", card.medications)
            .putString("conditions", card.conditions)
            .putString("contacts", Card.encodeContacts(card.contacts))
            .putString("note", card.note)
            .putBoolean("lock", card.onLockScreen)
            .putStringSet("shown", card.shown.map { it.name }.toSet())
            .commit()
        com.wanderwildwood.zatsuno.glance.GlanceProvider.changed(context)
    }
}
