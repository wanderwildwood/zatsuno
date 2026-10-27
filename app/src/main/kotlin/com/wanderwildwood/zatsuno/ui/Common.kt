package com.wanderwildwood.zatsuno.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** An icon in a top bar, with a target a thumb can find. */
@Composable
fun BarButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(48.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * When the keyboard goes away the field that was typing is let go, so its cursor stops being
 * drawn (a focused field keeps the screen redrawing) and the next tap starts fresh. Read from
 * the window's own insets, since Compose's do not report the keyboard under adjustResize.
 */
@androidx.compose.runtime.Composable
fun ClearFocusWhenKeyboardHides() {
    val view = androidx.compose.ui.platform.LocalView.current
    val focus = androidx.compose.ui.platform.LocalFocusManager.current
    androidx.compose.runtime.DisposableEffect(view) {
        var was = false
        val listener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
            val shown = androidx.core.view.ViewCompat.getRootWindowInsets(view)
                ?.isVisible(androidx.core.view.WindowInsetsCompat.Type.ime()) == true
            if (was && !shown) focus.clearFocus()
            was = shown
        }
        view.viewTreeObserver.addOnGlobalLayoutListener(listener)
        onDispose { view.viewTreeObserver.removeOnGlobalLayoutListener(listener) }
    }
}
