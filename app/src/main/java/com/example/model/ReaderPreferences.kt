package com.example.model

import androidx.compose.runtime.Immutable

enum class LayoutMode { ORIGINAL, GLOBAL, CUSTOM }

data class BookLayout(val mode: LayoutMode, val preferences: ReaderPreferences)

/**
 * Five curated reading visual themes with real-time background, ink, and system-bar updates.
 */
enum class ReaderTheme {
    SYSTEM,
    PURE_WHITE,
    WARM_PAPER,
    SOFT_GRAY,
    AMOLED_BLACK,
}

/**
 * Reading navigation layout mode, mapping directly to Readium's `scroll` preference flag.
 */
enum class PageTurnMode {
    PAGINATED,
    SCROLLED,
}

/**
 * Reader typography and visual preferences.
 * Designed for 1:1 mapping to Readium 3.x `EpubPreferences`.
 */
@Immutable
data class ReaderPreferences(
    val fontSizeSp: Float = 18f,
    val lineHeightMultiplier: Float = 1.75f,
    val horizontalMarginDp: Float = 22f,
    val theme: ReaderTheme = ReaderTheme.SYSTEM,
    val pageTurnMode: PageTurnMode = PageTurnMode.PAGINATED,
) {
    companion object {
        const val MIN_FONT_SIZE_SP = 14f
        const val MAX_FONT_SIZE_SP = 28f
        const val MIN_LINE_HEIGHT = 1.3f
        const val MAX_LINE_HEIGHT = 2.2f
        const val MIN_MARGIN_DP = 12f
        const val MAX_MARGIN_DP = 40f

        val Default = ReaderPreferences()
    }
}
