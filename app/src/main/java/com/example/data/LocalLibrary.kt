package com.example.data

import android.content.Context
import android.net.Uri
import android.text.format.Formatter
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import com.example.model.*
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.services.positionsByReadingOrder

private fun entry(id: String) = stringPreferencesKey("book:$id")
private fun position(id: String) = stringPreferencesKey("position:$id")
private fun marks(id: String) = stringPreferencesKey("marks:$id")
private fun trash(id: String) = stringPreferencesKey("trash:$id")
private fun layoutKey(id: String) = stringPreferencesKey("layout:$id")
private fun customKey(id: String) = stringPreferencesKey("custom:$id")
private val settings = stringPreferencesKey("typography")

class LocalLibrary internal constructor(
    context: Context,
    private val scope: CoroutineScope,
    private val store: DataStore<Preferences> = PreferenceDataStoreFactory.create(scope = scope + Dispatchers.IO) {
        context.applicationContext.preferencesDataStoreFile("epub_library")
    },
) {
    private val app = context.applicationContext
    private val files = EpubBooks(app)
    private val fileLock = Mutex()
    val books = store.data.map { data ->
        data.asMap().keys.filter { it.name.startsWith("book:") }
            .mapNotNull { book(data, it.name.removePrefix("book:")) }
    }.distinctUntilChanged()
    fun observeBook(id: String) = store.data.map { book(it, id) }.distinctUntilChanged()
    fun observeBookmarks(id: String) = store.data.map { it.bookmarks(id) }.distinctUntilChanged()
    val preferences = store.data.map { decodePreferences(it[settings]) }.distinctUntilChanged()

    fun layout(id: String) = store.data.map { data ->
        val mode = data.layoutMode(id)
        val shared = decodePreferences(data[settings])
        val active = if (mode == LayoutMode.CUSTOM) data[customKey(id)]?.let(::decodePreferences) ?: shared else shared
        BookLayout(mode, active)
    }.distinctUntilChanged()

    // Existing shelf entries used global typography before per-book modes were introduced.
    private fun Preferences.layoutMode(id: String) =
        LayoutMode.entries.find { it.name == this[layoutKey(id)] } ?: LayoutMode.GLOBAL

    suspend fun setLayoutMode(id: String, mode: LayoutMode) {
        store.edit { data ->
            if (data[entry(id)] == null) return@edit
            if (mode == LayoutMode.CUSTOM && data[customKey(id)] == null) {
                data[customKey(id)] = encodePreferences(decodePreferences(data[settings]))
            }
            data[layoutKey(id)] = mode.name
        }
    }

    suspend fun editLayout(id: String, reset: Boolean = false, change: (ReaderPreferences) -> ReaderPreferences) {
        store.edit { data ->
            if (data[entry(id)] == null) return@edit
            val key = when (data.layoutMode(id)) {
                LayoutMode.ORIGINAL -> return@edit
                LayoutMode.GLOBAL -> settings
                LayoutMode.CUSTOM -> customKey(id)
            }
            val shared = decodePreferences(data[settings])
            val value = if (reset) {
                if (key == settings) ReaderPreferences.Default else shared
            } else change(data[key]?.let(::decodePreferences) ?: shared)
            data[key] = encodePreferences(value)
        }
    }

    private val sortKey = stringPreferencesKey("shelf-sort")
    private val listKey = androidx.datastore.preferences.core.booleanPreferencesKey("shelf-list")
    private val gridKey = androidx.datastore.preferences.core.intPreferencesKey("shelf-grid-columns")
    val gridColumns = store.data.map { (it[gridKey] ?: 2).coerceIn(2, 4) }.distinctUntilChanged()
    suspend fun stepGridColumns(step: Int) {
        store.edit { it[gridKey] = ((it[gridKey] ?: 2) + step).coerceIn(2, 4) }
    }
    val shelfOrder = store.data.map { it[sortKey] }.distinctUntilChanged()
    val listLayout = store.data.map { it[listKey] ?: false }.distinctUntilChanged()
    suspend fun sortBy(name: String) { store.edit { it[sortKey] = name } }
    suspend fun toggleLayout() { store.edit { it[listKey] = !(it[listKey] ?: false) } }

    private fun book(data: Preferences, id: String): Book? {
        if (data[trash(id)] != null) return null
        val row = JSONObject(data[entry(id)] ?: return null)
        val last = data[position(id)]?.let(::JSONObject)
        val added = row.getLong("added")
        return Book(id = id, title = row.getString("title"), author = row.getString("author"),
            publisher = row.optString("publisher"), description = row.optString("description"),
            coverPath = if (row.optBoolean("cover")) files.cover(id).path else null,
            progress = last?.optDouble("fraction", 0.0)?.toFloat() ?: 0f,
            currentChapterIndex = last?.optInt("chapter") ?: 0,
            lastReadTimestamp = last?.optLong("time", added) ?: added,
            fileSizeLabel = Formatter.formatShortFileSize(app, row.getLong("size")),
            currentChapterTitle = last?.optString("title").orEmpty())
    }

    suspend fun import(uri: Uri): Pair<Book, Boolean> = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val temporary = File.createTempFile("import-", ".epub", files.directory)
            try {
                val id = files.copyAndIdentify(uri, temporary)
                val existing = store.data.first()
                val target = files.file(id)
                if (existing[entry(id)] != null && target.isFile) {
                    val restored = existing[trash(id)] != null
                    val updated = store.edit { it.remove(trash(id)) }
                    return@withLock requireNotNull(book(updated, id)) to restored
                }
                // Move the completed stream before opening the archive. The shelf entry is
                // published only after parsing succeeds; recover() removes interrupted orphans.
                if (!target.exists() && !temporary.renameTo(target)) throw IOException("无法保存书籍文件")
                try {
                    val pub = files.open(target)
                    val row = try {
                        val metadata = pub.metadata
                        JSONObject().apply {
                            put("title", metadata.title?.takeIf(String::isNotBlank)
                                ?: files.displayName(uri).substringBeforeLast('.').ifBlank { "未命名书籍" })
                            put("author", metadata.authors.joinToString("、") { it.name }.ifBlank { "未知作者" })
                            put("publisher", metadata.publishers.joinToString("、") { it.name })
                            put("description", metadata.description.orEmpty())
                            put("count", pub.readingOrder.size)
                            put("cover", files.saveCover(pub, id))
                            put("added", System.currentTimeMillis())
                            put("size", target.length())
                        }
                    } finally { pub.close() }
                    val updated = store.edit {
                        if (it[entry(id)] == null) it[layoutKey(id)] = LayoutMode.ORIGINAL.name
                        it[entry(id)] = row.toString()
                        it.remove(trash(id))
                    }
                    requireNotNull(book(updated, id)) to true
                } catch (error: Throwable) {
                    // Cancellation can arrive after DataStore has committed. Keep any
                    // file already referenced by the durable shelf, even in that case.
                    withContext(NonCancellable) {
                        val orphan = runCatching { store.data.first()[entry(id)] == null }.getOrDefault(false)
                        if (orphan) {
                            target.delete()
                            files.cover(id).delete()
                        }
                    }
                    throw error
                }
            } finally { temporary.delete() }
        }
    }

    internal suspend fun open(id: String): Publication {
        require(id.matches(Regex("[0-9a-f]{64}"))) { "无效的书籍编号" }
        var opening: Publication? = null
        try {
            return withContext(Dispatchers.IO) {
                files.open(files.file(id)).also {
                    opening = it
                    // The Fragment constructor requests positions synchronously.
                    it.positionsByReadingOrder()
                }
            }
        } catch (error: Throwable) {
            opening?.close()
            throw error
        }
    }

    suspend fun lastLocator(id: String): Locator? =
        store.data.first()[position(id)]?.let { Locator.fromJSON(JSONObject(it).optJSONObject("locator")) }

    fun recordPosition(id: String, index: Int, title: String, fraction: Float, locator: Locator) {
        scope.launch { savePosition(id, index, title, fraction, locator) }
    }

    internal suspend fun savePosition(id: String, index: Int, title: String, fraction: Float, locator: Locator) {
        store.edit { data ->
            if (data[entry(id)] != null && data[trash(id)] == null) {
                data[position(id)] = JSONObject().apply {
                    put("locator", locator.toJSON()); put("fraction", fraction.toDouble())
                    put("chapter", index); put("title", title); put("time", System.currentTimeMillis())
                }.toString()
            }
        }
    }

    private fun Preferences.bookmarks(id: String): List<Bookmark> {
        val list = JSONArray(this[marks(id)] ?: "[]")
        return (0 until list.length()).map { list.getJSONObject(it) }.map {
            Bookmark(it.getString("id"), id, it.getInt("chapter"), it.getString("title"),
                it.getInt("percent"), it.optString("excerpt"), it.getString("created"),
                it.getJSONObject("locator").toString())
        }
    }

    suspend fun bookmark(id: String, index: Int, title: String, percent: Int, locator: Locator, toggle: Boolean): Boolean {
        val json = locator.toJSON()
        var added = false
        store.edit { data ->
            if (data[entry(id)] == null || data[trash(id)] != null) return@edit
            val list = JSONArray(data[marks(id)] ?: "[]")
            val previous = (0 until list.length()).firstOrNull {
                samePlace(Locator.fromJSON(list.getJSONObject(it).getJSONObject("locator")), locator)
            }
            if (previous != null && toggle) list.remove(previous)
            else if (previous == null) {
                list.put(JSONObject().apply {
                    put("id", UUID.randomUUID().toString()); put("chapter", index); put("title", title)
                    put("percent", percent); put("excerpt", locator.text.highlight.orEmpty())
                    put("created", DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date()))
                    put("locator", json)
                })
                added = true
            } else added = true
            data[marks(id)] = list.toString()
        }
        return added
    }

    suspend fun removeBookmark(bookId: String, bookmarkId: String) {
        store.edit { data ->
            val array = JSONArray(data[marks(bookId)] ?: "[]")
            (0 until array.length()).firstOrNull { array.getJSONObject(it).getString("id") == bookmarkId }?.let(array::remove)
            data[marks(bookId)] = array.toString()
        }
    }

    suspend fun removeBook(id: String): String = UUID.randomUUID().toString().also { token ->
        fileLock.withLock { store.edit { if (it[entry(id)] != null) it[trash(id)] = token } }
    }

    suspend fun endRemoval(id: String, token: String, undo: Boolean) = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val updated = store.edit { data ->
                if (data[trash(id)] == token) {
                    data.remove(trash(id))
                    if (!undo) { data.remove(entry(id)); data.remove(position(id)); data.remove(marks(id)); data.remove(layoutKey(id)); data.remove(customKey(id)) }
                }
            }
            if (updated[entry(id)] == null) { files.file(id).delete(); files.cover(id).delete() }
        }
    }

    // Recover interrupted imports/removals before any new import acquires this same lock.
    suspend fun recover() = withContext(Dispatchers.IO) {
        fileLock.withLock {
            val active = store.edit { data ->
                for (key in data.asMap().keys.filter { it.name.startsWith("trash:") }) {
                    val id = key.name.substringAfter(':')
                    data.remove(trash(id)); data.remove(entry(id)); data.remove(position(id)); data.remove(marks(id)); data.remove(layoutKey(id)); data.remove(customKey(id))
                }
            }
            files.directory.listFiles()?.filter { active[entry(it.nameWithoutExtension)] == null }?.forEach(File::delete)
        }
    }

    suspend fun updatePreferences(change: (ReaderPreferences) -> ReaderPreferences) {
        store.edit { data -> data[settings] = encodePreferences(change(decodePreferences(data[settings]))) }
    }

    private fun encodePreferences(p: ReaderPreferences): String = JSONObject().apply {
        put("size", p.fontSizeSp.toDouble()); put("line", p.lineHeightMultiplier.toDouble())
        put("margin", p.horizontalMarginDp.toDouble()); put("theme", p.theme.name); put("mode", p.pageTurnMode.name)
    }.toString()

    private fun decodePreferences(raw: String?): ReaderPreferences {
        val p = ReaderPreferences.Default
        val j = raw?.let(::JSONObject) ?: return p
        return ReaderPreferences(j.optDouble("size", p.fontSizeSp.toDouble()).toFloat(),
            j.optDouble("line", p.lineHeightMultiplier.toDouble()).toFloat(),
            j.optDouble("margin", p.horizontalMarginDp.toDouble()).toFloat(),
            ReaderTheme.entries.find { it.name == j.optString("theme") } ?: p.theme,
            PageTurnMode.entries.find { it.name == j.optString("mode") } ?: p.pageTurnMode)
    }
}

internal fun samePlace(a: Locator?, b: Locator?): Boolean =
    a != null && b != null && a.href == b.href && a.locations.fragments == b.locations.fragments &&
        a.locations.position == b.locations.position &&
        kotlin.math.abs((a.locations.progression ?: 0.0) - (b.locations.progression ?: 0.0)) < 0.000001
