package com.wanderwildwood.zatsuno.signal

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File

/** Five seconds a mile, three a kilometre, and the line that shows on every count. */
class LightningTest {

    @Test
    fun fiveSecondsAMileThreeAKilometre() {
        assertEquals(1.0, Lightning.miles(5.0), 1e-9)
        assertEquals(1.0, Lightning.km(3.0), 1e-9)
        assertEquals(6.0, Lightning.miles(30.0), 1e-9)
        assertEquals(10.0, Lightning.km(30.0), 1e-9)
    }

    @Test
    fun secondsAndDistancesReadToOneDecimalWithADot() {
        assertEquals("12.3", Lightning.seconds(12.34))
        assertEquals("2.5", Lightning.distance(Lightning.miles(12.34)))
        assertEquals("4.1", Lightning.distance(Lightning.km(12.34)))
        // A tap right on the thunder: not "0.0 mi", which would read as struck.
        assertEquals("0.1", Lightning.distance(0.0))
    }

    @Test
    fun elapsedIsNeverNegativeAndCutToTenths() {
        assertEquals(12.3, Lightning.elapsed(1000, 13_390), 1e-9)
        assertEquals(0.0, Lightning.elapsed(5000, 4000), 1e-9)
    }

    @Test
    fun theListKeepsTheLastSixNewestLast() {
        var counts = emptyList<Lightning.Count>()
        for (i in 1..8) counts = Lightning.record(counts, Lightning.Count(i * 60_000L, 40.0 - i * 4))
        assertEquals(Lightning.KEEP, counts.size)
        assertEquals(listOf(3L, 4, 5, 6, 7, 8).map { it * 60_000 }, counts.map { it.at })
        assertEquals(8.0, counts.last().seconds, 1e-9)
    }

    @Test
    fun theLineFollowsCurrentNwsAdvice() {
        // Thunder heard at all means in range (NWS: "If you hear thunder, lightning is close enough
        // to strike you"), and shelter holds 30 minutes after the last thunder. No 30-second cut-off.
        val strings = listOf("src/main/res/values/strings.xml", "app/src/main/res/values/strings.xml").map(::File).first { it.isFile }.readText()
        val line = Regex("""<string name="lightning_in_range">(.*?)</string>""").find(strings)!!.groupValues[1]
        assert("close enough to strike" in line) { line }
        assert("30 minutes" in line) { line }
        assert("30 seconds" !in line) { line }
    }
}
