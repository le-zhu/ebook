package com.example.ui.bookshelf

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.automirrored.rounded.LibraryBooks
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.model.Book
import com.example.ui.EbookSnackbarHost
import com.example.ui.EbookActionButton
import com.example.ui.EqualButtonGroup
import com.example.ui.EbookBadge
import com.example.ui.EbookBottomSheet
import com.example.ui.SheetFixedContent
import com.example.ui.SheetScrollBody
import com.example.ui.theme.SystemBarsAppearanceEffect
import com.example.ui.theme.TallControlHeight
import com.example.ui.theme.FloatingPanelElevation
import kotlinx.coroutines.launch

private val shelfContentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookshelfScreen(
    viewModel: BookshelfViewModel,
    onOpenBook: (String) -> Unit,
    onImport: () -> Unit,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var sortMenuExpanded by remember { mutableStateOf(false) }

    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()

    SystemBarsAppearanceEffect(
        isDarkBackground = isSystemInDarkTheme(),
        hideSystemBars = false,
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        contentWindowInsets = WindowInsets.safeDrawing,
        snackbarHost = { EbookSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                expandedHeight = TallControlHeight,
                modifier = Modifier.shadow(FloatingPanelElevation, clip = false),
                title = {
                    Text(
                        text = stringResource(R.string.bookshelf_title),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
                actions = {
                    IconButton(onClick = onImport) {
                        Icon(Icons.Rounded.Add, contentDescription = stringResource(R.string.bookshelf_import_folder))
                    }
                    Box {
                        IconButton(
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.testTag("bookshelf_sort_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Sort,
                                contentDescription = stringResource(R.string.bookshelf_sort_cd),
                            )
                        }
                        SortDropdownMenu(
                            expanded = sortMenuExpanded,
                            currentSort = uiState.sortOrder,
                            onDismiss = { sortMenuExpanded = false },
                            onSelectSort = { order ->
                                viewModel.setSortOrder(order)
                                sortMenuExpanded = false
                            },
                        )
                    }

                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.testTag("bookshelf_toggle_view_button"),
                    ) {
                        val isGrid = uiState.viewMode == BookshelfViewMode.GRID
                        Icon(
                            imageVector = if (isGrid) {
                                Icons.AutoMirrored.Rounded.ViewList
                            } else {
                                Icons.Rounded.GridView
                            },
                            contentDescription = stringResource(
                                if (isGrid) {
                                    R.string.bookshelf_toggle_view_list_cd
                                } else {
                                    R.string.bookshelf_toggle_view_grid_cd
                                },
                            ),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (uiState.books.isEmpty()) {
            BookshelfEmptyState(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            )
        } else {
            AnimatedContent(
                targetState = uiState.viewMode,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "bookshelf_view_mode_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) { mode ->
                when (mode) {
                    BookshelfViewMode.GRID -> {
                        val compact = uiState.columns > 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(uiState.columns),
                            state = gridState,
                            contentPadding = shelfContentPadding,
                            horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 14.dp),
                            verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 16.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .pointerInput(viewModel) { detectColumnPinches(viewModel::stepGridColumns) }
                                .testTag("bookshelf_grid"),
                        ) {
                            items(
                                items = uiState.books,
                                key = { it.id },
                            ) { book ->
                                BookGridCard(
                                    book = book,
                                    columns = uiState.columns,
                                    onClick = { onOpenBook(book.id) },
                                    onLongClick = { viewModel.openBookDetailSheet(book.id) },
                                )
                            }
                        }
                    }

                    BookshelfViewMode.LIST -> {
                        LazyColumn(
                            state = listState,
                            contentPadding = shelfContentPadding,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("bookshelf_list"),
                        ) {
                            items(
                                items = uiState.books,
                                key = { it.id },
                            ) { book ->
                                BookListCard(
                                    book = book,
                                    onClick = { onOpenBook(book.id) },
                                    onLongClick = { viewModel.openBookDetailSheet(book.id) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    uiState.selectedBookForSheet?.let { selectedBook ->
        BookDetailBottomSheet(
            book = selectedBook,
            onDismiss = { viewModel.dismissBookDetailSheet() },
            onReadClick = {
                viewModel.dismissBookDetailSheet()
                onOpenBook(selectedBook.id)
            },
            onRemoveClick = {
                viewModel.dismissBookDetailSheet()
                coroutineScope.launch {
                    val token = viewModel.removeBook(selectedBook.id)
                    var undo = false
                    try {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        undo = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.bookshelf_removed_snackbar, selectedBook.title),
                            actionLabel = context.getString(R.string.bookshelf_undo),
                            duration = SnackbarDuration.Short,
                        ) == SnackbarResult.ActionPerformed
                    } finally {
                        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable) {
                            viewModel.endRemoval(selectedBook.id, token, undo)
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(tag: String, onClick: () -> Unit, onLongClick: () -> Unit, content: @Composable () -> Unit) {
    val haptic = LocalHapticFeedback.current
    val cardShape = MaterialTheme.shapes.medium
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(cardShape)
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
            )
            .testTag(tag),
        shape = cardShape,
        color = MaterialTheme.colorScheme.surface,
        content = content,
    )
}

@Composable
private fun BookGridCard(
    book: Book,
    columns: Int,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val compact = columns > 2
    val coverInset = if (compact) 4.dp else 8.dp
    BookCard("book_grid_card_${book.id}", onClick, onLongClick) {
        Column(Modifier.padding(horizontal = if (columns == 3) 2.dp else 0.dp)) {
            BookCoverArtwork(
                book = book,
                compact = compact,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = coverInset, end = coverInset, top = coverInset)
                    .aspectRatio(2f / 3f)
                    .testTag("book_grid_cover"),
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 6.dp else 12.dp, vertical = if (compact) 6.dp else 10.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(8.dp))
                BookProgressRow(book, shortLabel = true)
            }
        }
    }
}

@Composable
private fun BookListCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    BookCard("book_list_card_${book.id}", onClick, onLongClick) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BookCoverArtwork(
                book = book,
                compact = true,
                modifier = Modifier
                    .width(64.dp)
                    .aspectRatio(2f / 3f),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${book.author} · ${book.publisher}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = book.currentChapterTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                BookProgressRow(book, shortLabel = false)
            }
        }
    }
}

@Composable
private fun BookProgressRow(book: Book, shortLabel: Boolean) {
    val percent = book.progressPercent
    val label = when {
        percent == 0 -> stringResource(R.string.bookshelf_unread)
        shortLabel -> "$percent%"
        percent >= 100 -> stringResource(R.string.bookshelf_finished)
        else -> stringResource(R.string.bookshelf_reading_progress, percent)
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (shortLabel) 8.dp else 10.dp),
    ) {
        BookProgressBar(book.progress, Modifier.weight(1f))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BookProgressBar(progress: Float, modifier: Modifier) {
    LinearProgressIndicator(
        progress = { progress.coerceIn(0f, 1f) },
        modifier = modifier.height(4.dp).clip(CircleShape),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.secondaryContainer,
        strokeCap = StrokeCap.Round,
        drawStopIndicator = {},
    )
}

@Composable
private fun SortDropdownMenu(
    expanded: Boolean,
    currentSort: BookshelfSortOrder,
    onDismiss: () -> Unit,
    onSelectSort: (BookshelfSortOrder) -> Unit,
) {
    val options = listOf(
        BookshelfSortOrder.RECENTLY_READ to R.string.bookshelf_sort_recent,
        BookshelfSortOrder.TITLE to R.string.bookshelf_sort_title,
        BookshelfSortOrder.PROGRESS to R.string.bookshelf_sort_progress,
        BookshelfSortOrder.AUTHOR to R.string.bookshelf_sort_author,
    )
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("bookshelf_sort_menu"),
        shape = MaterialTheme.shapes.medium,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = FloatingPanelElevation,
    ) {
        options.forEach { (order, labelRes) ->
            val isCurrent = order == currentSort
            DropdownMenuItem(
                text = {
                    Text(
                        text = stringResource(labelRes),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (isCurrent) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                onClick = { onSelectSort(order) },
                modifier = Modifier
                    .semantics { selected = isCurrent }
                    .testTag("sort_option_${order.name.lowercase()}"),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BookDetailBottomSheet(
    book: Book,
    onDismiss: () -> Unit,
    onReadClick: () -> Unit,
    onRemoveClick: () -> Unit,
) {
    EbookBottomSheet(onDismiss, Modifier.testTag("book_detail_sheet").padding(bottom = 24.dp)) {
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f, fill = false)) {
            // Reserve space for the synopsis even when metadata is tall in landscape or large type.
            val sectionLimit = maxHeight / 2
            Column {
                SheetFixedContent {
                    BookDetailHeader(book, Modifier.heightIn(max = sectionLimit))
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = stringResource(R.string.book_detail_synopsis),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                val synopsis = remember(book.description) { synopsisText(book.description) }
                SheetScrollBody(
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.heightIn(max = sectionLimit).testTag("book_detail_synopsis_viewport"),
                ) {
                    Text(
                        text = synopsis.ifBlank { stringResource(R.string.book_detail_no_synopsis) },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        SheetFixedContent {
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            EqualButtonGroup {
                EbookActionButton(
                    label = stringResource(R.string.book_detail_remove),
                    icon = Icons.Rounded.DeleteOutline,
                    onClick = onRemoveClick,
                    modifier = Modifier.testTag("book_detail_remove_button"),
                    destructive = true,
                )

                EbookActionButton(
                    label = stringResource(if (book.progressPercent > 0) R.string.book_detail_continue_reading else R.string.book_detail_start_reading),
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    onClick = onReadClick,
                    modifier = Modifier.testTag("book_detail_read_button"),
                )
            }
        }
    }
}

@Composable
private fun BookDetailHeader(book: Book, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        BookCoverArtwork(book, compact = true, modifier = Modifier.widthIn(max = 86.dp).aspectRatio(2f / 3f))
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = book.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // In a short window the metadata can overflow, while the cover and title stay pinned.
            Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState(), overscrollEffect = null)) {
                Spacer(Modifier.height(4.dp))
                Text(book.author, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EbookBadge(book.fileSizeLabel)
                    EbookBadge("EPUB")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "${stringResource(R.string.book_detail_publisher)}：${book.publisher}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun BookshelfEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.size(TallControlHeight),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.LibraryBooks,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.bookshelf_empty_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.bookshelf_empty_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
