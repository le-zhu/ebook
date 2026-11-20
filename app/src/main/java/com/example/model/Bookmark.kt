package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Represents a saved reading bookmark.
 * In Readium 3.x integration, [chapterIndex] and [progressPercent] can be paired
 * with a serialized Readium `Locator` JSON field.
 */
@Immutable
data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val progressPercent: Int,
    val excerpt: String,
    val createdAtLabel: String,
    val locatorJson: String? = null,
)
