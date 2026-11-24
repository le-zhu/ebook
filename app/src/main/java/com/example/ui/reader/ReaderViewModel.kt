package com.example.ui.reader

import androidx.lifecycle.ViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LocalLibrary
import com.example.data.samePlace
import com.example.data.withContents
import com.example.model.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONObject
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.shared.publication.*
import org.readium.r2.shared.publication.services.locateProgression
import org.readium.r2.shared.util.Url

enum class ReaderPanel { TOC, SEARCH, SETTINGS }

data class ReaderUiState(
    val book: Book? = null,
    val currentChapterIndex: Int = 0,
    val section: TocItem? = null,
    val overallProgress: Float = 0f,
    val previewProgress: Float? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val isCurrentChapterBookmarked: Boolean = false,
    val layoutMode: LayoutMode = LayoutMode.ORIGINAL,
    val panel: ReaderPanel? = null,
    val canReturnFromSearch: Boolean = false,
    val searchHighlight: Locator? = null,
    val preferences: ReaderPreferences = ReaderPreferences.Default,
    val controlsVisible: Boolean = true,
    val error: String? = null,
) {
    val currentChapterTitle get() = section?.title
    val progressPercent get() = (overallProgress * 100).toInt()
    val previousChapter get() = book?.adjacentTarget(section, -1)
    val nextChapter get() = book?.adjacentTarget(section, 1)
}

class ReaderViewModel(private val id: String, private val library: LocalLibrary, private val saved: SavedStateHandle) : ViewModel() {
    private val state = MutableStateFlow(ReaderUiState(canReturnFromSearch = saved.contains("searchOrigin")))
    val uiState = state.asStateFlow()
    internal var publication: Publication? = null
        private set
    internal var locator: Locator? = null
        private set
    private var navigator: EpubNavigatorFragment? = null
    private var seekJob: Job? = null
    private var seekTarget: Locator? = null
    private var landing: Pair<Locator, Float>? = null
    internal val awaitingSeek get() = seekTarget != null
    private var sectionJob: Job? = null
    private var locations: ReadingLocations? = null
    internal val search = BookSearch(viewModelScope)

    init {
        viewModelScope.launch {
            try {
                val book = library.observeBook(id).first() ?: error("书架中找不到这本书")
                val initial = library.lastLocator(id)
                val pub = library.open(id)
                publication = pub
                locator = initial
                val indexByFile = withContext(Dispatchers.IO) { ReadingLocations(book.withContents(pub), pub) }
                locations = indexByFile
                val withContents = indexByFile.book
                val index = initial?.let { pub.readingOrder.indexOfFirstWithHref(it.href) }
                    ?: book.currentChapterIndex.coerceIn(pub.readingOrder.indices)
                val page = indexByFile.files[index]
                val layout = library.layout(id).first()
                state.update { it.copy(book = withContents, preferences = layout.preferences, layoutMode = layout.mode,
                    currentChapterIndex = index, section = initial?.let(page::explicit) ?: page.chapter,
                    overallProgress = book.progress) }
                library.layout(id).collect { next ->
                    state.update { it.copy(preferences = next.preferences, layoutMode = next.mode) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { state.update { it.copy(error = e.message ?: "无法打开书籍") } }
        }
        viewModelScope.launch { library.observeBookmarks(id).collect { marks ->
            state.update { it.copy(bookmarks = marks, isCurrentChapterBookmarked = matches(marks, locator)) }
        } }
    }

    private fun matches(marks: List<Bookmark>, place: Locator?) = marks.any {
        samePlace(it.locator, place)
    }

    internal fun attach(nav: EpubNavigatorFragment) { navigator = nav }
    internal fun detach(nav: EpubNavigatorFragment) {
        if (navigator === nav) {
            cancelSeek()
            sectionJob?.cancel()
            library.flushPosition(id)
            navigator = null
        }
    }

    internal fun moved(place: Locator, pageCount: Int? = null) {
        val pub = publication ?: return
        val index = pub.readingOrder.indexOfFirstWithHref(place.href) ?: return
        val page = locations?.files?.getOrNull(index) ?: return
        val target = seekTarget
        if (target != null) {
            if (!reachedSeekPage(target, place, pageCount, pub.metadata.layout == Layout.FIXED)) return
            state.value.previewProgress?.let { landing = place to it }
            seekJob?.cancel()
            seekTarget = null
        } else if (seekJob?.isActive == true) {
            // The destination is still being resolved. Ignore the previous page.
            return
        }
        if (!samePlace(landing?.first, place)) landing = null
        val fraction = landing?.second ?: (place.locations.totalProgression ?:
            (index + (place.locations.progression ?: 0.0)) / pub.readingOrder.size).coerceIn(0.0, 1.0).toFloat()
        locator = place
        val explicit = page.explicit(place)
        val section = explicit ?: state.value.section?.takeIf { state.value.currentChapterIndex == index } ?: page.chapter
        state.update { it.copy(currentChapterIndex = index, section = section, overallProgress = fraction,
            previewProgress = if (target != null) null else it.previewProgress,
            isCurrentChapterBookmarked = matches(it.bookmarks, place)) }
        library.recordPosition(id, index, section?.title.orEmpty(), fraction, place)
        sectionJob?.cancel()
        val nav = navigator ?: return
        if (page.hasAnchors && explicit == null) sectionJob = viewModelScope.launch {
            val resolved = page.visible(nav, place)
            if (navigator !== nav || locator != place) return@launch
            if (resolved != state.value.section) {
                state.update { it.copy(section = resolved) }
                library.recordPosition(id, index, resolved?.title.orEmpty(), fraction, place)
            }
        }
    }

    fun selectToc(item: TocItem): Boolean {
        val target = item.destination ?: return false
        return goToTarget(target.chapterIndex, target.href)
    }

    private fun goToTarget(index: Int?, href: String?): Boolean {
        if (index == null) return false
        cancelSeek()
        val pub = publication ?: return false
        val base = pub.readingOrder.getOrNull(index) ?: return false
        val url = href?.let { Url(it) }
        val target = if (url != null && pub.readingOrder.indexOfFirstWithHref(url) == index)
            base.copy(href = Href(url)) else base
        return navigator?.go(target, false) == true
    }

    fun selectBookmark(mark: Bookmark) {
        cancelSeek()
        val place = mark.locator
        if (place != null) navigator?.go(place, false) else goToTarget(mark.chapterIndex, null)
    }

    fun goToPreviousChapter() = state.value.previousChapter?.let(::selectToc) == true
    fun goToNextChapter() = state.value.nextChapter?.let(::selectToc) == true

    fun previewSeek(value: Float) {
        seekJob?.cancel()
        seekTarget = null
        state.update { it.copy(previewProgress = value.coerceIn(0f, 1f)) }
    }

    fun finishSeek() {
        val requested = state.value.previewProgress ?: return
        val nav = navigator ?: return cancelSeek()
        val pub = publication ?: return cancelSeek()
        seekJob?.cancel()
        seekJob = viewModelScope.launch {
            val target = try {
                pub.locateProgression(requested.toDouble())
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) { null }
            seekTarget = target
            if (target != null && nav.go(target, false)) {
                // A same-page jump may not produce a new StateFlow emission. The
                // pagination callback can acknowledge it too. Recover on load failure.
                delay(8000)
            }
            cancelSeek()
            moved(nav.currentLocator.value)
        }
    }

    internal fun readingSurfaceTouched() { if (state.value.previewProgress != null) cancelSeek() }

    private fun cancelSeek() {
        seekJob?.cancel()
        seekJob = null
        seekTarget = null
        state.update { it.copy(previewProgress = null) }
    }

    suspend fun bookmark(toggle: Boolean): Boolean? {
        val place = locator ?: return null
        val s = state.value
        return library.bookmark(id, s.currentChapterIndex, s.currentChapterTitle ?: place.title.orEmpty(),
            s.progressPercent, place, toggle)
    }

    fun removeBookmark(key: String) { viewModelScope.launch { library.removeBookmark(id, key) } }
    fun toggleControls() { state.update { it.copy(controlsVisible = !it.controlsVisible) } }
    fun openPanel(panel: ReaderPanel) {
        if (state.value.panel == ReaderPanel.SEARCH && panel != ReaderPanel.SEARCH) search.dismiss()
        state.update { it.copy(panel = panel, controlsVisible = true) }
    }

    fun dismissPanel(panel: ReaderPanel) {
        if (state.value.panel != panel) return
        if (panel == ReaderPanel.SEARCH) search.dismiss()
        state.update { it.copy(panel = null) }
    }
    fun searchChapter(hit: Locator): String {
        val index = publication?.readingOrder?.indexOfFirstWithHref(hit.href)
        val page = index?.let { locations?.files?.getOrNull(it) }
        return page?.explicit(hit)?.title ?: hit.title?.takeIf { it.isNotBlank() } ?: page?.chapter?.title.orEmpty()
    }

    fun submitSearch() = search.start { library.open(id, preloadPositions = false) }

    fun readSearchHit(hit: Locator) {
        val nav = navigator ?: return
        val origin = nav.currentLocator.value
        cancelSeek()
        if (nav.go(hit, false)) {
            rememberReturnOrigin(origin)
            dismissPanel(ReaderPanel.SEARCH)
            state.update { it.copy(canReturnFromSearch = true, searchHighlight = hit, controlsVisible = true) }
        }
    }

    internal fun followInternalLink(link: Link): Boolean {
        val nav = navigator ?: return false
        val origin = nav.currentLocator.value
        cancelSeek()
        if (!nav.go(link, false)) return false
        rememberReturnOrigin(origin)
        state.update { it.copy(canReturnFromSearch = true, searchHighlight = null, controlsVisible = true) }
        return true
    }

    private fun rememberReturnOrigin(origin: Locator) {
        if (!saved.contains("searchOrigin")) saved["searchOrigin"] = origin.toJSON().toString()
    }

    fun returnFromSearch() {
        val origin = saved.get<String>("searchOrigin")?.let { Locator.fromJSON(JSONObject(it)) } ?: return
        cancelSeek()
        if (navigator?.go(origin, false) == true) finishSearchVisit()
    }

    fun finishSearchVisit() {
        saved.remove<String>("searchOrigin")
        state.update { it.copy(canReturnFromSearch = false, searchHighlight = null) }
    }
    private fun settings(change: (ReaderPreferences) -> ReaderPreferences) {
        viewModelScope.launch { library.editLayout(id, change = change) }
    }
    fun setFontSize(v: Float) = settings { it.copy(fontSizeSp = v.coerceIn(ReaderPreferences.MIN_FONT_SIZE_SP, ReaderPreferences.MAX_FONT_SIZE_SP)) }
    fun setLineHeight(v: Float) = settings { it.copy(lineHeightMultiplier = v.coerceIn(ReaderPreferences.MIN_LINE_HEIGHT, ReaderPreferences.MAX_LINE_HEIGHT)) }
    fun setHorizontalMargin(v: Float) = settings { it.copy(horizontalMarginDp = v.coerceIn(ReaderPreferences.MIN_MARGIN_DP, ReaderPreferences.MAX_MARGIN_DP)) }
    fun setReaderTheme(v: ReaderTheme) = settings { it.copy(theme = v) }
    fun setPageTurnMode(v: PageTurnMode) = settings { it.copy(pageTurnMode = v) }
    fun setLayoutMode(mode: LayoutMode) { viewModelScope.launch { library.setLayoutMode(id, mode) } }
    fun resetPreferences() { viewModelScope.launch { library.editLayout(id, reset = true) { it } } }

    override fun onCleared() {
        library.flushPosition(id)
        publication?.close()
    }

    companion object {
        fun provideFactory(bookId: String, repository: LocalLibrary) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                ReaderViewModel(bookId, repository, extras.createSavedStateHandle()) as T
        }
    }
}

// Pagination rounds a requested fraction to the start of its rendered page.
// A notification from the old chapter (or a transient chapter-start offset)
// must not move the thumb while the requested location is loading.
internal fun reachedSeekPage(target: Locator, actual: Locator, pageCount: Int?, fixed: Boolean): Boolean {
    if (!target.href.removeFragment().isEquivalent(actual.href.removeFragment())) return false
    if (fixed) return true
    val expected = target.locations.progression ?: return true
    val observed = actual.locations.progression ?: return false
    val tolerance = if (pageCount != null && pageCount > 0) 1.0 / pageCount else 0.0
    return kotlin.math.abs(expected - observed) <= tolerance + 0.001
}
