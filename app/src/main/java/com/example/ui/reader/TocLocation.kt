@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package com.example.ui.reader

import com.example.model.Book
import com.example.model.TocItem
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.html.cssSelector
import org.readium.r2.shared.util.Url

internal class ReadingLocations(val book: Book, publication: Publication) {
    val files: List<TocLocation>

    init {
        val itemsByFile = book.navigationTargets.groupBy { it.chapterIndex }
        var lastItem: TocItem? = null
        files = publication.readingOrder.mapIndexed { index, link ->
            val items = itemsByFile[index].orEmpty()
            val location = TocLocation(items, link.url(), lastItem)
            lastItem = items.lastOrNull() ?: lastItem
            location
        }
    }
}

internal class TocLocation(items: List<TocItem>, resource: Url, preceding: TocItem? = null) {
    private val sections = mutableListOf<TocItem>()
    private val anchors = mutableListOf<String>()
    val chapter: TocItem?
    val hasAnchors get() = anchors.isNotEmpty()

    init {
        val file = resource.removeFragment().removeQuery()
        var start: TocItem? = null
        for (item in items) {
            val url = item.href?.let { Url(it) } ?: continue
            if (!url.removeFragment().removeQuery().isEquivalent(file)) continue
            val fragment = url.fragment
            if (fragment != null) {
                anchors += fragment
                sections += item
            } else if (start == null) start = item
        }
        chapter = start ?: preceding
    }

    fun explicit(place: Locator): TocItem? {
        val fragment = place.href.fragment
        return sections.getOrNull(anchors.indexOfLast { it == fragment || it in place.locations.fragments })
    }

    suspend fun visible(nav: EpubNavigatorFragment, place: Locator): TocItem? {
        if (sections.isEmpty()) return chapter
        val first = nav.firstVisibleElementLocator() ?: return explicit(place) ?: chapter
        if (!first.href.removeFragment().isEquivalent(place.href.removeFragment())) return chapter
        val selector = first.locations.cssSelector ?: return explicit(first) ?: explicit(place) ?: chapter
        val index = nav.evaluateJavascript(sectionScript(selector, anchors))?.toIntOrNull()
        return index?.let(sections::getOrNull) ?: explicit(first) ?: chapter
    }

    companion object {
        // Compare EPUB anchors to the first visible reading element in DOM order.
        // This works for both paginated (including RTL) and scrolled layouts.
        internal fun sectionScript(selector: String, anchors: List<String>): String = """
            (() => {
                const first = document.querySelector(${JSONObject.quote(selector)});
                if (!first || first === document.body || first === document.documentElement) return -1;
                const ids = ${JSONArray(anchors)};
                let best = null, index = -1;
                ids.forEach((id, i) => {
                    const node = document.getElementById(id) || document.getElementsByName(id)[0];
                    if (!node) return;
                    let before = node === first || (node.compareDocumentPosition(first) & Node.DOCUMENT_POSITION_FOLLOWING);
                    if (!before && first.contains(node)) {
                        // Many EPUBs put an empty anchor just inside the chapter heading.
                        const prefix = document.createRange();
                        prefix.setStart(first, 0);
                        prefix.setEndBefore(node);
                        before = !prefix.toString().trim();
                    }
                    if (!before) return;
                    if (!best || node === best || (best.compareDocumentPosition(node) & Node.DOCUMENT_POSITION_FOLLOWING)) {
                        best = node; index = i;
                    }
                });
                return index;
            })()
        """.trimIndent()
    }
}
