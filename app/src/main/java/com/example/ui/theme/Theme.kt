package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF0169CC),
    onPrimary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFD3E4FF),
    onSecondaryContainer = Color(0xFF0C1C30),
    tertiary = Color(0xFF7B5733),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDCBE),
    onTertiaryContainer = Color(0xFF2C1600),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF9F8F4),
    onBackground = Color(0xFF191C1A),
    onSurface = Color(0xFF191C1A),
    surfaceVariant = Color(0xFFDEE5DF),
    onSurfaceVariant = Color(0xFF414944),
    outline = Color(0xFF717974),
    outlineVariant = Color(0xFFC1C9C3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF3F2EC),
    surfaceContainer = Color(0xFFF0F0F2),
    surfaceContainerHigh = Color(0xFFE7E6DF),
    surfaceContainerHighest = Color(0xFFE1E0D9),
)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF9AC7FF),
    onPrimary = Color(0xFF003063),
    secondaryContainer = Color(0xFF37475E),
    onSecondaryContainer = Color(0xFFD3E4FF),
    tertiary = Color(0xFFEDBE91),
    onTertiary = Color(0xFF462A0A),
    tertiaryContainer = Color(0xFF60401E),
    onTertiaryContainer = Color(0xFFFFDCBE),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF111418),
    onBackground = Color(0xFFE2E3E8),
    onSurface = Color(0xFFE2E3E8),
    surfaceVariant = Color(0xFF414750),
    onSurfaceVariant = Color(0xFFC1C7D0),
    outline = Color(0xFF8B919A),
    outlineVariant = Color(0xFF414750),
    surfaceContainerLowest = Color(0xFF0C0F13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282B30),
    surfaceContainerHighest = Color(0xFF33363B),
)

private val smallCorner = RoundedCornerShape(8.dp)
private val panelCorner = RoundedCornerShape(28.dp)
val AppShapes = Shapes(
    extraSmall = smallCorner,
    small = smallCorner,
    medium = RoundedCornerShape(16.dp),
    large = panelCorner,
    extraLarge = panelCorner,
)

internal val FloatingPanelElevation = 6.dp
internal val ControlHeight = 48.dp
internal val TallControlHeight = 64.dp
internal const val ListSheetHeightFraction = 0.8f

@Composable
fun EpubReaderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = appColorScheme(darkTheme, dynamicColor),
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

@Composable
internal fun appColorScheme(darkTheme: Boolean, dynamicColor: Boolean = true): ColorScheme {
    val context = LocalContext.current
    return remember(context, LocalConfiguration.current, darkTheme, dynamicColor) {
        val scheme = when {
            dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            darkTheme -> DarkColorScheme
            else -> LightColorScheme
        }
        // Raised panels sit on the neutral surfaceContainer used for the shelf and inactive choices.
        val accent = if (darkTheme) scheme.secondaryContainer else lerp(scheme.secondaryContainer, scheme.primary, 0.16f)
        val lab = accent.convert(ColorSpaces.Oklab)
        // Both container roles resolve to the same wallpaper-adaptive fill.
        val fill = lab.copy(green = lab.green * 1.6f, blue = lab.blue * 1.6f).convert(accent.colorSpace)
        scheme.copy(
            surface = if (darkTheme) scheme.surfaceContainerHigh else Color.White,
            secondary = scheme.primary,
            onSecondary = scheme.onPrimary,
            primaryContainer = fill,
            secondaryContainer = fill,
            onPrimaryContainer = scheme.onSecondaryContainer,
        )
    }
}

