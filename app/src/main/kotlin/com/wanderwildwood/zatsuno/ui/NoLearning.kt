package com.wanderwildwood.zatsuno.ui

import android.view.inputmethod.EditorInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.InterceptPlatformTextInput
import androidx.compose.ui.platform.PlatformTextInputMethodRequest

/**
 * The text fields inside [content] ask the keyboard not to learn what is typed in them
 * (IME_FLAG_NO_PERSONALIZED_LEARNING): a patient's name or condition stays out of the keyboard's
 * own dictionary and suggestions. Autocorrect still works as usual. Compose's KeyboardOptions has
 * no switch for it, so the request to the keyboard is changed on its way out, the way Compose's
 * own documentation shows.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun NoLearning(content: @Composable () -> Unit) {
    InterceptPlatformTextInput(
        interceptor = { request, next ->
            val quiet = PlatformTextInputMethodRequest { outAttributes ->
                request.createInputConnection(outAttributes).also {
                    outAttributes.imeOptions = noLearning(outAttributes.imeOptions)
                }
            }
            next.startInputMethod(quiet)
        },
        content = content,
    )
}

/** [imeOptions] with the keyboard's learning turned off, everything else kept. */
fun noLearning(imeOptions: Int): Int = imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
