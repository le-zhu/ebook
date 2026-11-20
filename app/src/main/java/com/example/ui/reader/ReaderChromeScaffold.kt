package com.example.ui.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.FormatListBulleted
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.TextFormat
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.rounded.Search
import com.example.ui.EbookTextButton
import com.example.R
import com.example.ui.EbookActionButton
import com.example.ui.EbookSnackbarHost
import com.example.ui.theme.ReaderColorPalette
import com.example.ui.theme.TallControlHeight
import com.example.ui.theme.FloatingPanelElevation
import kotlin.math.roundToInt

@Composable
fun ReaderChromeScaffold(
    bookTitle: String,
    chapterTitle: String,
    overallProgress: Float,
    isBookmarked: Boolean,
    controlsVisible: Boolean,
    canGoPreviousChapter: Boolean,
    canGoNextChapter: Boolean,
    palette: ReaderColorPalette,
    snackbarHostState: SnackbarHostState?,
    onNavigateBack: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSearch: () -> Unit,
    onReturnFromSearch: (() -> Unit)?,
    onStayAtSearchResult: () -> Unit,
    onToggleBookmark: () -> Unit,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onOpenSettings: () -> Unit,
    readerContent: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
) {
    val controls = MaterialTheme.colorScheme
    val animatedBackground by animateColorAsState(
        targetValue = palette.background,
        animationSpec = tween(durationMillis = 260),
        label = "reader_bg_color",
    )
    val animatedChromeSurface by animateColorAsState(
        targetValue = controls.surface,
        animationSpec = tween(durationMillis = 240),
        label = "reader_chrome_surface",
    )
    val animatedChromeText by animateColorAsState(
        targetValue = controls.onSurface,
        animationSpec = tween(durationMillis = 240),
        label = "reader_chrome_text",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(animatedBackground),
    ) {
        // Reading viewport underneath the overlays
        readerContent(Modifier.fillMaxSize())

        // Material 3 Immersive Top Bar Overlay
        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically(tween(220)) { -it },
            exit = slideOutVertically(tween(180)) { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Surface(
                color = animatedChromeSurface,
                contentColor = animatedChromeText,
                shadowElevation = FloatingPanelElevation,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("reader_top_bar"),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .statusBarsPadding()
                            .heightIn(min = TallControlHeight)
                            .padding(horizontal = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("reader_back_button"),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.reader_back_cd),
                                tint = animatedChromeText,
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 6.dp),
                        ) {
                            Text(
                                text = bookTitle,
                                style = MaterialTheme.typography.titleMedium,
                                color = animatedChromeText,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (chapterTitle.isNotBlank()) Text(
                                text = chapterTitle,
                                style = MaterialTheme.typography.labelMedium,
                                color = controls.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(onClick = onOpenSearch, modifier = Modifier.testTag("reader_search")) {
                            Icon(Icons.Rounded.Search, contentDescription = "全书搜索", tint = animatedChromeText)
                        }
                        IconButton(
                            onClick = onToggleBookmark,
                            modifier = Modifier.testTag("reader_bookmark_toggle"),
                        ) {
                            Icon(
                                imageVector = if (isBookmarked) {
                                    Icons.Rounded.Bookmark
                                } else {
                                    Icons.Rounded.BookmarkBorder
                                },
                                contentDescription = stringResource(
                                    if (isBookmarked) {
                                        R.string.reader_bookmark_remove_cd
                                    } else {
                                        R.string.reader_bookmark_add_cd
                                    },
                                ),
                                tint = if (isBookmarked) controls.primary else animatedChromeText,
                            )
                        }
                    }
                    if (onReturnFromSearch != null) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            EbookTextButton("返回原阅读位置", onReturnFromSearch, Modifier.testTag("return_from_search"))
                            Spacer(Modifier.weight(1f))
                            IconButton(onClick = onStayAtSearchResult, modifier = Modifier.testTag("stay_at_search_result")) {
                                Icon(Icons.Rounded.Close, contentDescription = "留在当前位置并清除高亮")
                            }
                        }
                    }
                }
            }
        }

        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (snackbarHostState != null) {
                EbookSnackbarHost(snackbarHostState,
                    Modifier.padding(horizontal = 16.dp).padding(bottom = if (controlsVisible) 0.dp else 20.dp))
            }
            AnimatedVisibility(
                visible = controlsVisible,
                enter = slideInVertically(tween(220)) { it },
                exit = slideOutVertically(tween(180)) { it },
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                FloatingReaderControlCapsule(
                    chapterTitle = chapterTitle,
                    overallProgress = overallProgress,
                    canGoPreviousChapter = canGoPreviousChapter,
                    canGoNextChapter = canGoNextChapter,
                    surfaceColor = animatedChromeSurface,
                    contentColor = animatedChromeText,
                    onPreviousChapter = onPreviousChapter,
                    onNextChapter = onNextChapter,
                    onSeekPreview = onSeekPreview,
                    onSeekFinished = onSeekFinished,
                    onOpenToc = onOpenToc,
                    onOpenSettings = onOpenSettings,
                )
            }
        }
    }
}

@Composable
private fun FloatingReaderControlCapsule(
    chapterTitle: String,
    overallProgress: Float,
    canGoPreviousChapter: Boolean,
    canGoNextChapter: Boolean,
    surfaceColor: Color,
    contentColor: Color,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    onSeekPreview: (Float) -> Unit,
    onSeekFinished: () -> Unit,
    onOpenToc: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val controls = MaterialTheme.colorScheme
    val progress = overallProgress.coerceIn(0f, 1f)
    val chapterArrowColors = IconButtonDefaults.iconButtonColors(
        contentColor = contentColor,
        disabledContentColor = contentColor.copy(alpha = 0.38f),
    )

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = surfaceColor,
        contentColor = contentColor,
        shadowElevation = FloatingPanelElevation,
        modifier = Modifier
            .widthIn(max = 560.dp)
            .fillMaxWidth()
            .testTag("reader_bottom_capsule"),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            // Chapter name & live progress percentage
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = chapterTitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = controls.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = controls.primary,
                )
            }

            // Prev chapter + Slider + Next chapter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onPreviousChapter,
                    enabled = canGoPreviousChapter,
                    colors = chapterArrowColors,
                    modifier = Modifier.testTag("reader_prev_chapter_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateBefore,
                        contentDescription = stringResource(R.string.reader_prev_chapter),
                    )
                }

                Slider(
                    value = progress,
                    onValueChange = onSeekPreview,
                    onValueChangeFinished = onSeekFinished,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reader_progress_slider"),
                )

                IconButton(
                    onClick = onNextChapter,
                    enabled = canGoNextChapter,
                    colors = chapterArrowColors,
                    modifier = Modifier.testTag("reader_next_chapter_button"),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.NavigateNext,
                        contentDescription = stringResource(R.string.reader_next_chapter),
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Secondary bottom actions row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EbookActionButton(
                    label = stringResource(R.string.reader_toc_label),
                    icon = Icons.AutoMirrored.Rounded.FormatListBulleted,
                    onClick = onOpenToc,
                    modifier = Modifier.testTag("reader_capsule_toc_button"),
                )
                EbookActionButton(
                    label = stringResource(R.string.reader_settings_button),
                    icon = Icons.Rounded.TextFormat,
                    onClick = onOpenSettings,
                    iconModifier = Modifier.offset(y = 1.5.dp).size(22.dp),
                    modifier = Modifier.testTag("reader_settings_button"),
                )
            }
        }
    }
}
