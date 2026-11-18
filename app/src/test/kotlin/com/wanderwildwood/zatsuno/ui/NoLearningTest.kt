package com.wanderwildwood.zatsuno.ui

import android.view.inputmethod.EditorInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoLearningTest {
    @Test fun learningIsTurnedOffAndTheRestKept() {
        val options = noLearning(EditorInfo.IME_ACTION_DONE)
        assertEquals(EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING, options and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING)
        assertEquals(EditorInfo.IME_ACTION_DONE, options and EditorInfo.IME_MASK_ACTION)
    }

    @Test fun onlyTheAssessmentScreensAreKeptOutOfScreenshots() {
        for (r in listOf(Route.KEY, Route.NOTE, Route.READ_OUT, Route.LAST, Route.PATIENTS, Route.QR, Route.LAST_QR)) {
            assertTrue(r, Route.holdsPatient(r))
        }
        for (r in listOf(Route.HOME, Route.AID, Route.aid("call"), Route.aid("bleeding", more = true), Route.knot("bowline"),
            Route.COMPASS, Route.SEARCH, Route.CARD, Route.CARD_EDIT, Route.KNOTS, Route.DISASTERS)) {
            assertFalse(r, Route.holdsPatient(r))
        }
    }
}
