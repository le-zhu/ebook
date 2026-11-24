package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Core domain model representing an EPUB book on the local bookshelf.
 */
@Immutable
data class Book(
    val id: String,
    val title: String,
    val author: String,
    val publisher: String,
    val progress: Float,
    val currentChapterIndex: Int,
    val lastReadTimestamp: Long?,
    val importedAt: Long,
    val fileSizeLabel: String,
    val toc: List<TocItem> = emptyList(),
    val coverPath: String? = null,
    val currentChapterTitle: String = "",
) {
    val tocEntries = toc.flattenToc()
    val navigationTargets = tocEntries.filter { it.chapterIndex != null }.distinctBy { it.href }
    private val targetIndices = navigationTargets.mapIndexed { index, item -> item.href to index }.toMap()

    fun adjacentTarget(current: TocItem?, offset: Int): TocItem? =
        navigationTargets.getOrNull((targetIndices[current?.href] ?: -1) + offset)

    val hasReadingPosition: Boolean get() = lastReadTimestamp != null

    val progressPercent: Int
        get() = (progress.coerceIn(0f, 1f) * 100f).toInt()
}
