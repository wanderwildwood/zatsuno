package com.wanderwildwood.zatsuno.glance

import android.content.Context
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.card.CardStore
import com.wanderwildwood.zatsuno.card.Field

/**
 * The emergency card for Glance's lock-screen panel: only while the person has switched it on,
 * only the fields they left ticked, and only to Glance (see [GlanceProvider]).
 */
class CardOnLockScreen : GlanceProvider() {

    override fun enabled(context: Context): Boolean = CardStore.load(context).onLockScreen

    override fun lines(context: Context): List<Line> {
        val lines = CardStore.load(context).lockScreenLines()
        val heading = context.getString(R.string.card_title)
        return lines.mapIndexed { i, (field, text) ->
            Line(
                text = text,
                lead = context.getString(label(field)),
                bold = field == Field.ALLERGIES || field == Field.CONDITIONS,
                heading = if (i == 0) heading else null,
            )
        }
    }

    companion object {
        fun label(field: Field): Int = when (field) {
            Field.NAME -> R.string.card_name_short
            Field.BLOOD -> R.string.card_blood_short
            Field.ALLERGIES -> R.string.card_allergies_short
            Field.MEDICATIONS -> R.string.card_medications_short
            Field.CONDITIONS -> R.string.card_conditions_short
            Field.CONTACTS -> R.string.card_contact_short
            Field.NOTE -> R.string.card_note_short
        }
    }
}
