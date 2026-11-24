package com.example

import android.net.Uri
import androidx.activity.BackEventCompat
import androidx.core.view.WindowCompat
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import android.content.res.Configuration
import androidx.viewpager.widget.ViewPager
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.core.view.descendants
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import com.example.model.LayoutMode
import com.example.model.ReaderTheme
import com.example.ui.reader.ReaderViewModel
import com.example.ui.reader.ReaderPanel
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.services.locateProgression
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs
import kotlin.math.hypot

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class ReaderActionsTest {
    @get:Rule val ui = createAndroidComposeRule<MainActivity>()

    private fun find(tag: String): SemanticsNodeInteraction {
        try {
            ui.waitUntil(10000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
        } catch (error: ComposeTimeoutException) {
            throw AssertionError("Missing $tag: " + ui.onAllNodes(isRoot()).printToString(), error)
        }
        return ui.onNodeWithTag(tag)
    }

    private fun awaitReader(reader: ReaderViewModel) {
        // These secondary models have no composable collecting their state; advance the Android looper explicitly.
        val main = shadowOf(ui.activity.mainLooper)
        ui.waitUntil(10000) {
            main.idleFor(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            val state = reader.uiState.value
            check(state.error == null) { state.error.orEmpty() }
            state.book != null
        }
    }

    private fun View.findPager(): ViewPager? {
        if (this is ViewPager) return this
        if (this is ViewGroup) for (i in 0 until childCount) getChildAt(i).findPager()?.let { return it }
        return null
    }

    private fun importTestBook(title: String, description: String? = null, extraText: String = ""): String {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/example.epub")
        val bytes = epub(title, description = description, extraText = extraText)
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        return runBlocking { app.library.import(uri).first.id }
    }

    @Test fun shelfDistinguishesUnreadBooksFromZeroPercentRead() {
        val unread = importTestBook("尚未打开")
        val started = importTestBook("刚开始读")
        val lib = (ui.activity.application as ReaderApplication).library
        runBlocking {
            val pub = lib.open(started)
            try {
                lib.savePosition(started, 0, "上章", 0.005f, requireNotNull(pub.locateProgression(0.005)))
            } finally { pub.close() }
        }
        for (view in listOf("grid", "list")) {
            if (view == "list") find("bookshelf_toggle_view_button").performClick()
            for ((id, label) in listOf(unread to "未读", started to "0%")) {
                val tag = "book_${view}_card_$id"
                find(tag)
                ui.waitUntil(5000) {
                    ui.onAllNodes(hasText(label) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                        .fetchSemanticsNodes().isNotEmpty()
                }
            }
        }
        find("book_list_card_$started").performTouchInput { longClick() }
        find("book_detail_read_button").assert(hasText("继续阅读"))
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun longSynopsisScrollsIndependently() = verifySynopsisScrolling()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w640dp-h320dp-land")
    fun shortWindowKeepsSynopsisAndActionsAccessible() = verifySynopsisScrolling()

    private fun verifySynopsisScrolling() {
        val id = importTestBook("书籍详情", (1..60).joinToString("") { "<p>简介第${it}段。</p>" })
        find("book_grid_card_$id").performTouchInput { longClick() }
        val viewport = find("book_detail_synopsis_viewport")
        val heading = ui.onNodeWithText("内容简介")
        val actions = find("book_detail_read_button").assert(hasText("开始阅读"))
        val remove = find("book_detail_remove_button")
        val headingBounds = heading.getUnclippedBoundsInRoot()
        val actionBounds = actions.getUnclippedBoundsInRoot()
        val removeBounds = remove.getUnclippedBoundsInRoot()
        val range = viewport.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue(range.maxValue() > 0f)
        viewport.performTouchInput { swipeUp() }
        assertTrue(range.value() > 0f)
        viewport.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.ScrollBy) { it(0f, range.maxValue() - range.value()) }
        ui.waitForIdle()
        assertEquals(range.maxValue(), range.value(), 1f)
        val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        val paragraph = ui.onNodeWithText("简介第60段。", substring = true, useUnmergedTree = true)
        paragraph.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val text = layouts.single()
        assertEquals(text.layoutInput.text.length, text.getLineEnd(text.lineCount - 1))
        assertFalse(text.multiParagraph.didExceedMaxLines)
        val textTop = paragraph.getUnclippedBoundsInRoot().top
        viewport.performTouchInput { down(center); moveBy(androidx.compose.ui.geometry.Offset(0f, -60.dp.toPx()), 120) }
        assertTrue(paragraph.getUnclippedBoundsInRoot().top < textTop)
        assertEquals(headingBounds, heading.getUnclippedBoundsInRoot())
        assertEquals(actionBounds, actions.getUnclippedBoundsInRoot())
        assertEquals(removeBounds, remove.getUnclippedBoundsInRoot())
        viewport.performTouchInput { up() }
        assertEquals(textTop, paragraph.getUnclippedBoundsInRoot().top)
        viewport.performTouchInput { swipeDown() }
        assertTrue(range.value() < range.maxValue())
        assertEquals(headingBounds, heading.getUnclippedBoundsInRoot())
        assertEquals(actionBounds, actions.getUnclippedBoundsInRoot())
        assertEquals(removeBounds, remove.getUnclippedBoundsInRoot())
        for (tag in listOf("book_detail_read_button", "book_detail_remove_button")) {
            val button = find(tag)
            val bounds = button.fetchSemanticsNode().boundsInRoot
            assertTrue("$tag: $bounds", bounds.height >= with(ui.density) { 47.dp.toPx() })
        }
        find("book_detail_read_button").performTouchInput { click() }
        find("reader_top_bar").assertIsDisplayed()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w320dp-h780dp")
    fun largeTextMovesWholeDetailButtonsOntoSeparateRows() {
        val previous = org.robolectric.RuntimeEnvironment.getFontScale()
        try {
            org.robolectric.RuntimeEnvironment.setFontScale(2f)
            ui.activityRule.scenario.recreate()
            val id = importTestBook("大字书籍详情")
            find("book_grid_card_$id").performTouchInput { longClick() }
            val read = find("book_detail_read_button").assertIsDisplayed().getUnclippedBoundsInRoot()
            val remove = find("book_detail_remove_button").getUnclippedBoundsInRoot()
            val layouts = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            ui.onNodeWithText("从书架移除", useUnmergedTree = true).performSemanticsAction(
                androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ) { it(layouts) }
            val text = layouts.single()
            assertEquals(2f, text.layoutInput.density.fontScale, .01f)
            assertTrue("buttons overlap at large type: $remove, $read", remove.bottom <= read.top)
            assertEquals(read.right - read.left, remove.right - remove.left)
            assertEquals(1, text.lineCount)
        } finally {
            org.robolectric.RuntimeEnvironment.setFontScale(previous)
        }
    }

    @Test @Config(qualifiers = "w411dp-h891dp-notnight")
    fun cancelledShelfPreviewPreservesDarkReaderSystemBars() {
        val id = importTestBook("系统栏")
        val app = ui.activity.application as ReaderApplication
        runBlocking { app.library.updatePreferences { it.copy(theme = ReaderTheme.AMOLED_BLACK) } }
        find("book_grid_card_$id").performClick()
        find("reader_top_bar").assertIsDisplayed()
        val window = ui.activity.window
        val bars = WindowCompat.getInsetsController(window, window.decorView)
        val dispatcher = ui.activity.onBackPressedDispatcher
        fun preview() {
            ui.runOnIdle { dispatcher.dispatchOnBackStarted(BackEventCompat(0f, 50f, 0f, BackEventCompat.EDGE_LEFT)) }
            ui.runOnIdle { dispatcher.dispatchOnBackProgressed(BackEventCompat(50f, 50f, .6f, BackEventCompat.EDGE_LEFT)) }
            find("bookshelf_sort_button")
            assertFalse(bars.isAppearanceLightStatusBars)
            assertFalse(bars.isAppearanceLightNavigationBars)
        }
        assertFalse(bars.isAppearanceLightStatusBars)
        preview()
        ui.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        ui.onAllNodesWithTag("bookshelf_sort_button").assertCountEquals(0)
        assertFalse("reader status bar after cancellation", bars.isAppearanceLightStatusBars)
        assertFalse("reader navigation bar after cancellation", bars.isAppearanceLightNavigationBars)
        preview()
        ui.runOnIdle { dispatcher.onBackPressed() }
        ui.onAllNodesWithTag("reader_top_bar").assertCountEquals(0)
        assertTrue(bars.isAppearanceLightStatusBars)
        assertTrue(bars.isAppearanceLightNavigationBars)
    }

    @Test fun pinchChangesOneGridStepAndKeepsBookTapsSeparate() {
        val app = ui.activity.application as ReaderApplication
        for (index in 1..8) {
            val uri = Uri.parse("content://reader.test/grid-$index")
            val bytes = epub("书架 $index")
            shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
            runBlocking { app.library.import(uri) }
        }
        val cards = SemanticsMatcher("book covers") {
            it.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null }
                ?.startsWith("book_grid_card_") == true
        }
        fun expectColumns(expected: Int) {
            ui.waitUntil(10000) {
                val tops = ui.onAllNodes(cards).fetchSemanticsNodes().map { it.boundsInRoot.top }
                tops.isNotEmpty() && tops.count { it == tops.minOrNull() } == expected
            }
        }
        fun expectCardInsets(columns: Int) {
            val tag = ui.onAllNodes(cards).fetchSemanticsNodes().first().config[SemanticsProperties.TestTag]
            val inCard = hasAnyAncestor(hasTestTag(tag))
            val card = find(tag).getUnclippedBoundsInRoot()
            val cover = ui.onNode(hasTestTag("book_grid_cover") and inCard, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val label = ui.onNode(hasText("未读") and inCard, useUnmergedTree = true).getUnclippedBoundsInRoot()
            val track = ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo) and inCard,
                useUnmergedTree = true).getUnclippedBoundsInRoot()
            val inset = when (columns) {
                2 -> 12f
                3 -> 8f
                else -> 6f
            }
            val coverInset = when (columns) {
                2 -> 8f
                3 -> 6f
                else -> 4f
            }
            assertEquals(coverInset, (cover.left - card.left).value, 1f)
            assertEquals(coverInset, (card.right - cover.right).value, 1f)
            assertEquals(inset, (track.left - card.left).value, 1f)
            assertEquals(inset, (card.right - label.right).value, 1f)
            assertEquals(8f, (label.left - track.right).value, 1f)
        }
        fun pinch(inward: Boolean) {
            find("bookshelf_grid").performTouchInput {
                val start = if (inward) 0.32f else 0.08f
                val end = if (inward) 0.08f else 0.32f
                down(0, percentOffset(0.5f - start, 0.16f))
                down(1, percentOffset(0.5f + start, 0.16f))
                for (frame in 1..6) {
                    val span = start + (end - start) * frame / 6
                    updatePointerTo(0, percentOffset(0.5f - span, 0.16f))
                    updatePointerTo(1, percentOffset(0.5f + span, 0.16f))
                    move()
                }
                up(1); up(0)
            }
        }
        expectColumns(2)
        expectCardInsets(2)
        val importButton = ui.onNodeWithContentDescription("导入文件夹")
        val toolbarPosition = importButton.getUnclippedBoundsInRoot()
        find("bookshelf_grid").performScrollToIndex(6)
        assertEquals(toolbarPosition, importButton.getUnclippedBoundsInRoot())
        find("bookshelf_grid").performScrollToIndex(0)
        pinch(true); expectColumns(3)
        expectCardInsets(3)
        pinch(true); expectColumns(4)
        expectCardInsets(4)
        pinch(true); expectColumns(4)
        find("bookshelf_toggle_view_button").performClick()
        find("bookshelf_list").assertExists()
        assertEquals(toolbarPosition, importButton.getUnclippedBoundsInRoot())
        find("bookshelf_toggle_view_button").performClick()
        expectColumns(4)
        pinch(false); expectColumns(3)
        pinch(false); expectColumns(2)
        pinch(false); expectColumns(2)
        ui.onAllNodes(cards).onFirst().performClick()
        find("reader_top_bar").assertExists()
    }

    @Test fun seekingKeepsTheThumbAtTheRequestedPositionThroughStaleCallbacks() {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/seek.epub")
        val bytes = epub("进度条")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val id = runBlocking { app.library.import(uri).first.id }
        find("book_grid_card_$id").performClick()
        val slider = find("reader_progress_slider")
        ui.waitUntil(10000) { ui.runOnIdle {
            ui.activity.supportFragmentManager.fragments.any { it is EpubNavigatorFragment && it.view != null }
        } }

        val vm = ui.runOnIdle {
            androidx.lifecycle.ViewModelProvider(ui.activity,
                com.example.ui.reader.ReaderViewModel.provideFactory(id, app.library))
                .get("seek-callback-test", com.example.ui.reader.ReaderViewModel::class.java)
        }
        awaitReader(vm)
        val pub = requireNotNull(vm.publication)
        val first = requireNotNull(pub.locatorFromLink(pub.readingOrder.first()))
        val target = runBlocking { requireNotNull(pub.locateProgression(0.73)) }
        assertNotEquals(first.href, target.href)
        ui.runOnIdle {
            val nav = ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().single()
            vm.attach(nav)
            vm.moved(first)
            vm.previewSeek(.73f)
            vm.moved(first)
            assertEquals(.73f, vm.uiState.value.previewProgress)
            vm.finishSeek()
            vm.moved(first)
            assertEquals(.73f, vm.uiState.value.previewProgress)
            vm.moved(target.copyWithLocations(progression = 0.0), pageCount = 12)
            assertEquals(.73f, vm.uiState.value.previewProgress)
            vm.moved(target, pageCount = 12)
            assertNull(vm.uiState.value.previewProgress)
            assertEquals(.73f, vm.uiState.value.overallProgress, .0001f)
            vm.moved(target)
            assertEquals(.73f, vm.uiState.value.overallProgress, .0001f)
            vm.detach(nav)
        }
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(.66f) }
        ui.waitUntil(10000) { ui.onAllNodesWithText("66%").fetchSemanticsNodes().isNotEmpty() }
    }

    @Test fun toolbarImportPrecedesSortAndViewAndOpensTheSystemFolderPicker() {
        val button = ui.onNodeWithContentDescription("导入文件夹")
        val add = button.getUnclippedBoundsInRoot()
        val sort = find("bookshelf_sort_button").getUnclippedBoundsInRoot()
        val view = find("bookshelf_toggle_view_button").getUnclippedBoundsInRoot()
        assertEquals(sort.top, add.top)
        assertTrue(add.right <= sort.left && sort.right <= view.left)
        button.performClick()
        ui.runOnIdle {
            val shadow = shadowOf(ui.activity)
            val request = requireNotNull(shadow.nextStartedActivityForResult)
            assertEquals(android.content.Intent.ACTION_OPEN_DOCUMENT_TREE, request.intent.action)
            shadow.receiveResult(request.intent, android.app.Activity.RESULT_CANCELED, null)
        }
        button.assertIsDisplayed()
    }

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun lightShelfAndReaderUseMatchingSurfacesAndSliders() = verifyReaderColors()

    @Test @GraphicsMode(GraphicsMode.Mode.NATIVE) @Config(qualifiers = "w411dp-h891dp-night")
    fun darkShelfAndReaderUseMatchingSurfacesAndSliders() = verifyReaderColors()

    private fun verifyReaderColors() {
        fun actionMetrics(tag: String): Triple<Float, androidx.compose.ui.unit.TextUnit, androidx.compose.ui.text.font.FontWeight?> {
            val height = find(tag).fetchSemanticsNode().boundsInRoot.height
            val layout = mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
            val getLayout = androidx.compose.ui.semantics.SemanticsActions.GetTextLayoutResult
            ui.onNode(SemanticsMatcher.keyIsDefined(getLayout) and hasAnyAncestor(hasTestTag(tag)), useUnmergedTree = true)
                .performSemanticsAction(getLayout) { it(layout) }
            val text = layout.single()
            assertFalse("$tag truncates its label", text.multiParagraph.didExceedMaxLines)
            for (line in 0 until text.lineCount) {
                val right = text.getLineRight(line)
                val left = text.getLineLeft(line)
                assertTrue("$tag: text line spans $left..$right in ${text.size.width}px", left >= -1f && right <= text.size.width + 1f)
            }
            return Triple(height, text.layoutInput.style.fontSize, text.layoutInput.style.fontWeight)
        }
        fun fill(tag: String, y: Float, x: Float = 0.5f): Int {
            val pixels = find(tag).captureToImage().toPixelMap()
            return pixels[(pixels.width * x).toInt(), (pixels.height * y).toInt()].toArgb()
        }
        fun sliderSamples(tag: String): List<Pair<Int, Int>> {
            val pixels = find(tag).captureToImage().toPixelMap()
            // At 50%, these columns pass through the filled track, thumb and remaining track.
            return listOf(0.25f, 0.5f, 0.75f).map { fraction ->
                val x = (pixels.width * fraction).toInt()
                val color = pixels[x, pixels.height / 2].toArgb()
                color to (0 until pixels.height).count { pixels[x, it].toArgb() == color }
            }
        }
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/colors.epub")
        val bytes = epub("界面配色")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val imported = ui.activity.lifecycleScope.async(Dispatchers.IO) {
            app.library.import(uri).first.also { book ->
                app.library.stepGridColumns(1)
                app.library.setLayoutMode(book.id, LayoutMode.GLOBAL)
                val pub = app.library.open(book.id)
                try {
                    app.library.savePosition(book.id, 0, "上章", 0.4f, requireNotNull(pub.locateProgression(0.4)))
                } finally { pub.close() }
            }
        }
        val main = shadowOf(ui.activity.mainLooper)
        ui.waitUntil(15000) {
            main.idleFor(20, java.util.concurrent.TimeUnit.MILLISECONDS)
            imported.isCompleted
        }
        val book = runBlocking { imported.await() }

        val dark = ui.activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        val system = if (dark) dynamicDarkColorScheme(ui.activity) else dynamicLightColorScheme(ui.activity)
        val panel = (if (dark) system.surfaceContainerHigh else system.surfaceContainer).toArgb()
        val canvas = (if (dark) Color.Black else Color.White).toArgb()
        find("book_grid_card_" + book.id)
        val track = ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo),
            useUnmergedTree = true).captureToImage().toPixelMap()
        val accentFill = track[track.width * 4 / 5, track.height / 2].toArgb()
        fun checkBookTrack(node: SemanticsNodeInteraction) {
            val bar = node.captureToImage().toPixelMap()
            val remainingX = bar.width * 4 / 5
            assertEquals(system.primary.toArgb(), bar[bar.width / 5, bar.height / 2].toArgb())
            assertEquals(accentFill, bar[remainingX, bar.height / 2].toArgb())
            val thickness = (0 until bar.height).count { bar[remainingX, it].toArgb() == accentFill }
            assertEquals(with(ui.density) { 4.dp.roundToPx() }, thickness)
        }
        val previous = (if (dark) system.secondaryContainer else lerp(system.secondaryContainer, system.primary, 0.16f))
            .convert(ColorSpaces.Oklab)
        val actual = Color(accentFill).convert(ColorSpaces.Oklab)
        val previousChroma = hypot(previous.green, previous.blue)
        val actualChroma = hypot(actual.green, actual.blue)
        assertEquals("accent lightness", previous.red, actual.red, 0.015f)
        if (previousChroma > 0.01f) assertTrue("stronger accent", actualChroma > previousChroma * 1.2f)
        else assertTrue("neutral wallpaper", actualChroma < 0.025f)
        val titleBounds = ui.onNodeWithText("书架").fetchSemanticsNode().boundsInRoot
        val sortBounds = find("bookshelf_sort_button").fetchSemanticsNode().boundsInRoot
        val toggleBounds = find("bookshelf_toggle_view_button").fetchSemanticsNode().boundsInRoot
        assertTrue(abs(titleBounds.center.y - sortBounds.center.y) < sortBounds.height / 8)
        assertTrue(abs(titleBounds.center.y - toggleBounds.center.y) < toggleBounds.height / 8)
        find("bookshelf_sort_button").performClick()
        assertEquals(panel, fill("bookshelf_sort_menu", 0.015f))
        for (tag in listOf("recently_read", "title", "progress", "author")) {
            assertEquals(panel, fill("sort_option_$tag", 0.15f))
        }
        find("sort_option_recently_read").assertIsSelected()
        val label = ui.onNodeWithText("最近阅读", useUnmergedTree = true).captureToImage().toPixelMap()
        assertTrue("sort label uses the neutral text color", (0 until label.height).any { y ->
            (0 until label.width).any { x -> label[x, y].toArgb() == system.onSurfaceVariant.toArgb() }
        })
        find("sort_option_title").performClick()
        find("bookshelf_sort_button").performClick()
        find("sort_option_title").assertIsSelected()
        find("sort_option_recently_read").assertIsNotSelected()
        assertEquals(panel, fill("sort_option_title", 0.15f))
        find("sort_option_recently_read").performClick()

        val shelfHeaderBottom = find("bookshelf_grid").fetchSemanticsNode().boundsInRoot.top
        for (mode in listOf("grid", "list")) {
            if (mode == "list") find("bookshelf_toggle_view_button").performClick()
            val card = "book_${mode}_card_${book.id}"
            val shelfTop = find("bookshelf_$mode").fetchSemanticsNode().boundsInRoot.top
            assertTrue("compact header above $mode", shelfTop - titleBounds.center.y in 0f..sortBounds.height)
            val shelf = find("bookshelf_$mode").captureToImage().toPixelMap()
            assertEquals(canvas, shelf[1, shelf.height / 2].toArgb())
            assertEquals(panel, fill(card, 0.99f))
            val inCard = hasAnyAncestor(hasTestTag(card))
            val progress = ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
                and inCard, useUnmergedTree = true)
            if (mode == "grid") {
                val bounds = find(card).getUnclippedBoundsInRoot()
                val track = progress.getUnclippedBoundsInRoot()
                val percent = ui.onNode(hasText("40%") and inCard, useUnmergedTree = true).getUnclippedBoundsInRoot()
                for (text in listOf(book.title, book.author)) {
                    // The metadata appears below any lettering on the fallback cover.
                    val field = ui.onAllNodes(hasText(text) and inCard, useUnmergedTree = true).onLast().getUnclippedBoundsInRoot()
                    assertEquals(8f, (field.left - bounds.left).value, 1f)
                }
                assertEquals(8f, (track.left - bounds.left).value, 1f)
                assertEquals(8f, (percent.left - track.right).value, 1f)
                assertEquals(8f, (bounds.right - percent.right).value, 1f)
            }
            checkBookTrack(progress)
        }
        find("book_list_card_" + book.id).performTouchInput { longClick() }
        assertEquals(panel, fill("book_detail_sheet", 0.015f, 0.025f))
        ui.assertFourPercentRow("book_detail_remove_button", "book_detail_read_button")
        ui.onNodeWithText("暂无简介").assertExists()
        ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)
            and hasAnyAncestor(hasTestTag("book_detail_sheet")), useUnmergedTree = true).assertDoesNotExist()
        find("book_detail_read_button").performClick()
        assertEquals(panel, fill("reader_top_bar", 0.15f))
        assertEquals(shelfHeaderBottom, find("reader_top_bar").fetchSemanticsNode().boundsInRoot.bottom, 1f)
        assertEquals(panel, fill("reader_bottom_capsule", 0.05f))
        assertEquals(accentFill, fill("reader_settings_button", 0.25f))
        ui.assertFourPercentRow("reader_capsule_toc_button", "reader_settings_button")
        val settingsMetrics = actionMetrics("reader_settings_button")
        assertEquals(settingsMetrics, actionMetrics("reader_capsule_toc_button"))
        fun expectBookmarkNotice(message: Int) {
            val text = ui.activity.getString(message)
            ui.waitUntil(10000) { ui.onAllNodes(hasTestTag("bookmark_notice") and hasAnyDescendant(hasText(text)))
                .fetchSemanticsNodes().isNotEmpty() }
            assertEquals(settingsMetrics, actionMetrics("bookmark_notice"))
        }
        find("reader_bookmark_toggle").performClick()
        expectBookmarkNotice(R.string.reader_bookmark_added)
        find("reader_bookmark_toggle").performClick()
        expectBookmarkNotice(R.string.reader_bookmark_removed)
        find("reader_capsule_toc_button").performClick()
        assertEquals(panel, fill("toc_sheet", 0.015f, 0.025f))
        assertEquals(panel, fill("sheet_tab_toc", 0.18f))
        assertEquals(panel, fill("sheet_tab_bookmarks", 0.18f))
        find("sheet_tab_toc").assertIsSelected()
        ui.assertFourPercentRow("sheet_tab_toc", "sheet_tab_bookmarks")
        find("sheet_tab_bookmarks").performClick().assertIsSelected()
        find("sheet_add_bookmark_button").performClick()
        expectBookmarkNotice(R.string.reader_bookmark_added)
        ui.onNode(hasTestTag("bookmark_notice") and hasAnyAncestor(hasTestTag("toc_sheet"))).assertExists()
        ui.onNodeWithContentDescription(ui.activity.getString(R.string.sheet_delete_bookmark_cd)).performClick()
        expectBookmarkNotice(R.string.reader_bookmark_removed)
        find("sheet_tab_toc").performClick()
        find("toc_item_toc.0").performClick()
        find("reader_search").performClick()
        assertEquals(panel, fill("search_sheet", 0.015f, 0.025f))
        find("search_query").performTextInput("正文")
        find("search_query").performImeAction()
        find("search_hit_0").performClick()
        val slider = find("reader_progress_slider")
        ui.waitUntil(10000) { ui.runOnIdle {
            ui.activity.supportFragmentManager.fragments.any { it is EpubNavigatorFragment && it.view != null }
        } }
        slider.performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.SetProgress) { it(0.5f) }
        ui.onNodeWithText("50%").assertExists()
        val chapter = sliderSamples("reader_progress_slider")
        assertNotEquals(chapter[0].first, chapter[2].first)
        assertEquals(accentFill, chapter[2].first)
        find("reader_settings_button").performClick()
        assertEquals(panel, fill("settings_sheet", 0.015f, 0.025f))
        assertEquals(canvas, fill("layout_ORIGINAL", 0.18f))
        assertEquals(accentFill, fill("layout_GLOBAL", 0.18f))
        assertEquals(settingsMetrics, actionMetrics("layout_GLOBAL"))
        find("line_height_slider").performScrollTo()
        assertEquals(chapter, sliderSamples("line_height_slider"))
        val dismiss = androidx.compose.ui.semantics.SemanticsActions.Dismiss
        ui.onNode(SemanticsMatcher.keyIsDefined(dismiss) and hasClickAction()).performSemanticsAction(dismiss)
        ui.waitUntil(10000) { ui.onAllNodesWithTag("settings_sheet").fetchSemanticsNodes().isEmpty() }
    }

    @Test fun typographySwitchesChangeTheNavigatorAndSaveInTheSelectedScope() {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/layout.epub")
        val bytes = epub("排版切换", rtl = true)
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val id = runBlocking { app.library.import(uri).first.id }
        find("book_grid_card_$id").performClick()
        find("reader_settings_button").performClick()
        fun hasLayout(original: Boolean, pageMargin: Double = if (original) 1.0 else 22.0) = ui.runOnIdle {
            val navigator = ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().firstOrNull()
            val settings = navigator?.settings?.value
            val viewport = ui.activity.findViewById<View>(android.R.id.content).width
            settings != null && viewport > 0 && navigator.view?.width == viewport &&
                settings.pageMargins == pageMargin && settings.publisherStyles == original && settings.verticalText == original &&
                (settings.readingProgression == org.readium.r2.navigator.preferences.ReadingProgression.RTL) == original
        }
        find("layout_ORIGINAL").assertIsSelected()
        ui.waitUntil(10000) { hasLayout(original = true) }
        find("layout_GLOBAL").performClick()
        ui.waitUntil(10000) { hasLayout(original = false) }
        find("layout_GLOBAL").assertIsSelected()
        val initial = runBlocking { app.library.preferences.first().fontSizeSp }
        find("font_size_increase").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("19 sp").fetchSemanticsNodes().isNotEmpty() }
        ui.waitUntil(10000) { hasLayout(original = false) }
        find("layout_CUSTOM").assertIsDisplayed().performClick()
        ui.waitUntil(10000) { ui.onAllNodes(hasTestTag("layout_CUSTOM") and isSelected()).fetchSemanticsNodes().isNotEmpty() }
        find("margin_slider").performScrollTo().performSemanticsAction(
            androidx.compose.ui.semantics.SemanticsActions.SetProgress
        ) { it(34f) }
        ui.waitUntil(10000) { hasLayout(original = false, pageMargin = 34.0) }
        find("font_size_increase").performScrollTo().performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("20 sp").fetchSemanticsNodes().isNotEmpty() }
        assertEquals(initial + 1, runBlocking { app.library.preferences.first().fontSizeSp })
        find("layout_GLOBAL").assertIsDisplayed().performClick()
        ui.waitUntil(10000) { hasLayout(original = false) }
        find("layout_ORIGINAL").assertIsDisplayed().performClick()
        ui.waitUntil(10000) { hasLayout(original = true) }
        assertEquals(LayoutMode.ORIGINAL, runBlocking { app.library.layout(id).first().mode })
        ui.onNodeWithTag("font_size_slider").assertDoesNotExist()
    }

    @Test fun toolbarUsesPublishedTocAndOnlyChevronExpandsChildren() {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/front.epub")
        val bytes = epub("前置页测试", withFrontMatter = true)
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val id = runBlocking { app.library.import(uri).first.id }
        find("book_grid_card_$id").performClick()
        find("reader_capsule_toc_button").assertTextContains("目录 / 书签")
        find("reader_prev_chapter_button").assertIsNotEnabled()
        fun page() = ui.runOnIdle {
            ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>()
                .firstOrNull()?.view?.findPager()?.currentItem
        }
        ui.waitUntil(10000) { page() == 0 }
        find("reader_next_chapter_button").performClick()
        ui.waitUntil(10000) { page() == 1 }
        ui.onNodeWithTag("reader_toc_button").assertDoesNotExist()
        find("reader_capsule_toc_button").performClick()
        ui.onNodeWithTag("toc_item_toc.2.0").assertDoesNotExist()
        val expand = ui.onNodeWithContentDescription("展开「下章」")
        expand.performClick()
        find("toc_item_toc.2.0").assertExists()
        ui.onNodeWithTag("toc_item_toc.2.0.0").assertDoesNotExist()
        ui.onNodeWithContentDescription("展开「小节」").performClick()
        find("toc_item_toc.2.0.0").assertExists()
        ui.onNodeWithContentDescription("收起「下章」").performClick()
        ui.onNodeWithTag("toc_item_toc.2.0").assertDoesNotExist()
        assertEquals(1, page())
        ui.onNodeWithText("正在阅读").assertDoesNotExist()
        find("toc_item_toc.2").performClick()
        ui.waitUntil(10000) { page() == 3 }
        ui.onNodeWithTag("toc_sheet").assertDoesNotExist()
    }

    @Test fun searchResultsBounceAtTheEdgeAndOnlyDismissOnAFreshPullFromTheTop() {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/search-gestures.epub")
        val bytes = epub("搜索列表", "测试词。".repeat(32))
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val id = runBlocking { app.library.import(uri).first.id }
        find("book_grid_card_$id").performClick()
        find("reader_search").performClick()
        find("search_query").performTextInput("测试词")
        ui.onNodeWithTag("sheet_section_divider").assertDoesNotExist()
        find("search_query").performImeAction()
        ui.waitUntil(10000) { ui.onAllNodesWithText("已显示 50 处匹配").fetchSemanticsNodes().isNotEmpty() }

        val count = ui.onNodeWithText("已显示 50 处匹配")
        val countBounds = count.getUnclippedBoundsInRoot()
        val divider = find("sheet_section_divider").getUnclippedBoundsInRoot()
        val dialog = ui.onNode(isDialog()).getUnclippedBoundsInRoot()
        val firstHit = find("search_hit_0")
        assertEquals(dialog.left + 24.dp, divider.left)
        assertEquals(dialog.right - 24.dp, divider.right)
        assertEquals(divider.left, countBounds.left)
        assertEquals(divider.left, firstHit.getUnclippedBoundsInRoot().left)
        assertEquals(8.dp, divider.top - countBounds.bottom)
        assertEquals(divider.bottom, firstHit.getUnclippedBoundsInRoot().top)
        firstHit.checkSinglePageSidePull()
        val list = find("search_results").performScrollToIndex(50)
        val more = ui.onNodeWithText("显示更多结果")
        val bottomRowTop = more.getUnclippedBoundsInRoot().top
        list.performTouchInput {
            down(center)
            moveTo(percentOffset(.5f, .15f), 100)
        }
        val displacement = bottomRowTop - more.getUnclippedBoundsInRoot().top
        assertTrue(displacement > 16.dp && displacement <= 52.dp)
        assertEquals(countBounds, count.getUnclippedBoundsInRoot())
        assertEquals(divider, find("sheet_section_divider").getUnclippedBoundsInRoot())
        list.performTouchInput { up() }
        assertEquals(bottomRowTop, more.getUnclippedBoundsInRoot().top)
        more.performClick()
        ui.waitUntil(10000) { ui.onAllNodesWithText("共 64 处匹配").fetchSemanticsNodes().isNotEmpty() }
        find("search_hit_49").assertIsDisplayed()

        list.performScrollToIndex(1).performTouchInput { swipeDown(durationMillis = 100) }
        find("search_hit_0").assertIsDisplayed()
        list.performTouchInput { swipeDown(durationMillis = 140) }
        ui.onNodeWithTag("search_sheet").assertDoesNotExist()
        ui.onNodeWithTag("return_from_search").assertDoesNotExist()
    }

    @Test fun searchDetourReturnsToOriginalChapterAfterTwoHits() {
        val app = ui.activity.application as ReaderApplication
        val uri = Uri.parse("content://reader.test/test.epub")
        val bytes = epub("搜索跳转", "另一处正文。")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
        val id = runBlocking { app.library.import(uri).first.id }
        find("book_grid_card_$id").performClick()
        find("reader_search").performClick()
        find("search_query").performTextInput("正文")
        find("search_query").performImeAction()
        ui.waitUntil(10000) { ui.onAllNodesWithText("共 4 处匹配").fetchSemanticsNodes().isNotEmpty() }
        find("search_results").performScrollToIndex(2)
        find("search_hit_2").performClick()

        fun chapter() = ui.runOnIdle {
            ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>()
                .firstOrNull()?.view?.findPager()?.currentItem
        }
        find("return_from_search").assertExists()
        ui.waitUntil(10000) { chapter() == 1 }
        find("reader_search").performClick()
        find("search_results").performScrollToIndex(3)
        find("search_hit_3").performClick()
        find("return_from_search").performClick()
        ui.waitUntil(10000) { chapter() == 0 }
        ui.onNodeWithTag("return_from_search").assertDoesNotExist()

        find("reader_search").performClick()
        find("search_results").performScrollToIndex(2)
        find("search_hit_2").performClick()
        ui.waitUntil(10000) { chapter() == 1 }
        find("stay_at_search_result").performClick()
        ui.waitForIdle()
        assertEquals(1, chapter())
        ui.onNodeWithTag("return_from_search").assertDoesNotExist()
        ui.onNodeWithTag("stay_at_search_result").assertDoesNotExist()

        val reader = ui.runOnIdle {
            androidx.lifecycle.ViewModelProvider(ui.activity, com.example.ui.reader.ReaderViewModel.provideFactory(id, app.library))
                .get("search-highlight", com.example.ui.reader.ReaderViewModel::class.java)
        }
        awaitReader(reader)
        ui.runOnIdle {
            val nav = ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().single()
            val pub = requireNotNull(reader.publication)
            reader.attach(nav)
            val hit = requireNotNull(pub.locatorFromLink(pub.readingOrder.last()))
            reader.openPanel(ReaderPanel.SEARCH)
            reader.openPanel(ReaderPanel.TOC)
            reader.dismissPanel(ReaderPanel.SEARCH)
            assertEquals(ReaderPanel.TOC, reader.uiState.value.panel)
            reader.openPanel(ReaderPanel.SEARCH)
            reader.readSearchHit(hit)
            assertNull(reader.uiState.value.panel)
            assertEquals(hit, reader.uiState.value.searchHighlight)
            reader.finishSearchVisit()
            assertNull(reader.uiState.value.searchHighlight)
            assertFalse(reader.uiState.value.canReturnFromSearch)
            reader.detach(nav)
        }
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    @Test fun footnotesShowTheirTextWithoutLeavingThePage() {
        val ref = """<a epub:type="noteref" href="b.xhtml#test-note"><img alt="注" src="note.png"/></a>"""
        val note = """<span epub:type="footnote"><i id="test-note">跨章节</i><b>注释</b> &amp; 说明。</span>"""
        val endnote = """<span xmlns:ops="http://www.idpf.org/2007/ops" ops:type="endnote"><i id="tail">尾注</i>正文。</span>"""
        val id = importTestBook("脚注测试", extraText = ref + note + endnote)
        find("book_grid_card_$id").performClick()
        fun fragment() = ui.activity.supportFragmentManager.fragments.filterIsInstance<EpubNavigatorFragment>().firstOrNull()
        fun web() = (fragment()?.view as? ViewGroup)?.descendants?.filterIsInstance<WebView>()?.firstOrNull()
        ui.waitUntil(10000) { ui.runOnIdle { web() != null && fragment()?.view?.findPager()?.currentItem == 0 } }
        fun sheetHeight(tag: String) = find(tag).getUnclippedBoundsInRoot().run { bottom - top }
        fun measurePanel(button: String, sheet: String): androidx.compose.ui.unit.Dp {
            find(button).performClick()
            val height = sheetHeight(sheet)
            ui.onNodeWithContentDescription("关闭面板").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            ui.waitUntil(5000) { ui.onAllNodesWithTag(sheet).fetchSemanticsNodes().isEmpty() }
            return height
        }
        val maxHeight = measurePanel("reader_capsule_toc_button", "toc_sheet")
        assertEquals(maxHeight, measurePanel("reader_search", "search_sheet"))
        val webView = ui.runOnIdle { requireNotNull(web()) }
        val tap = org.json.JSONObject().put("x", 12).put("y", 12).put("interactiveElement", ref).toString()
        val consumed = runBlocking(Dispatchers.IO) {
            webView.javaClass.getMethod("onTap", String::class.java).invoke(webView, tap)
        }
        assertEquals(true, consumed)
        fun checkNote(text: String, scroll: Boolean = false) {
            ui.waitUntil(10000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
            ui.onNodeWithText(text).assertIsDisplayed()
            val heading = ui.onNodeWithText("注").assertIsDisplayed()
            val divider = find("sheet_section_divider").assertIsDisplayed()
            val height = sheetHeight("footnote_sheet")
            val body = ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange) and hasAnyAncestor(isDialog()))
            if (scroll) {
                assertEquals(maxHeight, height)
                val top = heading.getUnclippedBoundsInRoot()
                val line = divider.getUnclippedBoundsInRoot()
                body.performTouchInput { swipeUp() }
                assertTrue(body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].value() > 0f)
                assertEquals(top, heading.getUnclippedBoundsInRoot())
                assertEquals(line, divider.getUnclippedBoundsInRoot())
                ui.runOnIdle {
                    val dialog = org.robolectric.shadows.ShadowDialog.getLatestDialog() as androidx.activity.ComponentDialog
                    dialog.onBackPressedDispatcher.onBackPressed()
                }
            } else {
                assertTrue(height < maxHeight)
                assertEquals(0f, body.fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange].maxValue(), 0f)
                ui.onNodeWithContentDescription("关闭面板").performClick()
            }
            ui.waitUntil(5000) { ui.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
            ui.onNodeWithTag("return_from_search").assertDoesNotExist()
            assertEquals(0, ui.runOnIdle { fragment()?.view?.findPager()?.currentItem })
        }
        checkNote("跨章节注释 & 说明。")
        ui.runOnIdle {
            val nav = requireNotNull(fragment())
            val listener = EpubNavigatorFragment::class.java.getMethod("getListener\$readium_navigator")
                .invoke(nav) as EpubNavigatorFragment.Listener
            val link = org.readium.r2.shared.publication.Link(href = org.readium.r2.shared.publication.Href("b.xhtml#tail")!!)
            assertFalse(listener.shouldFollowInternalLink(link, null))
        }
        checkNote("尾注正文。")
        val inline = ui.runOnIdle { requireNotNull(shadowOf(webView).getJavascriptInterface("EbookFootnotes")) }
        val show = inline.javaClass.getMethod("showNote", String::class.java).apply { isAccessible = true }
        val longNote = (1..80).joinToString("\n") { "由原书提供的行内注释，第 ${it} 行。" }
        runBlocking(Dispatchers.IO) { show.invoke(inline, longNote) }
        checkNote(longNote, scroll = true)
    }

    @OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
    @Test fun internalLinkJumpShowsReturnBarAndReturnsToOrigin() {
        val id = importTestBook("引用跳转", extraText = """<a id="back" href="a.xhtml#ref">*</a>普通说明。<a id="ref" href="b.xhtml#back"><sup>*</sup></a>""")
        find("book_grid_card_$id").performClick()
        fun nav() = ui.activity.supportFragmentManager.fragments
            .filterIsInstance<EpubNavigatorFragment>()
            .firstOrNull { it.view != null }
        fun chapter() = ui.runOnIdle { nav()?.view?.findPager()?.currentItem }
        ui.waitUntil(10000) { chapter() == 0 }

        val targetLink = org.readium.r2.shared.publication.Link(
            href = org.readium.r2.shared.publication.Href("b.xhtml#back")!!,
        )
        fun triggerLink() = ui.runOnIdle {
            val fragment = requireNotNull(nav())
            val listener = EpubNavigatorFragment::class.java
                .getMethod("getListener\$readium_navigator")
                .invoke(fragment) as EpubNavigatorFragment.Listener
            assertFalse(listener.shouldFollowInternalLink(targetLink, null))
        }
        triggerLink()
        find("return_from_search").assertExists()
        ui.waitUntil(10000) { chapter() == 1 }

        find("return_from_search").performClick()
        ui.waitUntil(10000) { chapter() == 0 }
        ui.onNodeWithTag("return_from_search").assertDoesNotExist()

        triggerLink()
        ui.waitUntil(10000) { chapter() == 1 }
        find("stay_at_search_result").performClick()
        ui.waitForIdle()
        assertEquals(1, chapter())
        ui.onNodeWithTag("return_from_search").assertDoesNotExist()
    }
}
