package com.example.ui.bookshelf

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.LocalLibrary
import com.example.model.Book
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
)

class BookshelfViewModel(
    private val repository: LocalLibrary,
) : ViewModel() {

    private val sortOrder = repository.shelfOrder.map { name ->
        BookshelfSortOrder.entries.find { it.name == name } ?: BookshelfSortOrder.RECENTLY_READ
    }
    private val viewMode = repository.listLayout.map { if (it) BookshelfViewMode.LIST else BookshelfViewMode.GRID }
    private val selectedBookId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<BookshelfUiState> = combine(
        repository.books,
        sortOrder,
        viewMode,
        selectedBookId,
        repository.gridColumns,
    ) { books, sort, mode, activeSheetBookId, columns ->
        val sorted = when (sort) {
            BookshelfSortOrder.RECENTLY_READ -> books.sortedByDescending { it.lastReadTimestamp }
            BookshelfSortOrder.TITLE -> books.sortedBy { it.title }
            BookshelfSortOrder.PROGRESS -> books.sortedByDescending { it.progress }
            BookshelfSortOrder.AUTHOR -> books.sortedBy { it.author }
        }
        BookshelfUiState(
            books = sorted,
            columns = columns,
            sortOrder = sort,
            viewMode = mode,
            selectedBookForSheet = books.find { it.id == activeSheetBookId },
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
