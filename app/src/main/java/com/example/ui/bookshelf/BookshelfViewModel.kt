package com.example.ui.bookshelf

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LocalLibrary
import com.example.model.Book
import com.example.ui.publicationText
import java.text.Collator
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

enum class BookshelfSortOrder {
    RECENTLY_READ,
    TITLE,
    PROGRESS,
    AUTHOR,
}

enum class BookshelfViewMode {
    GRID,
    LIST,
}

@Immutable
data class BookshelfUiState(
    val books: List<Book> = emptyList(),
    val sortOrder: BookshelfSortOrder = BookshelfSortOrder.RECENTLY_READ,
    val viewMode: BookshelfViewMode = BookshelfViewMode.GRID,
    val columns: Int = 2,
    val selectedBookForSheet: Book? = null,
    val synopsis: String = "",
)

class BookshelfViewModel(
    private val repository: LocalLibrary,
) : ViewModel() {

    private val sortOrder = repository.shelfOrder.map { name ->
        BookshelfSortOrder.entries.find { it.name == name } ?: BookshelfSortOrder.RECENTLY_READ
    }
    private val viewMode = repository.listLayout.map { if (it) BookshelfViewMode.LIST else BookshelfViewMode.GRID }
    private val selectedBookId = MutableStateFlow<String?>(null)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val selectedDetails = selectedBookId.mapLatest { id ->
        id?.let { it to publicationText(repository.description(it)) }
    }.flowOn(Dispatchers.Default)

    private val sortedShelf = combine(repository.books, sortOrder) { books, sort ->
        val collator = Collator.getInstance(Locale.CHINA)
        val byTitle = compareBy<Book, String>(collator) { it.title }.thenByDescending { it.importedAt }
        val order = when (sort) {
            BookshelfSortOrder.RECENTLY_READ -> compareByDescending(Book::lastReadTimestamp).then(byTitle)
            BookshelfSortOrder.TITLE -> byTitle
            BookshelfSortOrder.PROGRESS -> compareByDescending(Book::progress).then(byTitle)
            BookshelfSortOrder.AUTHOR -> compareBy<Book, String>(collator) { it.author }.then(byTitle)
        }
        sort to books.sortedWith(order)
    }

    val uiState: StateFlow<BookshelfUiState> = combine(
        sortedShelf, viewMode, selectedDetails, repository.gridColumns,
    ) { (sort, books), mode, details, columns ->
        BookshelfUiState(
            books = books,
            columns = columns,
            sortOrder = sort,
            viewMode = mode,
            selectedBookForSheet = details?.let { (id, _) -> books.find { it.id == id } },
            synopsis = details?.second.orEmpty(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000L),
        initialValue = BookshelfUiState(),
    )

    fun setSortOrder(order: BookshelfSortOrder) {
        viewModelScope.launch { repository.sortBy(order.name) }
    }

    fun stepGridColumns(step: Int) {
        viewModelScope.launch { repository.stepGridColumns(step) }
    }

    fun toggleViewMode() {
        viewModelScope.launch { repository.toggleLayout() }
    }

    fun openBookDetailSheet(bookId: String) {
        selectedBookId.value = bookId
    }

    fun dismissBookDetailSheet() {
        selectedBookId.value = null
    }

    suspend fun removeBook(id: String) = repository.removeBook(id)
    suspend fun endRemoval(id: String, token: String, undo: Boolean) = repository.endRemoval(id, token, undo)

    companion object {
        fun provideFactory(repository: LocalLibrary): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return BookshelfViewModel(repository) as T
                }
            }
    }
}
