package com.example.data

import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.text.format.Formatter
import androidx.core.content.contentValuesOf
import androidx.core.database.sqlite.transaction
import com.example.model.Book
import com.example.model.Bookmark
import com.example.model.LayoutMode
import com.example.model.ReaderPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator

private const val BOOK_COLUMNS =
    "id, title, author, publisher, has_cover, file_size, progress, current_chapter_index, current_chapter_title, last_read_at"

internal class LibraryDatabase(
    context: Context,
    name: String = DEFAULT_NAME,
) : SQLiteOpenHelper(context.applicationContext, name, null, 1) {

    private val app = context.applicationContext

    val shelfChanges = MutableStateFlow(0L)
    val bookmarkChanges = MutableStateFlow(0L)
    val layoutChanges = MutableStateFlow(0L)

    // Publish on the writer thread, immediately after a successful commit.
    private inline fun <T> write(
        vararg changes: MutableStateFlow<Long>,
        notify: (T) -> Boolean = { true },
        block: SQLiteDatabase.() -> T,
    ): T = writableDatabase.transaction(body = block).also { result ->
        if (notify(result)) changes.forEach { revision -> revision.update { it + 1 } }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE books (
                id TEXT PRIMARY KEY NOT NULL,
                title TEXT NOT NULL,
                author TEXT NOT NULL,
                publisher TEXT NOT NULL DEFAULT '',
                description TEXT NOT NULL DEFAULT '',
                has_cover INTEGER NOT NULL DEFAULT 0,
                added_at INTEGER NOT NULL,
                file_size INTEGER NOT NULL DEFAULT 0,
                progress REAL NOT NULL DEFAULT 0.0,
                current_chapter_index INTEGER NOT NULL DEFAULT 0,
                current_chapter_title TEXT NOT NULL DEFAULT '',
                last_read_at INTEGER NOT NULL,
                locator_json TEXT,
                layout_mode TEXT NOT NULL DEFAULT 'ORIGINAL',
                custom_prefs_json TEXT,
                trash_token TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_books_active_read ON books(trash_token, last_read_at DESC)")
        db.execSQL(
            """
            CREATE TABLE bookmarks (
                id TEXT PRIMARY KEY NOT NULL,
                book_id TEXT NOT NULL,
                chapter_index INTEGER NOT NULL,
                chapter_title TEXT NOT NULL,
                percent INTEGER NOT NULL,
                excerpt TEXT NOT NULL DEFAULT '',
                created_at TEXT NOT NULL,
                locator_json TEXT,
                FOREIGN KEY(book_id) REFERENCES books(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_bookmarks_book_id ON bookmarks(book_id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    fun getActiveBooks(files: EpubBooks): List<Book> =
        readableDatabase.rawQuery("SELECT $BOOK_COLUMNS FROM books WHERE trash_token IS NULL", null).use { c ->
            List(c.count) { c.moveToNext(); c.toBook(files) }
        }

    fun getActiveBook(id: String, files: EpubBooks): Book? =
        readableDatabase.rawQuery("SELECT $BOOK_COLUMNS FROM books WHERE id = ? AND trash_token IS NULL", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.toBook(files) else null
        }

    fun getTrashToken(id: String): Pair<Boolean, String?> =
        readableDatabase.rawQuery("SELECT trash_token FROM books WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) true to c.getStringOrNull(0) else false to null
        }

    // Scalar reads bypass CursorWindow, including for multi-megabyte descriptions.
    fun description(id: String): String =
        readableDatabase.compileStatement("SELECT coalesce((SELECT description FROM books WHERE id = ?), '')").use {
            it.bindString(1, id)
            it.simpleQueryForString()
        }

    fun reserveImport(id: String) {
        val now = System.currentTimeMillis()
        writableDatabase.insertOrThrow("books", null, contentValuesOf(
            "id" to id, "title" to "", "author" to "", "added_at" to now, "last_read_at" to now,
            "trash_token" to IMPORTING,
        ))
    }

    fun trashedBooks(): Map<String, String> =
        readableDatabase.rawQuery("SELECT id, trash_token FROM books WHERE trash_token IS NOT NULL", null).use { c ->
            buildMap { while (c.moveToNext()) put(c.getString(0), c.getString(1)) }
        }

    fun publishBook(
        id: String,
        title: String,
        author: String,
        publisher: String,
        description: String,
        hasCover: Boolean,
        fileSize: Long,
    ) = write(shelfChanges, layoutChanges, notify = { it > 0 }) {
        val values = contentValuesOf(
            "title" to title,
            "author" to author,
            "publisher" to publisher,
            "description" to description,
            "has_cover" to if (hasCover) 1 else 0,
            "file_size" to fileSize,
            "trash_token" to null,
        )
        update("books", values, "id = ?", arrayOf(id))
    }

    fun setTrashToken(id: String, token: String?) = write(shelfChanges, notify = { it > 0 }) {
        update("books", contentValuesOf("trash_token" to token), "id = ?", arrayOf(id))
    }

    fun deleteTrashedBook(id: String, token: String) = write(shelfChanges, bookmarkChanges, layoutChanges, notify = { it > 0 }) {
        delete("books", "id = ? AND trash_token = ?", arrayOf(id, token))
    }

    fun getLastLocator(id: String): Locator? =
        readableDatabase.rawQuery("SELECT locator_json FROM books WHERE id = ?", arrayOf(id)).use { c ->
            if (c.moveToFirst()) c.getStringOrNull(0)?.toLocatorOrNull() else null
        }

    fun savePosition(id: String, index: Int, title: String, fraction: Float, locator: Locator, timestamp: Long) =
        write(shelfChanges, notify = { it > 0 }) {
            update(
                "books",
                contentValuesOf(
                    "progress" to fraction.toDouble(),
                    "current_chapter_index" to index,
                    "current_chapter_title" to title,
                    "last_read_at" to timestamp,
                    "locator_json" to locator.toJSON().toString(),
                ),
                "id = ? AND trash_token IS NULL",
                arrayOf(id),
            )
        }

    fun getBookLayout(id: String): Pair<LayoutMode, ReaderPreferences?>? =
        readableDatabase.rawQuery("SELECT layout_mode, custom_prefs_json FROM books WHERE id = ?", arrayOf(id)).use { c ->
            if (!c.moveToFirst()) return null
            val mode = LayoutMode.entries.find { it.name == c.getStringOrNull(0) } ?: LayoutMode.GLOBAL
            mode to c.getStringOrNull(1)?.let(::decodePreferences)
        }

    fun setLayoutMode(id: String, mode: LayoutMode, initialCustom: ReaderPreferences?) =
        write(layoutChanges, notify = { it > 0 }) {
            val values = contentValuesOf("layout_mode" to mode.name)
            if (initialCustom != null) values.put("custom_prefs_json", encodePreferences(initialCustom))
            update("books", values, "id = ? AND layout_mode != ?", arrayOf(id, mode.name))
        }

    fun setCustomPreferences(id: String, preferences: ReaderPreferences) =
        write(layoutChanges, notify = { it > 0 }) {
            update("books", contentValuesOf("custom_prefs_json" to encodePreferences(preferences)),
                "id = ? AND layout_mode = ?", arrayOf(id, LayoutMode.CUSTOM.name))
        }

    fun getBookmarks(bookId: String): List<Bookmark> =
        readableDatabase.rawQuery(
            "SELECT id, chapter_index, chapter_title, percent, excerpt, created_at, locator_json FROM bookmarks WHERE book_id = ? ORDER BY rowid ASC",
            arrayOf(bookId),
        ).use { c ->
            List(c.count) {
                c.moveToNext()
                Bookmark(
                    id = c.getString(0), bookId = bookId, chapterIndex = c.getInt(1), chapterTitle = c.getString(2),
                    progressPercent = c.getInt(3), excerpt = c.getString(4), createdAtLabel = c.getString(5),
                    locator = c.getStringOrNull(6)?.toLocatorOrNull(),
                )
            }
        }

    fun toggleBookmark(
        bookId: String,
        chapterIndex: Int,
        chapterTitle: String,
        percent: Int,
        locator: Locator,
        createdAt: String,
        toggle: Boolean,
    ): Boolean {
        var changed = false
        return write(bookmarkChanges, notify = { changed }) {
            val active = rawQuery("SELECT 1 FROM books WHERE id = ? AND trash_token IS NULL", arrayOf(bookId)).use { it.moveToFirst() }
            if (!active) return@write false
            val existingId = rawQuery("SELECT id, locator_json FROM bookmarks WHERE book_id = ?", arrayOf(bookId)).use { c ->
                var found: String? = null
                while (c.moveToNext()) {
                    if (samePlace(c.getStringOrNull(1)?.toLocatorOrNull(), locator)) {
                        found = c.getString(0)
                        break
                    }
                }
                found
            }
            when {
                existingId != null && toggle -> {
                    delete("bookmarks", "id = ?", arrayOf(existingId))
                    changed = true
                    false
                }
                existingId == null -> {
                    insertOrThrow(
                        "bookmarks", null,
                        contentValuesOf(
                            "id" to UUID.randomUUID().toString(), "book_id" to bookId, "chapter_index" to chapterIndex,
                            "chapter_title" to chapterTitle, "percent" to percent, "excerpt" to locator.text.highlight.orEmpty(),
                            "created_at" to createdAt, "locator_json" to locator.toJSON().toString(),
                        ),
                    )
                    changed = true
                    true
                }
                else -> true
            }
        }
    }

    fun removeBookmark(bookId: String, bookmarkId: String) = write(bookmarkChanges, notify = { it > 0 }) {
        delete("bookmarks", "book_id = ? AND id = ?", arrayOf(bookId, bookmarkId))
    }

    fun checkpoint() {
        writableDatabase.rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null).use { it.moveToFirst() }
    }

    private fun Cursor.toBook(files: EpubBooks): Book {
        val id = getString(0)
        return Book(
            id = id, title = getString(1), author = getString(2), publisher = getString(3),
            coverPath = if (getInt(4) != 0) files.cover(id).path else null,
            fileSizeLabel = Formatter.formatShortFileSize(app, getLong(5)),
            progress = getDouble(6).toFloat(), currentChapterIndex = getInt(7),
            currentChapterTitle = getString(8), lastReadTimestamp = getLong(9),
        )
    }

    private fun Cursor.getStringOrNull(index: Int): String? = if (isNull(index)) null else getString(index)
    private fun String.toLocatorOrNull(): Locator? = runCatching { Locator.fromJSON(JSONObject(this)) }.getOrNull()

    companion object {
        const val IMPORTING = "import"
        const val DEFAULT_NAME = "epub_library.db"
    }
}
