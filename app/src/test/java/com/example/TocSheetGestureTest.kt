package com.example

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import com.example.model.Book
import com.example.model.Bookmark
import com.example.model.TocItem
import com.example.ui.reader.TocAndBookmarksSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

internal fun SemanticsNodeInteraction.checkSinglePageSidePull() {
    val start = getUnclippedBoundsInRoot()
    for (direction in listOf(-1f, 1f)) {
        performTouchInput {
            down(center)
            moveBy(Offset(direction * 70.dp.toPx(), 0f), 100)
        }
        val dragged = getUnclippedBoundsInRoot()
        val shift = (dragged.left - start.left) * direction
        assertTrue("side pull: $shift", shift > 0.dp && shift <= 20.dp)
        assertEquals(start.top, dragged.top)
        performTouchInput { up() }
        assertEquals(start, getUnclippedBoundsInRoot())
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
class TocSheetGestureTest {
    @get:Rule val ui = createComposeRule()
    private var active by mutableStateOf("toc.0")

    private fun open(count: Int = 100) {
        val toc = (0 until count).map { TocItem("toc.$it", "标题 $it", it) }
        val book = Book("id", "书名", "作者", "", "", 0f, 0, 0L, "", toc = toc)
        val marks = toc.mapIndexed { i, item -> Bookmark("b$i", book.id, i, item.title, i, "", "") }
        ui.setContent {
            var shown by remember { mutableStateOf(true) }
            MaterialTheme {
                Box(Modifier.fillMaxSize().testTag("reader_host"), contentAlignment = Alignment.BottomCenter) {
                    Button(onClick = { shown = true }, modifier = Modifier.testTag("open_sheet")) { Text("目录 / 书签") }
                    if (shown) TocAndBookmarksSheet(book, active, marks, { shown = false },
                        { error("unexpected tap") }, { error("unexpected tap") }, {}, {}, remember { SnackbarHostState() })
                }
            }
        }
    }

    @Test fun selectingAChapterKeepsSiblingTitlesAligned() {
        open(count = 2)
        fun titleLeft(index: Int) = ui.onNodeWithText("标题 $index", useUnmergedTree = true)
            .getUnclippedBoundsInRoot().left
        for (index in 0..1) {
            ui.runOnIdle { active = "toc.$index" }
            ui.onNodeWithTag("toc_item_toc.$index").assertIsSelected()
            assertEquals(titleLeft(0), titleLeft(1))
        }
    }

    @Test fun unreleasedDragCanBringThePanelBackFromTheBottomEdge() {
        open()
        val handle = ui.onNode(SemanticsMatcher.keyIsDefined(SemanticsActions.Dismiss) and hasClickAction())
        val bottom = ui.onNodeWithTag("reader_host").getUnclippedBoundsInRoot().bottom
        val distance = with(ui.density) { bottom.toPx() }
        val list = ui.onNodeWithTag("toc_list")
        list.performTouchInput {
            down(center)
            moveBy(Offset(0f, distance), 250)
        }
        assertTrue(handle.getUnclippedBoundsInRoot().top >= bottom)
        list.performTouchInput {
            moveBy(Offset(0f, -distance), 250)
            up()
        }
        list.assertIsDisplayed()
    }

    @Test fun locationUpdateAndTabSwitchKeepTheManualScrollPosition() {
        open()
        ui.onNodeWithTag("toc_list").performScrollToIndex(75)
        ui.runOnIdle { active = "toc.1" }
        ui.onNodeWithTag("toc_item_toc.75").assertIsDisplayed()
        ui.onNodeWithTag("sheet_tab_bookmarks").performClick()
        ui.onNodeWithTag("sheet_tab_toc").performClick()
        ui.onNodeWithTag("toc_item_toc.75").assertIsDisplayed()
    }

    @Test fun onlyANewDownwardGestureAtTheTopDismissesTheToc() {
        open()
        checkList("toc_list")
    }

    @Test fun bookmarksUseTheSameGestureRule() {
        open()
        ui.onNodeWithTag("sheet_tab_bookmarks").performClick()
        checkList("bookmarks_list")
    }

    @Test fun releasingADownwardSheetDragDoesNotWaitForHorizontalPaging() {
        open()
        val sheet = ui.onNodeWithTag("toc_sheet")
        ui.mainClock.autoAdvance = false
        ui.onNodeWithTag("toc_list").performTouchInput {
            swipeDown(startY = height * .1f, endY = height * .85f, durationMillis = 180)
        }
        val releaseTop = sheet.getUnclippedBoundsInRoot().top
        repeat(6) { ui.mainClock.advanceTimeByFrame() }
        assertTrue(sheet.getUnclippedBoundsInRoot().top > releaseTop + 1.dp)
        ui.mainClock.autoAdvance = true
        sheet.assertDoesNotExist()
    }

    @Test fun swipeAndTapShareSelectionAndPreserveBothLists() {
        open()
        val toc = ui.onNodeWithTag("toc_list")
        val marks = ui.onNodeWithTag("bookmarks_list")
        toc.performScrollToIndex(35).performTouchInput { swipeLeft() }
        ui.onNodeWithTag("sheet_tab_bookmarks").assertIsSelected()
        marks.performScrollToIndex(68).performTouchInput { swipeRight() }
        ui.onNodeWithTag("sheet_tab_toc").assertIsSelected()
        ui.onNodeWithTag("toc_item_toc.35").assertIsDisplayed()
        ui.onNodeWithTag("sheet_tab_bookmarks").performClick()
        ui.onNodeWithTag("bookmark_card_b68").assertIsDisplayed()
    }

    @Test fun emptyPagesCanAlsoBeSwiped() {
        open(count = 0)
        val pager = ui.onNodeWithTag("reader_tabs_pager")
        pager.performTouchInput { swipeLeft() }
        ui.onNodeWithTag("sheet_tab_bookmarks").assertIsSelected()
        pager.performTouchInput { swipeRight() }
        ui.onNodeWithTag("sheet_tab_toc").assertIsSelected()
    }

    @Test fun diagonalTabSwipesSettleWithoutReversingOrDraggingTheSheet() {
        open()
        val pager = ui.onNodeWithTag("reader_tabs_pager")
        val sheet = ui.onNodeWithTag("toc_sheet")
        val sheetTop = sheet.getUnclippedBoundsInRoot().top
        val pageStart = pager.getUnclippedBoundsInRoot().left
        ui.mainClock.autoAdvance = false
        fun switchPage(toBookmarks: Boolean) {
            pager.performTouchInput {
                val startX = if (toBookmarks) .85f else .15f
                swipe(percentOffset(startX, .4f), percentOffset(1f - startX, .47f), durationMillis = 100)
            }
            val tag = if (toBookmarks) "bookmarks_list" else "toc_list"
            val target = ui.onNodeWithTag(tag)
            ui.mainClock.advanceTimeUntil(1000) { ui.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
            var previous = target.getUnclippedBoundsInRoot().left
            repeat(40) {
                ui.mainClock.advanceTimeByFrame()
                val left = target.getUnclippedBoundsInRoot().left
                val forward = if (toBookmarks) previous - left else left - previous
                assertTrue("page reversed: $previous to $left", forward >= (-1).dp)
                assertEquals(sheetTop, sheet.getUnclippedBoundsInRoot().top)
                previous = left
            }
            assertEquals(pageStart, previous)
            ui.onNodeWithTag(if (toBookmarks) "sheet_tab_bookmarks" else "sheet_tab_toc").assertIsSelected()
        }
        switchPage(toBookmarks = true)
        switchPage(toBookmarks = false)
    }

    @Test fun outwardTabDragsHaveASmallElasticRangeOnBothEnds() {
        open()
        val pager = ui.onNodeWithTag("reader_tabs_pager")
        fun pull(tag: String, direction: Float) {
            val page = ui.onNodeWithTag(tag)
            val start = page.getUnclippedBoundsInRoot().left
            pager.performTouchInput {
                down(center)
                moveBy(Offset(direction * 75.dp.toPx(), 0f), 100)
            }
            val first = (page.getUnclippedBoundsInRoot().left - start) * direction
            pager.performTouchInput { moveBy(Offset(direction * 75.dp.toPx(), 0f), 100) }
            val second = (page.getUnclippedBoundsInRoot().left - start) * direction
            assertTrue("outward displacement $first -> $second", first > 0.dp && second > first && second <= 20.dp)
            pager.performTouchInput { up() }
            assertEquals(start, page.getUnclippedBoundsInRoot().left)
        }
        pull("toc_list", 1f)
        ui.onNodeWithTag("sheet_tab_bookmarks").performClick()
        pull("bookmarks_list", -1f)
    }

    @Test fun bothListEdgesYieldSlightlyAndSpringBackWithoutMovingTheSheet() {
        open()
        fun check(tag: String, rowPrefix: String, atEnd: Boolean) {
            val index = if (atEnd) 99 else 0
            val list = ui.onNodeWithTag(tag).performScrollToIndex(index)
            val row = ui.onNodeWithTag(rowPrefix + index)
            val top = row.getUnclippedBoundsInRoot().top
            val sheet = ui.onNodeWithTag("toc_sheet")
            val sheetTop = sheet.getUnclippedBoundsInRoot().top
            if (!atEnd) list.performScrollToIndex(1)
            list.performTouchInput {
                down(center)
                moveTo(percentOffset(.5f, if (atEnd) .15f else .85f), 100)
            }
            val delta = row.getUnclippedBoundsInRoot().top - top
            val shift = if (atEnd) -delta else delta
            assertTrue("edge displacement: $shift", shift > 16.dp && shift <= 52.dp)
            assertEquals(sheetTop, sheet.getUnclippedBoundsInRoot().top)
            list.performTouchInput { up() }
            assertEquals(top, row.getUnclippedBoundsInRoot().top)
        }
        check("toc_list", "toc_item_toc.", atEnd = true)
        check("toc_list", "toc_item_toc.", atEnd = false)
        ui.onNodeWithTag("sheet_tab_bookmarks").performClick()
        check("bookmarks_list", "bookmark_card_b", atEnd = true)
        check("bookmarks_list", "bookmark_card_b", atEnd = false)
    }

    private fun checkList(tag: String) {
        val list = ui.onNodeWithTag(tag)
        list.performScrollToIndex(99).performTouchInput { swipeUp(durationMillis = 60) }
        list.assertIsDisplayed()
        list.performScrollToIndex(1).performTouchInput { swipeDown(durationMillis = 100) }
        list.assertIsDisplayed()
        list.performScrollToIndex(0).performTouchInput {
            down(center)
            moveTo(percentOffset(.5f, .35f), 60)
            moveTo(percentOffset(.5f, .9f), 80)
            up()
        }
        list.assertIsDisplayed()
        list.performTouchInput { swipeDown(durationMillis = 140) }
        ui.onNodeWithTag("toc_sheet").assertDoesNotExist()
        ui.onNodeWithTag("open_sheet").performTouchInput { click() }
        ui.onNodeWithTag("toc_list").assertIsDisplayed()
    }
}
