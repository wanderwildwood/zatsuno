package com.wanderwildwood.zatsuno.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.mudita.mmd.components.text.TextMMD
import com.wanderwildwood.zatsuno.R
import com.wanderwildwood.zatsuno.signal.Sos
import kotlinx.coroutines.delay

/** The camera flash as a torch, through [CameraManager.setTorchMode], which needs no permission. */
object Torch {
    /** The flash to use, the back one first, or null on a phone without one. */
    fun find(context: Context): String? = runCatching {
        val cm = context.getSystemService(CameraManager::class.java)
        val withFlash = cm.cameraIdList.filter { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
        withFlash.firstOrNull { id ->
            cm.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
        } ?: withFlash.firstOrNull()
    }.getOrNull()

    /** True when the flash did as asked; false when the camera has it or it failed. */
    fun set(context: Context, id: String, on: Boolean): Boolean =
        runCatching { context.getSystemService(CameraManager::class.java).setTorchMode(id, on) }.isSuccess
}

/**
 * "Flash SOS" on "Calling for help": the flash blinks · · · – – – · · · over and over until
 * the button is pressed again. The screen stays awake while it runs, and it stops by itself
 * when the page is left, the screen goes off or the app goes behind another. On a phone
 * without a flash the button is not there. Under it, when the "Signalling for rescue" page
 * exists, a line that opens it.
 */
@Composable
fun SosBlock(titles: Map<String, String>, onOpenPage: (String) -> Unit) {
    val context = LocalContext.current
    val torch = remember { Torch.find(context) }
    val signals = remember(titles) { titles.entries.firstOrNull { it.value == SIGNALS_PAGE }?.key }
    if (torch == null && signals == null) return
    var running by remember { mutableStateOf(false) }
    val failed = stringResource(R.string.sos_failed)

    if (torch != null) {
        val view = LocalView.current
        DisposableEffect(running) {
            view.keepScreenOn = running
            onDispose { view.keepScreenOn = false }
        }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE) running = false }
            owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer) }
        }
        LaunchedEffect(running) {
            if (!running) return@LaunchedEffect
            val steps = Sos.sequence()
            try {
                while (true) {
                    for (step in steps) {
                        if (!Torch.set(context, torch, step.on)) {
                            Toast.makeText(context, failed, Toast.LENGTH_LONG).show()
                            running = false
                            return@LaunchedEffect
                        }
                        delay(step.millis)
                    }
                }
            } finally {
                Torch.set(context, torch, false)
            }
        }
    }

    Column(Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 4.dp)) {
        if (torch != null) {
            WideButton(stringResource(if (running) R.string.sos_stop else R.string.sos_start)) { running = !running }
            if (running) {
                Spacer(Modifier.height(6.dp))
                TextMMD(text = stringResource(R.string.sos_running), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
        }
        if (signals != null) {
            Spacer(Modifier.height(8.dp))
            val see = stringResource(R.string.see_page, signals)
            TextMMD(
                text = buildAnnotatedString {
                    append(see.substringBefore(signals))
                    withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(signals) }
                    append(see.substringAfter(signals))
                },
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.clickable { onOpenPage(SIGNALS_PAGE) }.padding(vertical = 4.dp),
            )
        }
    }
}

/** The id of the page on signalling for rescue, linked from the SOS button once it exists. */
const val SIGNALS_PAGE = "signals"
