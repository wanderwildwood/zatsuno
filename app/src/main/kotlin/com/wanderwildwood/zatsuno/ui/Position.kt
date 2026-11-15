package com.wanderwildwood.zatsuno.ui

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.text.format.DateFormat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mudita.mmd.components.buttons.OutlinedButtonMMD
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.Given
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.compass.CompassModel
import com.wanderwildwood.zatsuno.compass.Fix
import com.wanderwildwood.zatsuno.compass.Live
import com.wanderwildwood.zatsuno.compass.Place
import com.wanderwildwood.zatsuno.coord.CoordinateFormatter
import com.wanderwildwood.zatsuno.coord.Maidenhead
import com.wanderwildwood.zatsuno.coord.Mgrs
import kotlinx.coroutines.delay
import java.util.Date

/** Keeps the heading and position running while this screen is in front, and only then. */
@Composable
fun RunWhileShown(model: CompassModel) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        var running = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> if (!running) { running = true; model.start() }
                Lifecycle.Event.ON_PAUSE -> if (running) { running = false; model.stop() }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            if (running) model.stop()
        }
    }
}

/**
 * What stands in for the position when there is none to show: why, and the one press that
 * would change it. Location refused once can be asked again; refused for good, only the
 * app's settings page can change it, so that is where the button goes.
 */
@Composable
fun LocationPrompt(live: Live, onChanged: () -> Unit) {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    // When nothing on the phone opens the settings page, said under the button until the next press.
    var noApp by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        asked = true
        onChanged()
    }
    Column(Modifier.fillMaxWidth()) {
        when {
            !live.permitted -> {
                TextMMD(text = stringResource(R.string.pos_need_permission), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                val activity = context.findActivity()
                val blocked = asked && activity != null &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION)
                if (blocked) {
                    WideButton(stringResource(R.string.pos_settings)) {
                        noApp = !open(
                            context,
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }
                } else {
                    WideButton(stringResource(R.string.pos_allow)) {
                        noApp = false
                        launcher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                    }
                }
            }
            !live.locationOn -> {
                TextMMD(text = stringResource(R.string.pos_off), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                WideButton(stringResource(R.string.pos_turn_on)) {
                    noApp = !open(context, Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            else -> TextMMD(text = stringResource(R.string.pos_waiting), style = MaterialTheme.typography.bodyMedium)
        }
        if (noApp && !(live.permitted && live.locationOn)) {
            Spacer(Modifier.height(6.dp))
            TextMMD(text = stringResource(R.string.no_app), style = MaterialTheme.typography.labelSmall)
        }
    }
}

/**
 * The position four ways (decimal, DMS, the US National Grid / MGRS reference, and the
 * Maidenhead locator), and how far it can be trusted: within how many metres, and how old it
 * is. Each line copies itself when pressed. An old fix is shown as old rather than hidden,
 * since an old position is still worth reading out.
 */
@Composable
fun PositionLines(fix: Fix, big: Boolean, quality: Boolean = true) {
    val context = LocalContext.current
    // "Copied" stays under the lines while this screen shows, not on a timer.
    var copied by remember { mutableStateOf(false) }
    fun copy(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(text, text))
        copied = true
    }
    val decimal = CoordinateFormatter.decimal(fix.lat, fix.lon)
    val dms = CoordinateFormatter.dms(fix.lat, fix.lon)
    val locator = Maidenhead.encode(fix.lat, fix.lon)
    val grid = Mgrs.format(fix.lat, fix.lon)
    Column(Modifier.fillMaxWidth()) {
        TextMMD(
            text = decimal,
            style = if (big) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().clickable { copy(decimal) }.heightIn(min = 48.dp).padding(vertical = 2.dp),
        )
        TextMMD(
            text = dms,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable { copy(dms) }.heightIn(min = 48.dp).padding(vertical = 2.dp),
        )
        if (grid != null) TextMMD(
            text = "${stringResource(R.string.label_grid)} $grid",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth().clickable { copy(grid) }.heightIn(min = 48.dp).padding(vertical = 2.dp),
        )
        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            // A position sent by another app comes with no accuracy or time to show.
            TextMMD(text = if (quality) accuracyText(fix) else "", style = MaterialTheme.typography.labelSmall)
            TextMMD(
                text = "${stringResource(R.string.label_locator)} $locator",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.heightIn(min = 48.dp).clickable { copy(locator) },
            )
        }
        if (copied) TextMMD(text = stringResource(R.string.copied), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
    }
}

/** "±8 m · 12 s old", refreshed every ten seconds: often enough to notice, rarely enough for e-ink. */
@Composable
private fun accuracyText(fix: Fix): String {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtimeNanos()) }
    LaunchedEffect(fix) {
        while (true) {
            now = SystemClock.elapsedRealtimeNanos()
            delay(10_000)
        }
    }
    val seconds = ((now - fix.elapsedNanos) / 1_000_000_000L).coerceAtLeast(0)
    val age = when {
        seconds < 15 -> stringResource(R.string.age_now)
        seconds < 120 -> stringResource(R.string.age_seconds, (seconds / 10 * 10).toString())
        seconds < 7200 -> stringResource(R.string.age_minutes, (seconds / 60).toString())
        else -> stringResource(R.string.age_hours, (seconds / 3600).toString())
    }
    val acc = fix.accuracy
    return if (acc != null) stringResource(R.string.pos_accuracy, Place.metres(acc), age)
    else stringResource(R.string.pos_accuracy_unknown, age)
}

/** Sends the position as text: to Messaging for a call for help, to Notes, anywhere. */
fun sharePosition(context: Context, fix: Fix) {
    val time = DateFormat.getTimeFormat(context).format(Date(fix.time))
    val acc = fix.accuracy
    val heading = if (acc != null) context.getString(R.string.share_heading, time, Place.metres(acc))
        else context.getString(R.string.share_heading_no_accuracy, time)
    val text = Place.shareText(heading, fix.lat, fix.lon)
    shareText(context, context.getString(R.string.pos_title), text)
}

/** Sends a position another app handed over, under the name it came with. */
fun shareGiven(context: Context, given: Given) {
    val heading = context.getString(R.string.share_heading_given, given.label ?: context.getString(R.string.pos_given))
    shareText(context, context.getString(R.string.pos_title), Place.shareText(heading, given.lat, given.lon))
}

/** Hands the position to whatever map app is on the phone (Topo answers `geo:`). */
fun openInMap(context: Context, fix: Fix): Boolean {
    val uri = Uri.parse(Place.geoUri(fix.lat, fix.lon, context.getString(R.string.map_label)))
    return open(context, Intent(Intent.ACTION_VIEW, uri))
}

/** Opens the dialler with the number filled in. Calling is still the person's own press. */
fun dial(context: Context, number: String): Boolean =
    open(context, Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))

fun shareText(context: Context, subject: String, text: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, subject)
        .putExtra(Intent.EXTRA_TEXT, text)
    context.startActivity(Intent.createChooser(send, null))
}

/**
 * Starts something; false when nothing on the phone can do it, for the caller to say so on the
 * screen until the next press (a toast is gone before the panel has shown it).
 */
fun open(context: Context, intent: Intent): Boolean = try {
    context.startActivity(intent)
    true
} catch (_: ActivityNotFoundException) {
    false
}

@Composable
fun WideButton(label: String, modifier: Modifier = Modifier.fillMaxWidth(), onClick: () -> Unit) {
    OutlinedButtonMMD(onClick = onClick, modifier = modifier.height(48.dp)) {
        TextMMD(text = label, style = MaterialTheme.typography.bodySmall, maxLines = 1)
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
