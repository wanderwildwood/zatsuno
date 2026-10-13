package com.wanderwildwood.zatsuno.compass

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.sqrt

/**
 * The heading worked out from raw sensor values, with no Android in it, so it can be tested.
 *
 * Every source the compass reads ends in the same 3x3 matrix Android's own
 * `SensorManager.getRotationMatrix` builds: its rows are east, north and up, each written in
 * the phone's own axes (x to the right of the screen, y to its top, z out of its face).
 */
object Orientation {

    /**
     * The matrix from a rotation-vector reading (x, y, z and, where the sensor gives it, w),
     * as `SensorManager.getRotationMatrixFromVector` builds it.
     */
    fun fromRotationVector(v: FloatArray): FloatArray? {
        if (v.size < 3) return null
        val x = v[0]; val y = v[1]; val z = v[2]
        val w = if (v.size >= 4) v[3] else {
            val t = 1f - x * x - y * y - z * z
            if (t > 0f) sqrt(t) else 0f
        }
        return floatArrayOf(
            1f - 2f * y * y - 2f * z * z, 2f * x * y - 2f * z * w, 2f * x * z + 2f * y * w,
            2f * x * y + 2f * z * w, 1f - 2f * x * x - 2f * z * z, 2f * y * z - 2f * x * w,
            2f * x * z - 2f * y * w, 2f * y * z + 2f * x * w, 1f - 2f * x * x - 2f * y * y,
        )
    }

    /**
     * The matrix from gravity (the accelerometer) and the magnetic field, as
     * `SensorManager.getRotationMatrix` builds it; null when the phone is falling, or the field
     * is lined up with gravity, so there is no north to find.
     */
    fun fromGravityAndField(g: FloatArray, m: FloatArray): FloatArray? {
        val gNorm = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
        if (gNorm < 0.1f * 9.81f) return null
        // East is the field crossed with up.
        var ex = m[1] * g[2] - m[2] * g[1]
        var ey = m[2] * g[0] - m[0] * g[2]
        var ez = m[0] * g[1] - m[1] * g[0]
        val eNorm = sqrt(ex * ex + ey * ey + ez * ez)
        if (eNorm < 0.1f) return null
        ex /= eNorm; ey /= eNorm; ez /= eNorm
        val ux = g[0] / gNorm; val uy = g[1] / gNorm; val uz = g[2] / gNorm
        // North is up crossed with east.
        val nx = uy * ez - uz * ey
        val ny = uz * ex - ux * ez
        val nz = ux * ey - uy * ex
        return floatArrayOf(ex, ey, ez, nx, ny, nz, ux, uy, uz)
    }

    /**
     * Which way the phone points, in degrees from north, from the matrix above.
     *
     * Lying flat or tilted up to about 60° that is the top of the phone, as a compass in the
     * hand reads. Held up nearer to upright, the top points at the sky and its heading swings
     * wildly with the smallest movement, so the reading turns to where the back of the phone
     * faces, which is the way the person holding it is looking. [upright] is the choice made
     * last time, so the switch between the two does not flicker at the boundary.
     *
     * Returns the degrees and whether this reading took the upright view, or null when neither
     * gives a direction.
     */
    fun heading(r: FloatArray, upright: Boolean): Pair<Float, Boolean>? {
        // How far the screen faces up: 1 lying flat, 0 standing on its edge.
        val flatness = abs(r[8])
        val nowUpright = if (upright) flatness < UPRIGHT_LEAVE else flatness < UPRIGHT_ENTER
        // The phone's top is (0, 1, 0) in its own axes; its back is (0, 0, -1).
        val east = if (nowUpright) -r[2] else r[1]
        val north = if (nowUpright) -r[5] else r[4]
        if (abs(east) < 1e-4f && abs(north) < 1e-4f) return null
        val deg = Math.toDegrees(atan2(east, north).toDouble()).toFloat()
        return Bearing.normalise(deg) to nowUpright
    }

    /** Below this flatness (about 60° from flat) the reading turns to the back of the phone. */
    private const val UPRIGHT_ENTER = 0.5f

    /** And back to the top only once it is laid down past about 50°. */
    private const val UPRIGHT_LEAVE = 0.64f
}
