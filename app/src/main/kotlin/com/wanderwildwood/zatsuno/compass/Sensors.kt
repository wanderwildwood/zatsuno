package com.wanderwildwood.zatsuno.compass

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** What the compass sensors have to say: a heading, how far to trust it, or nothing at all. */
sealed interface HeadingSignal {
    /** Degrees from magnetic north. */
    data class Degrees(val value: Float) : HeadingSignal

    /** The magnetometer's own word on itself: false when it needs a figure-eight to calibrate. */
    data class Calibrated(val ok: Boolean) : HeadingSignal

    /** Every source has been tried and none has given a reading. */
    data object Silent : HeadingSignal
}

/**
 * The heading, in degrees from magnetic north, from whichever of the phone's sensors gives one.
 *
 * Started from kCompass by Ondřej Koloničný, OK1CDJ (github.com/ok1cdj/kCompass, GPL-3.0),
 * which reads only the fused rotation-vector sensor. That sensor is built by Android from a
 * gyroscope, and a phone with no gyroscope has none, even though its magnetometer works; it
 * is also the one most often left broken by a phone maker's sensor software. So the sources
 * are tried in turn: the rotation vector, the geomagnetic rotation vector (the same with no
 * gyroscope), the accelerometer and magnetometer read directly, and last the old orientation
 * sensor. One that says nothing within [QUIET_MS] is given up for the next.
 *
 * The reading is smoothed on its sine and cosine rather than on the angle itself, so it does
 * not swing the long way round when it crosses north.
 */
object Heading {

    /** How much of each new reading is taken: lower is steadier, higher follows faster. */
    private const val ALPHA = 0.15f

    /** How long a source may stay quiet before the next is tried. */
    private const val QUIET_MS = 2500L

    private enum class Source { ROTATION, GEOMAGNETIC, ACCEL_MAG, ORIENTATION }

    @Suppress("DEPRECATION")
    private fun sources(sm: SensorManager): List<Source> = buildList {
        if (sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null) add(Source.ROTATION)
        if (sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR) != null) add(Source.GEOMAGNETIC)
        if (sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null &&
            sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD) != null
        ) add(Source.ACCEL_MAG)
        if (sm.getDefaultSensor(Sensor.TYPE_ORIENTATION) != null) add(Source.ORIENTATION)
    }

    fun available(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sources(sm).isNotEmpty()
    }

    @Suppress("DEPRECATION")
    fun updates(context: Context): Flow<HeadingSignal> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val order = sm?.let { sources(it) }.orEmpty()
        if (sm == null || order.isEmpty()) {
            close()
            return@callbackFlow
        }
        val gravity = FloatArray(3)
        val field = FloatArray(3)
        var haveGravity = false
        var haveField = false
        var upright = false
        var s = Float.NaN
        var c = Float.NaN
        var heard = false

        fun take(deg: Float) {
            heard = true
            val a = Math.toRadians(deg.toDouble()).toFloat()
            if (s.isNaN()) {
                s = sin(a); c = cos(a)
            } else {
                s += ALPHA * (sin(a) - s)
                c += ALPHA * (cos(a) - c)
            }
            trySend(HeadingSignal.Degrees(Bearing.normalise(Math.toDegrees(atan2(s, c).toDouble()).toFloat())))
        }

        fun fromMatrix(r: FloatArray?) {
            val (deg, up) = r?.let { Orientation.heading(it, upright) } ?: return
            upright = up
            take(deg)
        }

        // The magnetometer is listened to for its accuracy whichever source gives the heading:
        // it is the part that needs calibrating, and the fused sensors do not reliably pass
        // its word on. Its values feed the heading only when it is the source.
        val magnetometer = sm.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        var lastAccuracy = Int.MIN_VALUE
        var source: Source? = null

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                when (event.sensor.type) {
                    Sensor.TYPE_ROTATION_VECTOR, Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR ->
                        fromMatrix(Orientation.fromRotationVector(event.values))
                    Sensor.TYPE_ACCELEROMETER -> {
                        event.values.copyInto(gravity, endIndex = 3)
                        haveGravity = true
                    }
                    Sensor.TYPE_MAGNETIC_FIELD -> {
                        if (event.accuracy != lastAccuracy) report(event.accuracy)
                        if (source == Source.ACCEL_MAG) {
                            event.values.copyInto(field, endIndex = 3)
                            haveField = true
                            if (haveGravity) fromMatrix(Orientation.fromGravityAndField(gravity, field))
                        }
                    }
                    Sensor.TYPE_ORIENTATION -> take(event.values[0])
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                if (sensor?.type == Sensor.TYPE_MAGNETIC_FIELD) report(accuracy)
            }

            fun report(accuracy: Int) {
                lastAccuracy = accuracy
                // UNRELIABLE (0) and LOW (1) both mean a figure-eight is wanted; NO_CONTACT (-1)
                // is not a magnetometer's to say.
                if (accuracy >= SensorManager.SENSOR_STATUS_UNRELIABLE) {
                    trySend(HeadingSignal.Calibrated(accuracy >= SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM))
                }
            }
        }

        fun register(src: Source) {
            source = src
            when (src) {
                Source.ROTATION -> sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
                Source.GEOMAGNETIC -> sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR), SensorManager.SENSOR_DELAY_UI)
                Source.ACCEL_MAG -> sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_UI)
                Source.ORIENTATION -> sm.registerListener(listener, sm.getDefaultSensor(Sensor.TYPE_ORIENTATION), SensorManager.SENSOR_DELAY_UI)
            }
        }

        fun unregisterSource() {
            // Everything but the magnetometer, which stays for its accuracy.
            sm.unregisterListener(listener)
            if (magnetometer != null) sm.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
            haveGravity = false
            haveField = false
        }

        if (magnetometer != null) sm.registerListener(listener, magnetometer, SensorManager.SENSOR_DELAY_UI)
        launch {
            for (src in order) {
                heard = false
                register(src)
                delay(QUIET_MS)
                if (heard) return@launch
                unregisterSource()
            }
            // Nothing spoke. Say so, and keep the first listening in case it is only slow.
            trySend(HeadingSignal.Silent)
            register(order.first())
        }
        awaitClose { sm.unregisterListener(listener) }
    }
}

/**
 * Position fixes from the platform's own location service, about one a second.
 *
 * Taken from kCompass, as [Heading] is. The Kompakt has no Google Play services, so this asks
 * [LocationManager] directly: satellites, and the network provider as well where the phone has
 * one, for a quicker first fix. The last fix the phone already holds is sent first so the screen
 * is not empty while the satellites are found; its age is shown beside it, so an old one is not
 * mistaken for a new one.
 */
object Fixes {

    fun permitted(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether the phone's location setting is on at all. */
    fun switchedOn(context: Context): Boolean {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return lm.isLocationEnabled
    }

    fun updates(context: Context): Flow<Location> = callbackFlow {
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (!permitted(context) || lm == null) {
            close()
            return@callbackFlow
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                trySend(location)
            }

            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}

            @Deprecated("Still called on some versions")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        }
        try {
            val last = listOfNotNull(
                lm.getLastKnownLocation(LocationManager.GPS_PROVIDER),
                lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER),
            ).maxByOrNull { it.elapsedRealtimeNanos }
            if (last != null) trySend(last)
            val looper = Looper.getMainLooper()
            for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (provider in lm.allProviders && lm.isProviderEnabled(provider)) {
                    lm.requestLocationUpdates(provider, 1000L, 0f, listener, looper)
                }
            }
        } catch (_: SecurityException) {
            close()
            return@callbackFlow
        }
        awaitClose { lm.removeUpdates(listener) }
    }
}
