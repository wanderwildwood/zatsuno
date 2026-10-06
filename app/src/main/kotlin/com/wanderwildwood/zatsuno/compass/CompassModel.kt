package com.wanderwildwood.zatsuno.compass

import android.app.Application
import android.content.Context
import android.hardware.GeomagneticField
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One position fix, and the declination where it was taken. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val accuracy: Float?,
    /** [SystemClock.elapsedRealtimeNanos] when it was taken, for its age. */
    val elapsedNanos: Long,
    /** Wall-clock time it was taken, for the time written into a shared position. */
    val time: Long,
    /** Degrees east of true north the needle points here; west is negative. */
    val declination: Float,
)

/** Everything the compass and the position block show. */
data class Live(
    val permitted: Boolean = false,
    val locationOn: Boolean = true,
    val hasSensor: Boolean = true,
    val fix: Fix? = null,
    /** Degrees from magnetic north; null until the sensor has spoken. */
    val magnetic: Float? = null,
    val trueNorth: Boolean = false,
    val frozen: Boolean = false,
) {
    /** The heading to show, or null when there is none to show honestly. */
    val heading: Float?
        get() {
            val m = magnetic ?: return null
            if (!trueNorth) return m
            val f = fix ?: return null
            return Bearing.toTrue(m, f.declination)
        }
}

/**
 * The live heading and position, shared by the compass and the "Calling for help" page.
 *
 * Adapted from kCompass's CompassViewModel (Ondřej Koloničný, OK1CDJ, GPL-3.0). It runs only
 * while one of those screens is in front ([start] and [stop] follow the screen), and
 * redraws no more than once a second, and the heading only when it has moved more than three
 * degrees: every change is a panel refresh on e-ink. [freeze] stops both, leaving the last
 * reading still on the screen to be read or copied.
 */
class CompassModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("compass", Context.MODE_PRIVATE)

    private val _live = MutableStateFlow(
        Live(
            trueNorth = prefs.getBoolean(KEY_TRUE, false),
            hasSensor = Heading.available(app),
        ),
    )
    val live = _live.asStateFlow()

    private var job: Job? = null
    private var users = 0
    private var lastHeadingAt = 0L
    private var lastHeading = Float.NaN
    private var lastFixAt = 0L

    /** A screen that shows the heading or position has come to the front. */
    fun start() {
        users++
        refresh()
    }

    /** That screen has gone. */
    fun stop() {
        users = (users - 1).coerceAtLeast(0)
        if (users == 0) halt()
    }

    /** Look again at the permission and the location switch, and run if there is reason to. */
    fun refresh() {
        val ctx = getApplication<Application>()
        _live.update { it.copy(permitted = Fixes.permitted(ctx), locationOn = Fixes.switchedOn(ctx)) }
        if (users == 0 || _live.value.frozen) return
        if (job != null) {
            // Already running; but a permission granted since needs the position half started.
            if (_live.value.permitted && !fixesRunning) {
                halt(); run(ctx)
            }
            return
        }
        run(ctx)
    }

    private var fixesRunning = false

    private fun run(ctx: Context) {
        job = viewModelScope.launch {
            launch {
                Heading.updates(ctx).collect { deg ->
                    val now = SystemClock.elapsedRealtime()
                    val moved = lastHeading.isNaN() || Bearing.apart(deg, lastHeading) > HYSTERESIS
                    if (!moved || (lastHeadingAt != 0L && now - lastHeadingAt < INTERVAL)) return@collect
                    lastHeadingAt = now
                    lastHeading = deg
                    _live.update { it.copy(magnetic = deg) }
                }
            }
            if (Fixes.permitted(ctx)) {
                fixesRunning = true
                launch {
                    Fixes.updates(ctx).collect { loc ->
                        val now = SystemClock.elapsedRealtime()
                        val older = _live.value.fix?.let { loc.elapsedRealtimeNanos < it.elapsedNanos } ?: false
                        if (older || (lastFixAt != 0L && now - lastFixAt < INTERVAL)) return@collect
                        lastFixAt = now
                        val altitude = if (loc.hasAltitude()) loc.altitude.toFloat() else 0f
                        val declination = GeomagneticField(
                            loc.latitude.toFloat(), loc.longitude.toFloat(), altitude, System.currentTimeMillis(),
                        ).declination
                        _live.update {
                            it.copy(
                                fix = Fix(
                                    lat = loc.latitude,
                                    lon = loc.longitude,
                                    accuracy = if (loc.hasAccuracy()) loc.accuracy else null,
                                    elapsedNanos = loc.elapsedRealtimeNanos,
                                    time = loc.time,
                                    declination = declination,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    private fun halt() {
        job?.cancel()
        job = null
        fixesRunning = false
    }

    /** Hold what is on the screen, or let it run again. */
    fun freeze() {
        val frozen = !_live.value.frozen
        _live.update { it.copy(frozen = frozen) }
        if (frozen) halt() else refresh()
    }

    fun setTrueNorth(on: Boolean) {
        prefs.edit().putBoolean(KEY_TRUE, on).apply()
        _live.update { it.copy(trueNorth = on) }
    }

    override fun onCleared() {
        halt()
        super.onCleared()
    }

    private companion object {
        const val KEY_TRUE = "true_north"
        const val INTERVAL = 1000L
        const val HYSTERESIS = 3f
    }
}
