package com.wanderwildwood.zatsuno

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import com.mudita.mmd.ThemeMMD
import com.wanderwildwood.zatsuno.aid.Pages
import com.wanderwildwood.zatsuno.ui.FieldKitApp
import com.wanderwildwood.zatsuno.ui.monochrome

class MainActivity : ComponentActivity() {

    /** A page another app asked for, until the screen has gone to it. */
    private val request = mutableStateOf<Opening.Request?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Only a fresh start: remade after being stopped, the screen is already where it was.
        if (savedInstanceState == null) take(intent)
        setContent {
            ThemeMMD(colorScheme = monochrome) {
                FieldKitApp(request = request.value, onRequestHandled = { request.value = null })
            }
        }
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
        if (intent.getBooleanExtra(com.wanderwildwood.zatsuno.key.Recheck.EXTRA_WATCH, false)) {
            request.value = Opening.Request.Watch
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
