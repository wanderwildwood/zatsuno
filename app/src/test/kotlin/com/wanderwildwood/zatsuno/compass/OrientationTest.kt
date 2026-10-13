package com.wanderwildwood.zatsuno.compass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin

class OrientationTest {

    /** A field of 40 µT pointing north and dipping 60° into the ground, as in mid-latitudes. */
    private fun field(northX: Float, northY: Float): FloatArray {
        val h = 20f; val down = 34.6f
        return floatArrayOf(h * northX, h * northY, -down)
    }

    /** Flat, face up, its top turned [deg] east of north: north lies at -deg in its own axes. */
    private fun flatTurned(deg: Double): Float {
        val a = Math.toRadians(deg)
        val g = floatArrayOf(0f, 0f, 9.81f)
        val m = field(-sin(a).toFloat(), cos(a).toFloat())
        val r = Orientation.fromGravityAndField(g, m)!!
        return Orientation.heading(r, upright = false)!!.first
    }

    @Test
    fun flatPhoneReadsItsTop() {
        assertEquals(0f, flatTurned(0.0), 0.5f)
        assertEquals(90f, flatTurned(90.0), 0.5f)
        assertEquals(225f, flatTurned(225.0), 0.5f)
        assertEquals(359f, flatTurned(359.0), 0.5f)
    }

    @Test
    fun uprightPhoneReadsItsBack() {
        // Standing up, screen toward the reader, the back facing east: gravity is along -y
        // (the accelerometer reads +y), north is to the phone's left (-x).
        val g = floatArrayOf(0f, 9.81f, 0f)
        val m = floatArrayOf(-20f, -34.6f, 0f)
        val r = Orientation.fromGravityAndField(g, m)!!
        val (deg, upright) = Orientation.heading(r, upright = false)!!
        assertTrue(upright)
        assertEquals(90f, deg, 0.5f)
    }

    @Test
    fun uprightHoldsUntilWellTiltedBack() {
        // About 55° from flat: flatness 0.57, between the two thresholds.
        val tilt = Math.toRadians(55.0)
        val g = floatArrayOf(0f, (9.81 * sin(tilt)).toFloat(), (9.81 * cos(tilt)).toFloat())
        val m = floatArrayOf(0f, 20f, -34.6f)
        val r = Orientation.fromGravityAndField(g, m)!!
        assertTrue(Orientation.heading(r, upright = true)!!.second)
        assertFalse(Orientation.heading(r, upright = false)!!.second)
    }

    @Test
    fun noNorthWhenFallingOrFieldAlongGravity() {
        assertNull(Orientation.fromGravityAndField(floatArrayOf(0f, 0f, 0.2f), floatArrayOf(0f, 20f, -30f)))
        assertNull(Orientation.fromGravityAndField(floatArrayOf(0f, 0f, 9.81f), floatArrayOf(0f, 0f, -40f)))
    }

    @Test
    fun rotationVectorAboutUpIsTheHeading() {
        // A turn of 90° east about the vertical: the rotation vector is the half-angle quaternion
        // about z, by -90° (east of north is clockwise seen from above).
        val half = Math.toRadians(-45.0)
        val v = floatArrayOf(0f, 0f, sin(half).toFloat(), cos(half).toFloat())
        val r = Orientation.fromRotationVector(v)!!
        assertEquals(90f, Orientation.heading(r, upright = false)!!.first, 0.5f)
        // The same with w left for the reader to work out, as older sensors send it.
        val r3 = Orientation.fromRotationVector(floatArrayOf(0f, 0f, sin(half).toFloat()))!!
        assertEquals(90f, Orientation.heading(r3, upright = false)!!.first, 0.5f)
    }
}
