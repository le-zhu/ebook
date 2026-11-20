package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val EditorialDisplayFont = FontFamily(
    Font(R.font.newsreader, FontWeight.Normal),
    Font(R.font.newsreader, FontWeight.Medium),
    Font(R.font.newsreader, FontWeight.SemiBold),
)

val UiSansFont = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold),
)

/**
 * Editorial serif font family used for the reading body canvas.
 * Pairs bundled Newsreader for Latin/numerals with Android's system CJK Serif fallback.
 */
val ReaderBodySerifFont = FontFamily.Serif

// All app chrome uses this four-step scale. Publication text is styled separately.
private fun uiText(size: Int, lineHeight: Int) = TextStyle(
    fontFamily = UiSansFont, fontSize = size.sp, lineHeight = lineHeight.sp,
    fontWeight = FontWeight.Normal, letterSpacing = 0.sp,
)

private val smallText = uiText(12, 16)
private val bodyText = uiText(14, 20)
private val largeText = uiText(16, 24)
private val heading = uiText(20, 28).copy(fontWeight = FontWeight.SemiBold)
private val smallLabel = smallText.copy(fontWeight = FontWeight.Medium)
private val actionLabel = bodyText.copy(fontWeight = FontWeight.Medium)
private val display = heading.copy(fontFamily = EditorialDisplayFont, fontWeight = FontWeight.Normal)

val AppTypography = Typography(
    displayLarge = display, displayMedium = display, displaySmall = display,
    headlineLarge = heading, headlineMedium = heading, headlineSmall = heading,
    titleLarge = heading, titleMedium = largeText.copy(fontWeight = FontWeight.Medium), titleSmall = actionLabel,
    bodyLarge = largeText, bodyMedium = bodyText, bodySmall = smallText,
    labelLarge = actionLabel, labelMedium = smallLabel, labelSmall = smallLabel,
)
