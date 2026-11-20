package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Synchronizes status bar and navigation bar icon brightness and optional immersive visibility.
 * Ensures status bar icons immediately invert between dark/light when switching reader themes
 * (e.g., Pure White vs. AMOLED Black), and cleanly restores system bars upon leaving the reader.
 */
@Composable
fun SystemBarsAppearanceEffect(
    isDarkBackground: Boolean,
    hideSystemBars: Boolean = false,
) {
    val view = LocalView.current
    DisposableEffect(view, isDarkBackground, hideSystemBars) {
        val window = view.context.findActivity()?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDarkBackground
            controller.isAppearanceLightNavigationBars = !isDarkBackground
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (hideSystemBars) {
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            val disposeWindow = view.context.findActivity()?.window
            if (disposeWindow != null && hideSystemBars) {
                val controller = WindowCompat.getInsetsController(disposeWindow, view)
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
}
