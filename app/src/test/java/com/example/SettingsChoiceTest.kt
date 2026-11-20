package com.example

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.mutableStateOf
import com.example.model.ReaderTheme
import com.example.ui.reader.ReaderChromeScaffold
import com.example.ui.theme.resolveReaderPalette
import com.example.ui.theme.ReaderColorPalette
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import com.example.model.LayoutMode
import com.example.model.ReaderPreferences
import com.example.ui.reader.TypographySettingsSheet
import com.example.ui.theme.EpubReaderTheme
import com.example.ui.theme.AppShapes
import com.example.ui.theme.AppTypography
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsChoiceTest {
    @get:Rule val ui = createComposeRule()

    @Test fun appUsesFourTextSizesAndThreeCornerRadii() {
        with(AppTypography) {
            val styles = listOf(displayLarge, displayMedium, displaySmall, headlineLarge, headlineMedium, headlineSmall,
                titleLarge, titleMedium, titleSmall, bodyLarge, bodyMedium, bodySmall, labelLarge, labelMedium, labelSmall)
            assertEquals(setOf(12.sp, 14.sp, 16.sp, 20.sp), styles.map { it.fontSize }.toSet())
        }
        with(AppShapes) {
            assertEquals(listOf(8, 8, 16, 28, 28).map { RoundedCornerShape(it.dp) },
                listOf(extraSmall, small, medium, large, extraLarge))
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight")
    fun lightOptionsUseTheCurrentColorScheme() = verifyChoices()

    @Test @Config(qualifiers = "w360dp-h400dp")
    fun compactSettingsScrollVerticallyAndLetSlidersHandleHorizontalDrags() {
        val shown = mutableStateOf(true)
        val prefs = mutableStateOf(ReaderPreferences.Default)
        ui.setContent {
            if (shown.value) TypographySettingsSheet(
                preferences = prefs.value, mode = LayoutMode.GLOBAL, onModeChange = {},
                onDismiss = { shown.value = false }, onThemeSelected = {}, onPageTurnModeSelected = {},
                onFontSizeChange = { prefs.value = prefs.value.copy(fontSizeSp = it) },
                onLineHeightChange = { prefs.value = prefs.value.copy(lineHeightMultiplier = it) },
                onHorizontalMarginChange = { prefs.value = prefs.value.copy(horizontalMarginDp = it) },
                onResetClick = {},
            )
        }
        val sheet = ui.onNodeWithTag("settings_sheet")
        ui.onNodeWithText("排版与阅读设置").checkSinglePageSidePull()
        // Bring each control into view, including the last one on a small screen.
        for (tag in listOf("font_size_slider", "line_height_slider", "margin_slider")) {
            val before = prefs.value
            val slider = ui.onNodeWithTag(tag).performScrollTo()
            val left = slider.getUnclippedBoundsInRoot().left
            slider.performTouchInput {
                down(center)
                moveTo(percentOffset(.9f, .5f), 160)
            }
            assertEquals(left, slider.getUnclippedBoundsInRoot().left)
            slider.performTouchInput { up() }
            assertNotEquals(before, prefs.value)
        }
        sheet.performTouchInput { swipeDown(durationMillis = 120) }
        sheet.assertIsDisplayed()
        ui.onNodeWithText("排版与阅读设置").performScrollTo()
        sheet.performTouchInput { swipeDown(durationMillis = 140) }
        sheet.assertDoesNotExist()
    }

    @Test @Config(qualifiers = "w411dp-h891dp-night")
    fun darkOptionsUseTheCurrentColorScheme() = verifyChoices()

    @Test @Config(qualifiers = "w411dp-h891dp-notnight")
    fun optionsUseTheBuiltInColorsWhenDynamicColorIsOff() = verifyChoices(dynamicColor = false)

    @Test @Config(qualifiers = "w320dp-h780dp-notnight")
    fun narrowWindowKeepsTheStandardChoiceHeight() = verifyChoices()

    @Test @Config(qualifiers = "w360dp-h800dp-notnight")
    fun choicesAndControlsFitLargeSystemType() {
        val previous = RuntimeEnvironment.getFontScale()
        try {
            RuntimeEnvironment.setFontScale(2f)
            verifyChoices(largeType = true)
        } finally {
            RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Test @Config(qualifiers = "w840dp-h960dp-notnight")
    fun wideWindowKeepsTheStandardChoiceHeight() = verifyChoices()

    @Test @Config(qualifiers = "w411dp-h891dp-notnight")
    fun readingBarsAreOpaqueAndUseTheReadingModesControlColors() {
        val theme = mutableStateOf(ReaderTheme.SYSTEM)
        val page = mutableStateOf(Color.Black)
        lateinit var controls: ColorScheme
        ui.setContent {
            EpubReaderTheme(darkTheme = theme.value == ReaderTheme.AMOLED_BLACK) {
                val scheme = MaterialTheme.colorScheme
                val palette = resolveReaderPalette(theme.value, isSystemInDarkTheme(), scheme)
                SideEffect { controls = scheme }
                TestReaderChrome(palette, page.value)
            }
        }
        val chapters = ui.onAllNodesWithText("章", useUnmergedTree = true).assertCountEquals(2)
        for (index in 0..1) {
            val layout = mutableListOf<TextLayoutResult>()
            chapters[index].performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layout) }
            assertEquals(12.sp, layout.single().layoutInput.style.fontSize)
        }
        fun expectPixel(tag: String, x: Float, y: Float, color: Color) {
            val pixels = ui.onNodeWithTag(tag).captureToImage().toPixelMap()
            val actual = pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()]
            assertEquals(tag, color.toArgb(), actual.toArgb())
        }
        for (mode in listOf(ReaderTheme.SYSTEM, ReaderTheme.AMOLED_BLACK)) {
            ui.runOnIdle { theme.value = mode }
            for (backdrop in listOf(Color.Black, Color.White)) {
                ui.runOnIdle { page.value = backdrop }
                ui.waitForIdle()
                expectPixel("reader_top_bar", 0.5f, 0.15f, controls.surface)
                expectPixel("reader_bottom_capsule", 0.5f, 0.05f, controls.surface)
            }
            for (tag in listOf("reader_capsule_toc_button", "reader_settings_button")) {
                expectPixel(tag, 0.5f, 0.25f, controls.secondaryContainer)
            }
            expectPixel("reader_progress_slider", 0.75f, 0.5f, controls.secondaryContainer)
            expectPixel("reader_progress_slider", 0.2f, 0.5f, controls.primary)
        }
    }

    @Composable
    private fun TestReaderChrome(palette: ReaderColorPalette, page: Color, visible: Boolean = true, notices: SnackbarHostState? = null) {
        ReaderChromeScaffold(
            bookTitle = "书", chapterTitle = "章",
            overallProgress = 0.4f, isBookmarked = false, controlsVisible = visible,
            canGoPreviousChapter = true, canGoNextChapter = true, palette = palette,
            snackbarHostState = notices, onNavigateBack = {}, onOpenToc = {},
            onOpenSearch = {}, onReturnFromSearch = null, onStayAtSearchResult = {}, onToggleBookmark = {}, onPreviousChapter = {},
            onNextChapter = {}, onSeekPreview = {}, onSeekFinished = {}, onOpenSettings = {},
            readerContent = { Box(it.background(page)) },
        )
    }

    @Test @Config(qualifiers = "w411dp-h891dp")
    fun noticeFollowsTheExitingToolbarUntilTheToolbarClearsTheScreen() {
        val visible = mutableStateOf(true)
        val notices = SnackbarHostState()
        ui.setContent {
            val palette = resolveReaderPalette(ReaderTheme.SYSTEM, false, MaterialTheme.colorScheme)
            TestReaderChrome(palette, Color.White, visible.value, notices)
            LaunchedEffect(Unit) { notices.showSnackbar("通知", duration = SnackbarDuration.Indefinite) }
        }
        val notice = ui.onNodeWithText("通知")
        val start = notice.getUnclippedBoundsInRoot().top
        val bottom = ui.onRoot().getUnclippedBoundsInRoot().bottom
        val capsule = ui.onNodeWithTag("reader_bottom_capsule")
        ui.mainClock.autoAdvance = false
        ui.runOnIdle { visible.value = false }
        var lastTop = capsule.getUnclippedBoundsInRoot().top
        var movedTogether = false
        repeat(22) {
            ui.mainClock.advanceTimeByFrame()
            if (ui.onAllNodesWithTag("reader_bottom_capsule").fetchSemanticsNodes().isNotEmpty()) {
                lastTop = capsule.getUnclippedBoundsInRoot().top
                val noticeTop = notice.getUnclippedBoundsInRoot().top
                if (noticeTop > start + 4.dp && lastTop < bottom - 4.dp) movedTogether = true
            }
        }
        assertTrue("notice should move with the visible toolbar", movedTogether)
        assertTrue("toolbar disappeared at $lastTop, screen ends at $bottom", lastTop >= bottom - 2.dp)
        assertTrue(notice.getUnclippedBoundsInRoot().top > start)
        ui.mainClock.autoAdvance = true
    }

    private fun verifyChoices(dynamicColor: Boolean = true, largeType: Boolean = false) {
        lateinit var colors: ColorScheme
        ui.setContent {
            EpubReaderTheme(dynamicColor = dynamicColor) {
                val scheme = MaterialTheme.colorScheme
                SideEffect { colors = scheme }
                TypographySettingsSheet(preferences = ReaderPreferences.Default, mode = LayoutMode.GLOBAL,
                    onModeChange = {}, onDismiss = {}, onThemeSelected = {}, onPageTurnModeSelected = {},
                    onFontSizeChange = {}, onLineHeightChange = {}, onHorizontalMarginChange = {}, onResetClick = {})
            }
        }
        for (row in listOf(listOf("layout_ORIGINAL", "layout_GLOBAL", "layout_CUSTOM"),
                           listOf("page_mode_paginated", "page_mode_scrolled"))) {
            for ((index, tag) in row.withIndex()) {
                val selected = tag == "layout_GLOBAL" || tag == "page_mode_paginated"
                val node = ui.onNodeWithTag(tag).performScrollTo()
                if (selected) node.assertIsSelected() else node.assertIsNotSelected()
                val bounds = node.getUnclippedBoundsInRoot()
                if (index > 0) {
                    val previous = ui.onNodeWithTag(row[index - 1]).getUnclippedBoundsInRoot()
                    assertTrue(bounds.left - previous.right >= 6.dp)
                }
                val pixels = node.captureToImage().toPixelMap()
                val fill = pixels[pixels.width / 2, 2]
                val expected = if (selected) colors.secondaryContainer else colors.surfaceContainer
                assertEquals(tag, expected.red, fill.red, 0.025f)
                assertEquals(tag, expected.green, fill.green, 0.025f)
                assertEquals(tag, expected.blue, fill.blue, 0.025f)
            }
        }
        val choice = ui.onNodeWithTag("layout_GLOBAL").getUnclippedBoundsInRoot()
        if (largeType) {
            assertTrue((choice.bottom - choice.top).value > 48f)
            val text = ui.onNodeWithText("默认排版", useUnmergedTree = true)
            val layouts = mutableListOf<TextLayoutResult>()
            text.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.single().multiParagraph.didExceedMaxLines)
        } else assertEquals(48f, (choice.bottom - choice.top).value, 1f)
        assertEquals(colors.primary, colors.secondary)
        assertEquals(colors.primaryContainer, colors.secondaryContainer)
        assertEquals(colors.onPrimaryContainer, colors.onSecondaryContainer)
        for (tag in listOf("font_size_decrease", "font_size_increase")) {
            val button = ui.onNodeWithTag(tag).performScrollTo()
            val bounds = button.getUnclippedBoundsInRoot()
            if (largeType) assertTrue((bounds.bottom - bounds.top).value >= 48f)
            else assertEquals(choice.bottom - choice.top, bounds.bottom - bounds.top)
            val pixels = button.captureToImage().toPixelMap()
            assertEquals("$tag at rest", colors.surface, pixels[pixels.width / 2, pixels.height / 4])
        }
    }
}
