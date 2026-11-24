package com.example.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import com.example.model.Book
import com.example.model.BookLayout
import com.example.model.LayoutMode
import com.example.model.PageTurnMode
import com.example.model.ReaderPreferences
import com.example.model.ReaderTheme
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

private val settings = stringPreferencesKey("typography")
private val sortKey = stringPreferencesKey("shelf-sort")
private val listKey = booleanPreferencesKey("shelf-list")
private val gridKey = intPreferencesKey("shelf-grid-columns")
private const val POSITION_DEBOUNCE_MS = 350L

class LocalLibrary internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope + Dispatchers.IO) {
        context.applicationContext.preferencesDataStoreFile("epub_library")
    },
    databaseName: String = LibraryDatabase.DEFAULT_NAME,
) {
    private class PendingPosition(val index: Int, val title: String, val fraction: Float, val locator: Locator) {
        val time = System.currentTimeMillis()
        var timer: Job? = null
    }

    private val app = context.applicationContext
    private val files = EpubBooks(app)
    private val db = LibraryDatabase(app, databaseName)
    private val fileLock = Mutex()
    // Mode changes and edits must choose their scope before another layout action can run.
    private val layoutLock = Mutex()
    private val pending = mutableMapOf<String, PendingPosition>()

    private fun <T> observe(changes: Flow<Long>, query: LibraryDatabase.() -> T) =
        changes.map { withDb(query) }.distinctUntilChanged()

    val books = observe(db.shelfChanges) { getActiveBooks(files) }
    fun observeBook(id: String) = observe(db.shelfChanges) { getActiveBook(id, files) }
    fun observeBookmarks(id: String) = observe(db.bookmarkChanges) { getBookmarks(id) }
    suspend fun description(id: String) = withDb { description(id) }

    val preferences = store.data.map { decodePreferences(it[settings]) }.distinctUntilChanged()
    val gridColumns = store.data.map { (it[gridKey] ?: 2).coerceIn(2, 4) }.distinctUntilChanged()
    val shelfOrder = store.data.map { it[sortKey] }.distinctUntilChanged()
    val listLayout = store.data.map { it[listKey] ?: false }.distinctUntilChanged()

    fun layout(id: String) = combine(
        preferences,
        observe(db.layoutChanges) { getBookLayout(id) },
    ) { shared, stored ->
        val mode = stored?.first ?: LayoutMode.GLOBAL
        val active = if (mode == LayoutMode.CUSTOM) stored?.second ?: shared else shared
        BookLayout(mode, active)
    }.distinctUntilChanged()

    suspend fun setLayoutMode(id: String, mode: LayoutMode): Unit = layoutLock.withLock {
        val (current, custom) = withDb { getBookLayout(id) } ?: return@withLock
        if (current == mode) return@withLock
        val initial = if (mode == LayoutMode.CUSTOM && custom == null) preferences.first() else null
        withDb { setLayoutMode(id, mode, initial) }
    }

    suspend fun editLayout(id: String, reset: Boolean = false, change: (ReaderPreferences) -> ReaderPreferences): Unit = layoutLock.withLock {
        val (mode, custom) = withDb { getBookLayout(id) } ?: return@withLock
        when (mode) {
            LayoutMode.ORIGINAL -> Unit
            LayoutMode.GLOBAL -> editGlobalPreferences { if (reset) ReaderPreferences.Default else change(it) }
            LayoutMode.CUSTOM -> {
                val next = if (reset) preferences.first() else change(custom ?: preferences.first())
                if (next != custom) withDb { setCustomPreferences(id, next) }
            }
        }
    }

    suspend fun stepGridColumns(step: Int) { store.edit { it[gridKey] = ((it[gridKey] ?: 2) + step).coerceIn(2, 4) } }
    suspend fun sortBy(name: String) { store.edit { it[sortKey] = name } }
    suspend fun toggleLayout() { store.edit { it[listKey] = !(it[listKey] ?: false) } }
    suspend fun updatePreferences(change: (ReaderPreferences) -> ReaderPreferences) =
        layoutLock.withLock { editGlobalPreferences(change) }

    private suspend fun editGlobalPreferences(change: (ReaderPreferences) -> ReaderPreferences) {
        store.edit { data ->
            val current = decodePreferences(data[settings])
            val next = change(current)
            if (next != current) data[settings] = encodePreferences(next)
        }
    }

    suspend fun import(uri: Uri): Pair<Book, Boolean> = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val temporary = File.createTempFile("import-", ".epub", files.directory)
            try {
                val id = files.copyAndIdentify(uri, temporary)
                val (exists, token) = db.getTrashToken(id)
                val target = files.file(id)
                if (exists && token != LibraryDatabase.IMPORTING && target.isFile) {
                    if (token != null) db.setTrashToken(id, null)
                    return@withLock requireNotNull(db.getActiveBook(id, files)) to (token != null)
                }

                val existingFile = target.isFile
                if (!exists) db.reserveImport(id)
                try {
                    if (!existingFile && !temporary.renameTo(target)) throw IOException("无法保存书籍文件")
                    publishImport(id, uri, target)
                } catch (error: Exception) {
                    if (db.getTrashToken(id).second == LibraryDatabase.IMPORTING) {
                        if (!exists && existingFile) db.deleteTrashedBook(id, LibraryDatabase.IMPORTING)
                        else purgeBook(id, LibraryDatabase.IMPORTING)
                    }
                    throw error
                }
                requireNotNull(db.getActiveBook(id, files)) to true
            } finally { temporary.delete() }
        }
    }

    private suspend fun publishImport(id: String, uri: Uri, file: File) {
        val publication = files.open(file)
        try {
            val metadata = publication.metadata
            db.publishBook(
                id = id,
                title = metadata.title?.takeIf(String::isNotBlank)
                    ?: files.displayName(uri).substringBeforeLast('.').ifBlank { "未命名书籍" },
                author = metadata.authors.joinToString("、") { it.name }.ifBlank { "未知作者" },
                publisher = metadata.publishers.joinToString("、") { it.name },
                description = metadata.description.orEmpty(),
                hasCover = files.saveCover(publication, id),
                fileSize = file.length(),
            )
        } finally { publication.close() }
    }

    internal suspend fun open(id: String, preloadPositions: Boolean = true): Publication {
        require(id.matches(Regex("[0-9a-f]{64}"))) { "无效的书籍编号" }
        var opening: Publication? = null
        try {
            return withContext(Dispatchers.IO) {
                files.open(files.file(id)).also {
                    opening = it
                    // The Fragment constructor requests positions synchronously.
                    if (preloadPositions) it.positionsByReadingOrder()
                }
            }
        } catch (error: Throwable) {
            opening?.close()
            throw error
        }
    }

    suspend fun lastLocator(id: String): Locator? =
        synchronized(pending) { pending[id]?.locator } ?: withDb { getLastLocator(id) }

    fun recordPosition(id: String, index: Int, title: String, fraction: Float, locator: Locator) {
        val entry = PendingPosition(index, title, fraction, locator)
        synchronized(pending) {
            pending.put(id, entry)?.timer?.cancel()
            entry.timer = scope.launch {
                delay(POSITION_DEBOUNCE_MS)
                withDbLocked { persistPosition(id) }
            }
        }
    }

    fun flushPosition(id: String) {
        if (synchronized(pending) { id in pending }) scope.launch { withDbLocked { persistPosition(id) } }
    }

    internal suspend fun savePosition(id: String, index: Int, title: String, fraction: Float, locator: Locator) {
        val entry = PendingPosition(index, title, fraction, locator)
        synchronized(pending) { pending.put(id, entry)?.timer?.cancel() }
        withDbLocked { persistPosition(id) }
    }

    // All writes for a book and its removal are serialized. Keep newer input visible during a commit.
    private fun persistPosition(id: String) {
        val entry = synchronized(pending) { pending[id] } ?: return
        db.savePosition(id, entry.index, entry.title, entry.fraction, entry.locator, entry.time)
        synchronized(pending) { if (pending[id] === entry) pending.remove(id)?.timer?.cancel() }
    }

    suspend fun bookmark(id: String, index: Int, title: String, percent: Int, locator: Locator, toggle: Boolean): Boolean {
        val createdAt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date())
        return withDb { toggleBookmark(id, index, title, percent, locator, createdAt, toggle) }
    }

    suspend fun removeBookmark(bookId: String, bookmarkId: String) {
        withDb { removeBookmark(bookId, bookmarkId) }
    }

    suspend fun removeBook(id: String): String = withDbLocked {
        persistPosition(id)
        UUID.randomUUID().toString().also { setTrashToken(id, it) }
    }

    suspend fun endRemoval(id: String, token: String, undo: Boolean) = withDbLocked {
        if (getTrashToken(id).second == token) {
            if (undo) setTrashToken(id, null) else purgeBook(id, token)
        }
    }

    private fun purgeBook(id: String, token: String) {
        synchronized(pending) { pending.remove(id)?.timer?.cancel() }
        for (file in listOf(files.file(id), files.cover(id))) {
            if (file.exists() && !file.delete()) throw IOException("无法移除书籍文件")
        }
        db.deleteTrashedBook(id, token)
    }

    suspend fun recover() = withDbLocked {
        trashedBooks().forEach { (id, token) -> runCatching { purgeBook(id, token) } }
        files.directory.listFiles { file -> file.name.startsWith("import-") }.orEmpty().forEach(File::delete)
    }

    internal suspend fun checkpoint() { withDb { this.checkpoint() } }

    internal fun close() {
        synchronized(pending) {
            pending.values.forEach { it.timer?.cancel() }
            pending.clear()
        }
        db.close()
    }

    private suspend fun <T> withDb(block: LibraryDatabase.() -> T): T = withContext(Dispatchers.IO) { db.block() }

    private suspend fun <T> withDbLocked(block: LibraryDatabase.() -> T): T =
        withContext(Dispatchers.IO) { fileLock.withLock { db.block() } }
}

internal fun encodePreferences(p: ReaderPreferences): String = JSONObject().apply {
    put("size", p.fontSizeSp.toDouble()); put("line", p.lineHeightMultiplier.toDouble())
    put("margin", p.horizontalMarginDp.toDouble()); put("theme", p.theme.name); put("mode", p.pageTurnMode.name)
}.toString()

internal fun decodePreferences(raw: String?): ReaderPreferences {
    val p = ReaderPreferences.Default
    val j = raw?.let { runCatching { JSONObject(it) }.getOrNull() } ?: return p
    return ReaderPreferences(
        j.optDouble("size", p.fontSizeSp.toDouble()).toFloat(),
        j.optDouble("line", p.lineHeightMultiplier.toDouble()).toFloat(),
        j.optDouble("margin", p.horizontalMarginDp.toDouble()).toFloat(),
        ReaderTheme.entries.find { it.name == j.optString("theme") } ?: p.theme,
        PageTurnMode.entries.find { it.name == j.optString("mode") } ?: p.pageTurnMode,
    )
}

internal fun samePlace(a: Locator?, b: Locator?): Boolean =
    a != null && b != null && a.href == b.href && a.locations.fragments == b.locations.fragments &&
        a.locations.position == b.locations.position &&
        kotlin.math.abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) < 0.000001
