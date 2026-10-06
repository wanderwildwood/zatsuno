package com.wanderwildwood.zatsuno

import com.wanderwildwood.zatsuno.card.Card
import com.wanderwildwood.zatsuno.card.Contact
import com.wanderwildwood.zatsuno.card.Field
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardTest {
    private val card = Card(
        name = "Ada Whitlock",
        blood = "O+",
        allergies = "Penicillin",
        contacts = listOf(Contact("Tomas Reyes", "+1 555 010 1001")),
        note = "Spare key under the blue pot",
    )

    @Test
    fun nothingGoesOnTheLockScreenUntilSwitchedOn() {
        assertTrue(card.lockScreenLines().isEmpty())
    }

    @Test
    fun switchedOnItShowsOnlyTheTickedFields() {
        val on = card.copy(onLockScreen = true)
        assertEquals(listOf(Field.NAME, Field.BLOOD, Field.ALLERGIES, Field.CONTACTS), on.lockScreenLines().map { it.first })
        val fewer = on.copy(shown = setOf(Field.BLOOD))
        assertEquals(listOf(Field.BLOOD to "O+"), fewer.lockScreenLines())
    }

    @Test
    fun theNoteStartsOffTheLockScreen() {
        assertTrue(Field.NOTE !in Card.DEFAULT_SHOWN)
    }

    @Test
    fun emptyMeansEveryLineBlank() {
        assertTrue(Card().isEmpty)
        assertTrue(Card(name = "  ").isEmpty)
        assertTrue(!Card(contacts = listOf(Contact("Mum", ""))).isEmpty)
    }

    @Test
    fun contactsSurviveTheRoundTripEvenWithTabsAndNewlines() {
        val list = listOf(Contact("Tomas\tReyes", "+1 555 010 1001"), Contact("Nina\nOsei", ""))
        assertEquals(
            listOf(Contact("Tomas Reyes", "+1 555 010 1001"), Contact("Nina Osei", "")),
            Card.decodeContacts(Card.encodeContacts(list)),
        )
    }
}
