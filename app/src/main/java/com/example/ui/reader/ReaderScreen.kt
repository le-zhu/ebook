package com.example.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import com.example.ui.EbookTextButton
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.ui.BookmarkNotice
import com.example.ui.theme.ReaderColorPalette
import com.example.ui.theme.SystemBarsAppearanceEffect
import com.example.ui.theme.appColorScheme
import com.example.ui.theme.resolveReaderPalette
import kotlinx.coroutines.launch

/**
 * Full-screen immersive EPUB Reader screen.
 *
 * Renders the publication in [readerContent] underneath the existing reading panels.
 */
@Composable
fun ReaderScreen(
    viewModel: ReaderViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    readerContent: @Composable (Modifier, ReaderUiState, ReaderColorPalette) -> Unit,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    val isSystemDark = isSystemInDarkTheme()
    val colorScheme = MaterialTheme.colorScheme
    val palette = remember(uiState.preferences.theme, isSystemDark) {
        resolveReaderPalette(uiState.preferences.theme, isSystemDark)
    }

    val panel = uiState.panel
    SystemBarsAppearanceEffect(
        isDarkBackground = palette.isDark,
        hideSystemBars = !uiState.controlsVisible && panel == null,
    )

    val book = uiState.book
    val chapterTitle = uiState.currentChapterTitle.orEmpty()

    uiState.error?.let { message ->
        Column(
            modifier.fillMaxSize(), verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(message)
            EbookTextButton("返回书架", onNavigateBack)
        }
        return
    }
    if (book == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        return
    }

    fun showQuickMessage(resId: Int) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val message = context.getString(resId)
            when (resId) {
                R.string.reader_bookmark_added, R.string.reader_bookmark_removed ->
                    snackbarHostState.showSnackbar(BookmarkNotice(message))
                else -> snackbarHostState.showSnackbar(message)
            }
        }
    }

    BackHandler(enabled = uiState.canReturnFromSearch && panel == null) {
        viewModel.returnFromSearch()
    }
    val readingColors = if (palette.isDark == isSystemDark) colorScheme else appColorScheme(palette.isDark)
    MaterialTheme(colorScheme = readingColors) {
        ReaderChromeScaffold(
            bookTitle = book.title,
            chapterTitle = chapterTitle,
            overallProgress = uiState.previewProgress ?: uiState.overallProgress,
            isBookmarked = uiState.isCurrentChapterBookmarked,
            controlsVisible = uiState.controlsVisible,
            canGoPreviousChapter = uiState.previousChapter != null,
            canGoNextChapter = uiState.nextChapter != null,
            palette = palette,
            snackbarHostState = if (panel == ReaderPanel.TOC) null else snackbarHostState,
            onNavigateBack = onNavigateBack,
            onOpenSearch = { viewModel.openPanel(ReaderPanel.SEARCH) },
            onReturnFromSearch = if (uiState.canReturnFromSearch) viewModel::returnFromSearch else null,
            onStayAtSearchResult = viewModel::finishSearchVisit,
            onOpenToc = { viewModel.openPanel(ReaderPanel.TOC) },
            onToggleBookmark = {
                coroutineScope.launch {
                    viewModel.bookmark(toggle = true)?.let { added ->
                        showQuickMessage(if (added) R.string.reader_bookmark_added else R.string.reader_bookmark_removed)
                    }
                }
            },
            onPreviousChapter = {
                if (!viewModel.goToPreviousChapter()) {
                    showQuickMessage(R.string.reader_first_chapter_hint)
                }
            },
            onNextChapter = {
                if (!viewModel.goToNextChapter()) {
                    showQuickMessage(R.string.reader_last_chapter_hint)
                }
            },
            onSeekPreview = viewModel::previewSeek,
            onSeekFinished = viewModel::finishSeek,
            onOpenSettings = { viewModel.openPanel(ReaderPanel.SETTINGS) },
            readerContent = { slotModifier -> readerContent(slotModifier, uiState, palette) },
            modifier = modifier,
        )

        if (panel == null) return@MaterialTheme
        val dismiss = { viewModel.dismissPanel(panel) }
        when (panel) {
            ReaderPanel.SETTINGS -> TypographySettingsSheet(
                preferences = uiState.preferences,
                mode = uiState.layoutMode,
                onModeChange = viewModel::setLayoutMode,
                onDismiss = dismiss,
                onThemeSelected = viewModel::setReaderTheme,
                onPageTurnModeSelected = viewModel::setPageTurnMode,
                onFontSizeChange = viewModel::setFontSize,
                onLineHeightChange = viewModel::setLineHeight,
                onHorizontalMarginChange = viewModel::setHorizontalMargin,
                onResetClick = viewModel::resetPreferences,
            )
            ReaderPanel.SEARCH -> {
                val searchState by viewModel.search.state.collectAsStateWithLifecycle()
                BookSearchSheet(searchState, viewModel::searchChapter, viewModel.search::edit,
                    viewModel::submitSearch, viewModel.search::more, viewModel::readSearchHit, dismiss)
            }
            ReaderPanel.TOC -> TocAndBookmarksSheet(
                book = book,
                activeTocItemId = uiState.section?.id,
                bookmarks = uiState.bookmarks,
                snackbarHostState = snackbarHostState,
                onDismiss = dismiss,
                onSelectChapter = viewModel::selectToc,
                onSelectBookmark = viewModel::selectBookmark,
                onAddBookmarkHere = {
                    coroutineScope.launch { if (viewModel.bookmark(toggle = false) == true) showQuickMessage(R.string.reader_bookmark_added) }
                },
                onDeleteBookmark = { bookmarkId ->
                    viewModel.removeBookmark(bookmarkId)
                    showQuickMessage(R.string.reader_bookmark_removed)
                },
            )
        }
    }
}
