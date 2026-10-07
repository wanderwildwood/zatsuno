package com.wanderwildwood.zatsuno

import com.wanderwildwood.zatsuno.Opening.ACTION_CALL_FOR_HELP
import com.wanderwildwood.zatsuno.Opening.ACTION_FIRST_AID
import com.wanderwildwood.zatsuno.Opening.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpeningTest {
    private val pages = listOf("call", "heat", "lightning", "hypothermia")
    private val none = Double.NaN

    @Test
    fun aPageThatExistsOpens() {
        assertEquals(Request.Aid("heat"), Opening.read(ACTION_FIRST_AID, "heat", none, none, null, pages))
        assertEquals(Request.Aid("lightning"), Opening.read(ACTION_FIRST_AID, " Lightning ", none, none, null, pages))
    }

    @Test
    fun aPageThatDoesNotOpensNothing() {
        assertNull(Opening.read(ACTION_FIRST_AID, "sunburn", none, none, null, pages))
        assertNull(Opening.read(ACTION_FIRST_AID, null, none, none, null, pages))
        assertNull(Opening.read("android.intent.action.MAIN", "heat", none, none, null, pages))
    }

    @Test
    fun callForHelpCarriesAPositionAndItsLabel() {
        assertEquals(
            Request.Call(Given(35.6, -82.8, "Marked on the map")),
            Opening.read(ACTION_CALL_FOR_HELP, null, 35.6, -82.8, "  Marked  on\nthe map ", pages),
        )
        assertEquals(Request.Call(Given(35.6, -82.8, null)), Opening.read(ACTION_CALL_FOR_HELP, null, 35.6, -82.8, " ", pages))
    }

    @Test
    fun aPositionOffTheGlobeOrHalfOneIsLeftOut() {
        assertEquals(Request.Call(null), Opening.read(ACTION_CALL_FOR_HELP, null, none, none, "x", pages))
        assertEquals(Request.Call(null), Opening.read(ACTION_CALL_FOR_HELP, null, 35.6, none, null, pages))
        assertEquals(Request.Call(null), Opening.read(ACTION_CALL_FOR_HELP, null, 91.0, 10.0, null, pages))
        assertEquals(Request.Call(null), Opening.read(ACTION_CALL_FOR_HELP, null, 10.0, -181.0, null, pages))
    }

    @Test
    fun aLongLabelIsCut() {
        val call = Opening.read(ACTION_CALL_FOR_HELP, null, 1.0, 2.0, "a".repeat(200), pages) as Request.Call
        assertEquals(Opening.LABEL_MAX, call.given!!.label!!.length)
    }

    @Test
    fun aGivenPositionSurvivesBeingSaved() {
        val given = Given(-33.85, 151.2, "Here, with a comma, in it")
        assertEquals(given, Given.restore(given.save()))
        assertEquals(Given(1.0, 2.0, null), Given.restore(Given(1.0, 2.0, null).save()))
        assertNull(Given.restore(null))
        assertNull(Given.restore("junk"))
    }
}
