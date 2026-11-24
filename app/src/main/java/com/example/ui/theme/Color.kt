package com.example.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.example.model.ReaderTheme

/**
 * Resolved color tokens for the immersive reading canvas and its chrome overlays.
 * Directly reusable when bridging styling CSS / Preferences to Readium 3.x EpubPreferences.
 */
@Immutable
data class ReaderColorPalette(
    val background: Color,
    val contentText: Color,
    val isDark: Boolean,
)

fun resolveReaderPalette(
    theme: ReaderTheme,
    isSystemDark: Boolean,
): ReaderColorPalette = when (theme) {
    ReaderTheme.SYSTEM -> resolveReaderPalette(
        if (isSystemDark) ReaderTheme.AMOLED_BLACK else ReaderTheme.PURE_WHITE,
        isSystemDark,
    )
    ReaderTheme.PURE_WHITE -> ReaderColorPalette(
        background = Color(0xFFFFFFFF),
        contentText = Color(0xFF1A1C1E),
        isDark = false,
    )
    ReaderTheme.WARM_PAPER -> ReaderColorPalette(
        background = Color(0xFFF6EFE2),
        contentText = Color(0xFF2F261D),
        isDark = false,
    )
    ReaderTheme.SOFT_GRAY -> ReaderColorPalette(
        background = Color(0xFF222426),
        contentText = Color(0xFFD5D8DC),
        isDark = true,
    )
    ReaderTheme.AMOLED_BLACK -> ReaderColorPalette(
        background = Color(0xFF000000),
        contentText = Color(0xFFBDC1C6),
        isDark = true,
    )
}
