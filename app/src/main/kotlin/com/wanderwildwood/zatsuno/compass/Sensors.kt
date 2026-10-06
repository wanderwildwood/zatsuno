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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * The heading, from the phone's fused rotation-vector sensor, in degrees from magnetic north.
 *
 * Taken from kCompass by Ondřej Koloničný, OK1CDJ (github.com/ok1cdj/kCompass, GPL-3.0). It is
 * plain Android with no Google services, which the Kompakt does not have. The reading is
 * smoothed on its sine and cosine rather than on the angle itself, so it does not swing the
 * long way round when it crosses north.
 */
object Heading {

    /** How much of each new reading is taken: lower is steadier, higher follows faster. */
    private const val ALPHA = 0.15f

    fun available(context: Context): Boolean {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager ?: return false
        return sm.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) != null
    }

    fun updates(context: Context): Flow<Float> = callbackFlow {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (sm == null || sensor == null) {
            close()
            return@callbackFlow
        }
        val rotation = FloatArray(9)
        val orientation = FloatArray(3)
        var s = Float.NaN
        var c = Float.NaN
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotation, event.values)
                SensorManager.getOrientation(rotation, orientation)
                val a = orientation[0]
                if (s.isNaN()) {
                    s = sin(a); c = cos(a)
                } else {
                    s += ALPHA * (sin(a) - s)
                    c += ALPHA * (cos(a) - c)
                }
                trySend(Bearing.normalise(Math.toDegrees(atan2(s, c).toDouble()).toFloat()))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
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
