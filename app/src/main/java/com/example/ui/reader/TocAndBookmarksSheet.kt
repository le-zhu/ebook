package com.example.ui.reader

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    var expanded by rememberSaveable(book.id, activeTocItemId) {
        mutableStateOf(ArrayList(book.tocEntries.filter { activeTocItemId?.startsWith(it.id + ".") == true }.map { it.id }))
    }

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
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text(stringResource(R.string.sheet_tab_toc)) },
                    modifier = Modifier.testTag("sheet_tab_toc"),
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = {
                        val label = stringResource(R.string.sheet_tab_bookmarks)
                        Text(if (bookmarks.isEmpty()) label else "$label (${bookmarks.size})")
                    },
                    modifier = Modifier.testTag("sheet_tab_bookmarks"),
                )
            }

            Box(Modifier.fillMaxWidth().weight(1f)) {
                when (selectedTab) {
                    0 -> TocList(book.toc, expanded, activeTocItemId, onToggle = { id ->
                        expanded = ArrayList(if (id in expanded) expanded - id else expanded + id)
                    }) { item ->
                        onSelectChapter(item)
                        onDismiss()
                    }
                    1 -> BookmarksTab(bookmarks, onAddBookmarkHere, onDeleteBookmark) { bookmark ->
                        onSelectBookmark(bookmark)
                        onDismiss()
                    }
                }
                EbookSnackbarHost(snackbarHostState, Modifier.align(Alignment.BottomCenter).padding(16.dp))
            }
        }
    }
}

@Composable
private fun TocList(
    roots: List<TocItem>,
    expanded: List<String>,
    activeId: String?,
    onToggle: (String) -> Unit,
    onSelect: (TocItem) -> Unit,
) {
    if (roots.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(stringResource(R.string.toc_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    val entries = remember(roots, expanded) { roots.flattenToc(expanded.toSet()) }
    val state = rememberLazyListState()
    LaunchedEffect(activeId) {
        val index = entries.indexOfFirst { it.id == activeId }
        if (index >= 0) state.scrollToItem(index)
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("toc_list"),
        state = state,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(entries, key = { it.id }) { item ->
            TocRowItem(item, isActive = item.id == activeId, expanded = item.id in expanded,
                onClick = { onSelect(item) }, onToggle = { onToggle(item.id) })
        }
    }
}

@Composable
private fun BookmarksTab(
    bookmarks: List<Bookmark>,
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
        val listArea = Modifier.fillMaxWidth().weight(1f)
        if (bookmarks.isEmpty()) {
            BookmarksEmptyState(modifier = listArea)
        } else {
            LazyColumn(
                modifier = listArea.testTag("bookmarks_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(bookmarks, key = { it.id }) { bookmark ->
                    BookmarkCard(bookmark, onClick = { onSelect(bookmark) }, onDelete = { onDelete(bookmark.id) })
                }
            }
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
        shape = MaterialTheme.shapes.medium,
        color = if (isActive) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(enabled = item.destination != null, onClick = onClick)
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
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
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
