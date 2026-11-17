package com.wanderwildwood.zatsuno

import com.wanderwildwood.zatsuno.ui.DarkSelection
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalCursorBlinkEnabled
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.zatsuno.aid.Pages
import com.wanderwildwood.zatsuno.ui.ClearFocusWhenKeyboardHides
import com.wanderwildwood.zatsuno.ui.FieldKitApp
import com.wanderwildwood.zatsuno.ui.houseTypography
import com.wanderwildwood.zatsuno.ui.monochrome
import com.wanderwildwood.zatsuno.ui.ArmedScope

class MainActivity : ComponentActivity() {

    /** A page another app asked for, until the screen has gone to it. */
    private val request = mutableStateOf<Opening.Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.wanderwildwood.zatsuno.key.Recheck.channel(this)
        // Only a fresh start: remade after being stopped, the screen is already where it was.
        if (savedInstanceState == null) take(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) { DarkSelection {
                ArmedScope {
                    // A steady cursor: a blinking one redraws the screen twice a second for as long as
                    // a field has focus, which on e-ink is a refresh each time.
                    CompositionLocalProvider(LocalCursorBlinkEnabled provides false) {
                        ClearFocusWhenKeyboardHides()
                        FieldKitApp(request = request.value, onRequestHandled = { request.value = null })
                    }
                }
            } }
        }
    }

    override fun onStart() {
        super.onStart()
        // The app is open in front of the person: the reminder has done its job.
        com.wanderwildwood.zatsuno.key.Recheck.seen(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        take(intent)
    }

    private fun take(intent: Intent?) {
        intent ?: return
        // Reopened from the recent apps, the old request would come round again.
        if (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY != 0) return
        val end = intent.getBooleanExtra(com.wanderwildwood.zatsuno.key.Recheck.EXTRA_END, false)
        val monitor = intent.getBooleanExtra(com.wanderwildwood.zatsuno.key.Recheck.EXTRA_MONITOR, false)
        if (end || monitor) {
            // The reminder's own requests name one of the open patients. This screen is open to
            // other apps too, so a name that is not one of them is no request at all.
            val patient = intent.getStringExtra(com.wanderwildwood.zatsuno.key.Recheck.EXTRA_PATIENT)
            val open = com.wanderwildwood.zatsuno.key.IncidentStore.patients(this)
            if (open.none { it.id == patient }) return
            request.value = if (end) Opening.Request.End(patient) else Opening.Request.Monitor(patient)
            return
        }
        Opening.read(
            action = intent.action,
            page = intent.getStringExtra(Opening.EXTRA_PAGE),
            lat = intent.getDoubleExtra(Opening.EXTRA_LATITUDE, Double.NaN),
            lon = intent.getDoubleExtra(Opening.EXTRA_LONGITUDE, Double.NaN),
            label = intent.getStringExtra(Opening.EXTRA_LABEL),
            pages = Pages.ids,
        )?.let { request.value = it }
    }
}
