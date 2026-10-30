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
    fun theNetworkBeatsTheSim() {
        // A US phone roaming in Poland: the network says where it is.
        assertEquals(eu, Emergency.order(listOf("pl", "us")))
        // No network (flight mode): the SIM.
        assertEquals(us, Emergency.order(listOf("", "us")))
        assertEquals(eu, Emergency.order(listOf(null, "pl")))
    }

    @Test
    fun nothingKnownOffersBothWithOneOneTwoFirst() {
        assertEquals(eu, Emergency.order(listOf(null, "")))
        assertEquals(eu, Emergency.order(emptyList()))
    }
}
