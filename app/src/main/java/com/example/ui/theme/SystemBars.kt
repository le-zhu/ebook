package com.example.ui.theme

import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleResumeEffect

@Composable
fun SystemBarsAppearanceEffect(
    isDarkBackground: Boolean,
    hideSystemBars: Boolean = false,
) {
    val view = LocalView.current
    val window = LocalActivity.current?.window ?: return
    // A predictive back preview composes both destinations. Only the resumed one owns this window.
    LifecycleResumeEffect(window, isDarkBackground, hideSystemBars) {
        val controller = WindowCompat.getInsetsController(window, view)
        controller.isAppearanceLightStatusBars = !isDarkBackground
        controller.isAppearanceLightNavigationBars = !isDarkBackground
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        val bars = WindowInsetsCompat.Type.systemBars()
        if (hideSystemBars) controller.hide(bars) else controller.show(bars)
        onPauseOrDispose {}
    }
}
