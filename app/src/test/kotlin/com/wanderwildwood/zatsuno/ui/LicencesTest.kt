package com.wanderwildwood.zatsuno.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LicencesTest {
    @Test fun theListsAreNotEmptyAndNamedOnce() {
        assertTrue(LIBRARIES.isNotEmpty())
        assertEquals(LIBRARIES.size, LIBRARIES.map { it.name }.toSet().size)
        assertTrue(LICENCE_TEXTS.isNotEmpty())
        assertTrue(LIBRARIES.all { it.link.startsWith("http") })
    }

    @Test fun theSourceLinkIsTheRepo() {
        assertEquals("https://github.com/wanderwildwood/zatsuno", SOURCE_URL)
    }

    @Test fun paragraphsKeepEveryWord() {
        assertEquals(listOf("First line second line", "Third", "Fourth"), licenceParagraphs("First line\nsecond line\n\n  Third  \r\n\r\nFourth"))
    }
}
