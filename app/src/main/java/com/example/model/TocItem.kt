package com.example.model

import androidx.compose.runtime.Immutable

/**
 * Represents a hierarchical Table of Contents (TOC) entry.
 * Mirrors Readium's Link tree structure (`children`) for clean future mapping.
 */
@Immutable
data class TocItem(
    val id: String,
    val title: String,
    val chapterIndex: Int?,
    val level: Int = 0,
    val children: List<TocItem> = emptyList(),
    val href: String? = null,
) {
    val destination: TocItem?
        get() = if (chapterIndex != null) this else children.firstNotNullOfOrNull { it.destination }
}

/**
 * Flattens nested TOC trees into display order while preserving each node's indentation [TocItem.level].
 */
fun List<TocItem>.flattenToc(expanded: Set<String>? = null): List<TocItem> {
    val result = mutableListOf<TocItem>()
    fun traverse(items: List<TocItem>) {
        for (item in items) {
            result.add(item)
            if (expanded == null || item.id in expanded) traverse(item.children)
        }
    }
    traverse(this)
    return result
}
