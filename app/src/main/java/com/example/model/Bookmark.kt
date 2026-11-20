package com.example.model

import androidx.compose.runtime.Immutable
import org.readium.r2.shared.publication.Locator

@Immutable
data class Bookmark(
    val id: String,
    val bookId: String,
    val chapterIndex: Int,
    val chapterTitle: String,
    val progressPercent: Int,
    val excerpt: String,
    val createdAtLabel: String,
    val locator: Locator? = null,
)
