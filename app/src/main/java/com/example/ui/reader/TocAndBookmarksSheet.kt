package com.example.ui.reader

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Velocity
import com.example.R
import com.example.ui.EbookActionButton
import com.example.ui.EbookBadge
import com.example.ui.layoutChoiceHeight
import com.example.ui.theme.ListSheetHeightFraction
import com.example.ui.theme.TallControlHeight
import com.example.ui.EbookBottomSheet
import com.example.ui.EbookSnackbarHost
import com.example.model.Book
import com.example.model.Bookmark
import com.example.model.flattenToc
import com.example.model.TocItem
import com.example.ui.theme.ReaderBodySerifFont
import kotlinx.coroutines.launch

/**
 * Material 3 ModalBottomSheet with PrimaryTabRow switching between multi-level Table of Contents
 * and saved Bookmarks (with jump, add-at-current-position, and delete actions).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TocAndBookmarksSheet(
    book: Book,
    activeTocItemId: String?,
    bookmarks: List<Bookmark>,
    onDismiss: () -> Unit,
    onSelectChapter: (TocItem) -> Unit,
    onSelectBookmark: (Bookmark) -> Unit,
    onAddBookmarkHere: () -> Unit,
    onDeleteBookmark: (String) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val pager = rememberPagerState { 2 }
    val scope = rememberCoroutineScope()
    fun selectPage(index: Int) { scope.launch { pager.animateScrollToPage(index) } }
    var expanded by rememberSaveable(book.id) {
        mutableStateOf(ArrayList(book.tocEntries.filter { activeTocItemId?.startsWith(it.id + ".") == true }.map { it.id }))
    }
    val entries = remember(book.toc, expanded) { book.toc.flattenToc(expanded.toSet()) }
    val tocListState = rememberLazyListState(
        initialFirstVisibleItemIndex = entries.indexOfFirst { it.id == activeTocItemId }.coerceAtLeast(0),
    )
    val bookmarkListState = rememberLazyListState()

    EbookBottomSheet(onDismiss) {
        Column(
            modifier = Modifier
                .testTag("toc_sheet")
                .fillMaxWidth()
                .fillMaxHeight(ListSheetHeightFraction)
                .navigationBarsPadding(),
        ) {
            // Book title summary header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            PrimaryTabRow(
                selectedTabIndex = pager.currentPage,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = pager.currentPage == 0,
                    onClick = { selectPage(0) },
                    text = { Text(stringResource(R.string.sheet_tab_toc)) },
                    modifier = Modifier.testTag("sheet_tab_toc"),
                )
                Tab(
                    selected = pager.currentPage == 1,
                    onClick = { selectPage(1) },
                    text = {
                        val label = stringResource(R.string.sheet_tab_bookmarks)
                        Text(if (bookmarks.isEmpty()) label else "$label (${bookmarks.size})")
                    },
                    modifier = Modifier.testTag("sheet_tab_bookmarks"),
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                HorizontalPager(state = pager, modifier = Modifier.fillMaxSize().testTag("reader_tabs_pager")) { page ->
                    if (page == 0) {
                        TocList(entries, tocListState, expanded, activeTocItemId, onToggle = { id ->
                            expanded = ArrayList(if (id in expanded) expanded - id else expanded + id)
                        }) { item ->
                            onSelectChapter(item)
                            onDismiss()
                        }
                    } else {
                        BookmarksTab(bookmarks, bookmarkListState, onAddBookmarkHere, onDeleteBookmark) { bookmark ->
                            onSelectBookmark(bookmark)
                            onDismiss()
                        }
                    }
                }
                EbookSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun TocList(
    entries: List<TocItem>,
    state: LazyListState,
    expanded: List<String>,
    activeId: String?,
    onToggle: (String) -> Unit,
    onSelect: (TocItem) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().sheetListGestures(state).testTag("toc_list"),
        state = state,
        overscrollEffect = null,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (entries.isEmpty()) item {
            Box(Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.toc_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        items(entries, key = { it.id }) { item ->
            TocRowItem(item, isActive = item.id == activeId, expanded = item.id in expanded,
                onClick = { onSelect(item) }, onToggle = { onToggle(item.id) })
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<Bookmark>,
    state: LazyListState,
    onAdd: () -> Unit,
    onDelete: (String) -> Unit,
    onSelect: (Bookmark) -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        EbookActionButton(
            label = stringResource(R.string.sheet_add_bookmark_here),
            icon = Icons.Rounded.BookmarkAdd,
            onClick = onAdd,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("sheet_add_bookmark_button"),
        )
        LazyColumn(
            state = state,
            overscrollEffect = null,
            modifier = Modifier.fillMaxWidth().weight(1f).sheetListGestures(state).testTag("bookmarks_list"),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (bookmarks.isEmpty()) item { BookmarksEmptyState(Modifier.fillParentMaxSize()) }
            items(bookmarks, key = { it.id }) { bookmark ->
                BookmarkCard(bookmark, onClick = { onSelect(bookmark) }, onDelete = { onDelete(bookmark.id) })
            }
        }
    }
}

// Let Material handle the drag only if this touch started at the top and first moved down.
// A scroll that reaches the edge midway, or reverses, belongs to the list until release.
@Composable
private fun Modifier.sheetListGestures(state: LazyListState): Modifier {
    val maxOffset = with(LocalDensity.current) { 16.dp.toPx() }
    var edgeOffset by remember { mutableFloatStateOf(0f) }
    val connection = remember(state, maxOffset) {
        object : NestedScrollConnection {
            var startedAtTop = false
            var pullDown = false

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (startedAtTop && source == NestedScrollSource.UserInput && available.y != 0f) {
                    pullDown = available.y > 0f
                    startedAtTop = false
                }
                if (edgeOffset * available.y < 0f) edgeOffset = 0f
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (pullDown || source != NestedScrollSource.UserInput) return Offset.Zero
                edgeOffset = (edgeOffset + available.y * 0.18f).coerceIn(-maxOffset, maxOffset)
                return Offset(0f, available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (pullDown) return Velocity.Zero
                if (edgeOffset != 0f || available.y != 0f) {
                    try {
                        // Cap the impulse too, so a fast fling only nudges the list.
                        animate(
                            initialValue = edgeOffset,
                            targetValue = 0f,
                            initialVelocity = available.y.coerceIn(-maxOffset * 32, maxOffset * 32),
                            animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        ) { value, _ ->
                            edgeOffset = value.coerceIn(-maxOffset, maxOffset)
                        }
                    } finally {
                        edgeOffset = 0f
                    }
                }
                return Velocity(0f, available.y)
            }
        }
    }
    return clipToBounds().graphicsLayer { translationY = edgeOffset }
        .nestedScroll(connection)
        .pointerInput(connection) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                connection.startedAtTop = !state.canScrollBackward
                connection.pullDown = false
            }
        }
}

@Composable
private fun TocRowItem(
    item: TocItem,
    isActive: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
) {
    val startIndent = (item.level.coerceAtMost(5) * 18).dp
    val isSectionHeader = item.level == 0 && item.children.isNotEmpty()

    Surface(
        onClick = onClick,
        enabled = item.destination != null,
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { selected = isActive }
            .testTag("toc_item_${item.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = layoutChoiceHeight())
                .padding(
                    start = 14.dp + startIndent,
                    end = if (item.children.isEmpty()) 14.dp else 4.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isActive) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
                Spacer(modifier = Modifier.width(10.dp))
            }

            Text(
                text = item.title,
                style = if (isSectionHeader) {
                    MaterialTheme.typography.titleSmall
                } else {
                    MaterialTheme.typography.bodyMedium
                },
                fontWeight = if (isActive || isSectionHeader) FontWeight.SemiBold else FontWeight.Normal,
                color = when {
                    isActive -> MaterialTheme.colorScheme.onSecondaryContainer
                    isSectionHeader -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(vertical = 10.dp),
            )

            if (item.children.isNotEmpty()) {
                IconButton(onClick = onToggle) {
                    Icon(
                        if (expanded) Icons.Rounded.ExpandMore else Icons.AutoMirrored.Rounded.NavigateNext,
                        contentDescription = stringResource(if (expanded) R.string.toc_collapse else R.string.toc_expand, item.title),
                    )
                }
            }
        }
    }
}

@Composable
private fun BookmarkCard(
    bookmark: Bookmark,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bookmark_card_${bookmark.id}"),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 6.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = bookmark.chapterTitle,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    EbookBadge("${bookmark.progressPercent}%")
                }

                if (bookmark.excerpt.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "“${bookmark.excerpt}…”",
                        fontFamily = ReaderBodySerifFont,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = bookmark.createdAtLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_bookmark_${bookmark.id}"),
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = stringResource(R.string.sheet_delete_bookmark_cd),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookmarksEmptyState(
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            modifier = Modifier.size(TallControlHeight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Rounded.BookmarkBorder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.sheet_bookmarks_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.sheet_bookmarks_empty_subtitle),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
