package com.wanderwildwood.zatsuno.help

import org.junit.Assert.assertEquals
import org.junit.Test

class EmergencyTest {
    private val us = listOf("911", "112")
    private val eu = listOf("112", "911")

    @Test
    fun northAmericaKeepsNineElevenFirst() {
        for (c in listOf("us", "US", "ca", "mx", "pr")) assertEquals(c, us, Emergency.order(listOf(c)))
    }

    @Test
    fun polandAndTheRestOfTheWorldGetOneOneTwoFirst() {
        for (c in listOf("pl", "de", "fr", "gb", "au", "jp")) assertEquals(c, eu, Emergency.order(listOf(c)))
    }

    @Test
    fun theNetworkBeatsTheSimBeatsTheLanguage() {
        // A US phone roaming in Poland: the network says where it is.
        assertEquals(eu, Emergency.order(listOf("pl", "us", "US")))
        // No network (flight mode): the SIM; then the language setting.
        assertEquals(us, Emergency.order(listOf("", "us", "PL")))
        assertEquals(eu, Emergency.order(listOf(null, "", "PL")))
    }

    @Test
    fun nothingKnownKeepsTheOldOrder() {
        assertEquals(us, Emergency.order(listOf(null, "", "")))
    }
}
