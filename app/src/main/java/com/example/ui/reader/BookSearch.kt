@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)
package com.example.ui.reader

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.search.search
import org.readium.r2.shared.util.getOrElse

internal data class HitPage(val hits: List<Locator>, val hasMore: Boolean)

internal fun searchPages(publication: Publication, term: String, pageSize: Int = 50) = flow {
    val iterator = publication.search(term) ?: throw IOException("这本书不支持全文搜索")
    try {
        val page = ArrayList<Locator>(pageSize)
        while (true) {
            val found = iterator.next().getOrElse { throw IOException("无法读取书中部分章节") } ?: break
            for (hit in found.locators) {
                if (page.size == pageSize) {
                    emit(HitPage(page.toList(), true))
                    page.clear()
                }
                page.add(hit)
            }
        }
        emit(HitPage(page.toList(), false))
    } finally { iterator.close() }
}.flowOn(Dispatchers.IO).buffer(0)

internal data class SearchState(
    val query: String = "",
    val submitted: Boolean = false,
    val loading: Boolean = false,
    val hasMore: Boolean = false,
    val hits: List<Locator> = emptyList(),
    val error: String? = null,
)

internal class BookSearch(private val scope: CoroutineScope) {
    private val mutable = MutableStateFlow(SearchState())
    val state = mutable.asStateFlow()
    private var job: Job? = null
    private var nextPage: Channel<Unit>? = null

    fun edit(value: String) {
        if (value == mutable.value.query) return
        job?.cancel()
        mutable.value = SearchState(query = value)
    }

    fun start(openBook: suspend () -> Publication) {
        val term = mutable.value.query.trim()
        if (term.isEmpty()) return
        job?.cancel()
        val more = Channel<Unit>(Channel.CONFLATED)
        nextPage = more
        mutable.value = SearchState(query = term, submitted = true, loading = true)
        job = scope.launch {
            var publication: Publication? = null
            try {
                val book = openBook()
                publication = book
                searchPages(book, term).collect { page ->
                    mutable.update { it.copy(hits = it.hits + page.hits, loading = false, hasMore = page.hasMore) }
                    if (page.hasMore) more.receive()
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(loading = false, hasMore = false, error = e.message ?: "搜索失败") } }
            finally { publication?.close(); more.close() }
        }
    }

    fun more() {
        if (mutable.value.hasMore && !mutable.value.loading) {
            mutable.update { it.copy(loading = true) }
            nextPage?.trySend(Unit)
        }
    }

    fun dismiss() {
        if (mutable.value.loading) {
            job?.cancel()
            mutable.update { SearchState(query = it.query) }
        }
    }
}
