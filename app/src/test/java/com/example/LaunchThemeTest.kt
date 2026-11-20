package com.example

import android.app.Application
import android.content.ComponentName
import android.content.res.Configuration
import android.graphics.Color
import android.util.TypedValue
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LaunchThemeTest {
    @Test fun systemDayAndNightResolveBothLaunchBackgrounds() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val activityTheme = app.packageManager.getActivityInfo(ComponentName(app, MainActivity::class.java), 0).themeResource
        for ((mode, expected) in listOf(Configuration.UI_MODE_NIGHT_NO to Color.WHITE, Configuration.UI_MODE_NIGHT_YES to Color.BLACK)) {
            val config = Configuration(app.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or mode
            val resources = app.createConfigurationContext(config).resources
            val theme = resources.newTheme().apply { applyStyle(activityTheme, true) }
            for (attr in listOf(android.R.attr.windowBackground, android.R.attr.windowSplashScreenBackground)) {
                val value = TypedValue()
                assertTrue(theme.resolveAttribute(attr, value, true))
                assertEquals(expected, value.data)
            }
        }
    }
}
