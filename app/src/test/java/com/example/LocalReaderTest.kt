package com.example

import android.content.Context
import android.content.Intent
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.lifecycle.lifecycleScope
import androidx.test.core.app.ApplicationProvider
import com.example.data.LocalLibrary
import com.example.data.LibraryDatabase
import com.example.data.withContents
import com.example.model.LayoutMode
import com.example.model.TocItem
import com.example.ui.reader.TocLocation
import com.example.ui.reader.searchPages
import com.example.ui.reader.contextBefore
import com.example.ui.reader.contextAfter
import kotlinx.coroutines.flow.toList
import org.readium.r2.shared.publication.Locator
import com.example.model.ReaderTheme
import com.example.model.flattenToc
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.readium.r2.shared.publication.services.locateProgression
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LocalReaderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val resolver = shadowOf(context.contentResolver)

    private fun document(name: String, bytes: ByteArray): Uri =
        Uri.parse("content://books.test/document/$name").also { uri ->
            resolver.registerInputStreamSupplier(uri) { bytes.inputStream() }
        }

    @Test fun importReadResumeBookmarkAndUndo() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val library = (context.applicationContext as ReaderApplication).library
            val bytes = epub("本地测试")
            val source = document("first", bytes)
            val (book, added) = library.import(source)
            assertTrue(added)
            assertEquals("本地测试", book.title)
            assertEquals("作者甲", book.author)
            assertFalse(library.import(document("renamed", bytes)).second)
            assertTrue(library.import(document("second", epub("第二本"))).second)
            try { library.import(document("invalid", "not a book".toByteArray())); fail("Invalid file imported") }
            catch (_: java.io.IOException) {}
            assertEquals(2, library.books.first().size)
            val publication = library.open(book.id)
            val place = try {
                val opened = book.withContents(publication)
                assertEquals(listOf("上章", "下章"), opened.toc.map { it.title })
                val child = opened.toc.flattenToc().single { it.title == "小节" }
                assertEquals(1, child.level)
                assertEquals(1, child.chapterIndex)
                assertTrue(child.href!!.endsWith("#section"))
                requireNotNull(publication.locateProgression(0.75))
            } finally { publication.close() }
            library.savePosition(book.id, 1, "下章", 0.75f, place)
            assertTrue(library.bookmark(book.id, 1, "下章", 75, place, false))
            library.updatePreferences { it.copy(theme = ReaderTheme.WARM_PAPER, fontSizeSp = 24f) }
            // Open a copy of the on-disk SQLite and DataStore snapshots with a new LocalLibrary instance.
            library.setLayoutMode(book.id, LayoutMode.CUSTOM)
            library.editLayout(book.id) { it.copy(horizontalMarginDp = 31f) }
            library.stepGridColumns(1)
            library.sortBy("AUTHOR")
            library.toggleLayout()
            library.checkpoint()
            val snapshot = java.io.File.createTempFile("cold-library-", ".preferences_pb", context.cacheDir)
            java.io.File(context.filesDir, "datastore/epub_library.preferences_pb").copyTo(snapshot, overwrite = true)
            val coldDbName = "cold-library-${System.nanoTime()}.db"
            context.getDatabasePath("epub_library.db").copyTo(context.getDatabasePath(coldDbName), overwrite = true)
            val coldStore = androidx.datastore.preferences.core.PreferenceDataStoreFactory.create(scope = scope) { snapshot }
            // DataStore should only hold global preferences, never per-book keys.
            assertFalse(coldStore.data.first().asMap().keys.any { it.name.endsWith(":" + book.id) })
            val reopened = LocalLibrary(context, scope, coldStore, coldDbName)
            try {
                assertEquals(place.toJSON().toString(), reopened.lastLocator(book.id)?.toJSON().toString())
                assertEquals(0.75f, reopened.observeBook(book.id).first()!!.progress, 0.001f)
                assertEquals(place, reopened.observeBookmarks(book.id).first().single().locator)
                assertEquals(24f, reopened.preferences.first().fontSizeSp, 0f)
                assertEquals(LayoutMode.CUSTOM, reopened.layout(book.id).first().mode)
                assertEquals(31f, reopened.layout(book.id).first().preferences.horizontalMarginDp, 0f)
                assertEquals("AUTHOR", reopened.shelfOrder.first())
                assertTrue(reopened.listLayout.first())
                assertEquals(3, reopened.gridColumns.first())
                val token = reopened.removeBook(book.id)
                assertNull(reopened.observeBook(book.id).first())
                reopened.endRemoval(book.id, token, true)
                assertEquals(1, reopened.observeBookmarks(book.id).first().size)
                val finalRemoval = reopened.removeBook(book.id)
                reopened.endRemoval(book.id, finalRemoval, false)
                assertNull(reopened.observeBook(book.id).first())
                assertTrue(reopened.observeBookmarks(book.id).first().isEmpty())
                assertNull(reopened.lastLocator(book.id))
                assertTrue(reopened.import(source).second)
                reopened.savePosition(book.id, 1, "下章", 0.75f, place)
                reopened.bookmark(book.id, 1, "下章", 75, place, false)
                reopened.setLayoutMode(book.id, LayoutMode.CUSTOM)
                reopened.removeBook(book.id)
                reopened.recover()
                assertTrue(reopened.observeBookmarks(book.id).first().isEmpty())
                assertEquals("第二本", reopened.books.first().single().title)
            } finally {
                reopened.close()
                context.deleteDatabase(coldDbName)
            }
        } finally { scope.cancel() }
    }

    @Test fun longDescriptionIsLoadedInFullOnlyForTheSelectedBook() = runBlocking {
        val library = (context.applicationContext as ReaderApplication).library
        val synopsis = "长简介📚".repeat(360_000) + "末尾"
        val short = library.import(document("short", epub("普通书"))).first
        val long = library.import(document("long", epub("长简介", description = synopsis))).first
        assertEquals(setOf(short.id, long.id), library.books.first().map { it.id }.toSet())
        assertEquals(synopsis, library.description(long.id))
        assertEquals("", library.description(short.id))
    }

    @Test fun debouncedPositionUpdatesFlushImmediatelyOnDemand() = runBlocking {
        val lib = (context.applicationContext as ReaderApplication).library
        val book = lib.import(document("debounce", epub("翻页防抖"))).first
        val first = Locator(
            href = org.readium.r2.shared.util.Url("a.xhtml")!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            locations = Locator.Locations(progression = 0.2, totalProgression = 0.1),
        )
        val second = Locator(
            href = org.readium.r2.shared.util.Url("b.xhtml")!!,
            mediaType = org.readium.r2.shared.util.mediatype.MediaType.XHTML,
            locations = Locator.Locations(progression = 0.8, totalProgression = 0.9),
        )
        lib.recordPosition(book.id, 0, "上章", 0.1f, first)
        lib.recordPosition(book.id, 1, "下章", 0.9f, second)
        // Pending in-memory locator is immediately visible to lastLocator().
        assertEquals(second.toJSON().toString(), lib.lastLocator(book.id)?.toJSON().toString())
        lib.flushPosition(book.id)
        val updated = withTimeout(5000) { lib.observeBook(book.id).first { it != null && it.progress >= 0.89f } }
        assertEquals("下章", updated?.currentChapterTitle)
        assertEquals(second.toJSON().toString(), lib.lastLocator(book.id)?.toJSON().toString())
    }

    @Test fun globalAndPerBookLayoutsRemainIndependentAcrossSwitches() = runBlocking {
        val lib = (context.applicationContext as ReaderApplication).library
        val a = lib.import(document("layout-a", epub("甲"))).first.id
        val b = lib.import(document("layout-b", epub("乙"))).first.id
        assertEquals(LayoutMode.ORIGINAL, lib.layout(a).first().mode)
        lib.setLayoutMode(a, LayoutMode.GLOBAL)
        lib.setLayoutMode(b, LayoutMode.GLOBAL)
        lib.editLayout(a) { it.copy(fontSizeSp = 22f, lineHeightMultiplier = 1.9f) }
        assertEquals(22f, lib.layout(b).first().preferences.fontSizeSp)
        lib.setLayoutMode(b, LayoutMode.CUSTOM)
        assertEquals(1.9f, lib.layout(b).first().preferences.lineHeightMultiplier)
        lib.editLayout(b) { it.copy(fontSizeSp = 25f) }
        lib.editLayout(a) { it.copy(fontSizeSp = 16f) }
        lib.setLayoutMode(b, LayoutMode.ORIGINAL)
        lib.setLayoutMode(b, LayoutMode.GLOBAL)
        assertEquals(16f, lib.layout(b).first().preferences.fontSizeSp)
        lib.setLayoutMode(b, LayoutMode.CUSTOM)
        assertEquals(25f, lib.layout(b).first().preferences.fontSizeSp)
        lib.editLayout(b, reset = true) { it }
        assertEquals(16f, lib.layout(b).first().preferences.fontSizeSp)
        lib.editLayout(b) { it.copy(horizontalMarginDp = 34f) }
        assertNotEquals(34f, lib.layout(a).first().preferences.horizontalMarginDp)
    }

    @Test fun sizeEditWaitsForTheFirstSwitchToCustom() = runBlocking {
        val lib = (context.applicationContext as ReaderApplication).library
        val id = lib.import(document("layout-race", epub("排版顺序"))).first.id
        lib.setLayoutMode(id, LayoutMode.GLOBAL)
        coroutineScope {
            withBusyDatabaseWriter {
                launch(start = CoroutineStart.UNDISPATCHED) { lib.setLayoutMode(id, LayoutMode.CUSTOM) }
                val sizeEdit = async(start = CoroutineStart.UNDISPATCHED) {
                    lib.editLayout(id) { it.copy(fontSizeSp = 22f) }
                }
                assertNull(withTimeoutOrNull(500) { sizeEdit.await() })
            }
        }
        val book = lib.layout(id).first()
        assertEquals(LayoutMode.CUSTOM, book.mode)
        assertEquals(22f, book.preferences.fontSizeSp, 0f)
        assertEquals(18f, lib.preferences.first().fontSizeSp, 0f)
    }

    @Test fun unchangedLayoutChoicesDoNotNeedAWrite() = runBlocking {
        val lib = (context.applicationContext as ReaderApplication).library
        val id = lib.import(document("layout-repeat", epub("重复排版"))).first.id
        lib.setLayoutMode(id, LayoutMode.CUSTOM)
        coroutineScope {
            withBusyDatabaseWriter {
                val repeats = async {
                    lib.setLayoutMode(id, LayoutMode.CUSTOM)
                    lib.editLayout(id) { it.copy(fontSizeSp = 18f) }
                    lib.editLayout(id, reset = true) { it }
                }
                withTimeout(5000) { repeats.await() }
            }
        }
    }

    private suspend fun withBusyDatabaseWriter(block: suspend () -> Unit) {
        val path = context.getDatabasePath(LibraryDatabase.DEFAULT_NAME).path
        SQLiteDatabase.openDatabase(path, null, SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.ENABLE_WRITE_AHEAD_LOGGING).use {
            it.beginTransactionNonExclusive()
            try { block() } finally { it.endTransaction() }
        }
    }

    @Test fun wholeBookSearchReturnsNavigableMatchesInSpineOrder() = runBlocking {
        val lib = (context.applicationContext as ReaderApplication).library
        val book = lib.import(document("search", epub("搜索测试"))).first
        val pub = lib.open(book.id, preloadPositions = false)
        try {
            val pages = searchPages(pub, "正文", pageSize = 1).toList()
            assertEquals(listOf(true, false), pages.map { it.hasMore })
            val hits = pages.flatMap { it.hits }
            assertEquals(listOf("a.xhtml", "b.xhtml"), hits.map { it.href.toString() })
            assertTrue(hits.all { it.text.highlight == "正文" && it.text.after.orEmpty().contains("。") })
            assertTrue(searchPages(pub, "没有这个人名").first().hits.isEmpty())
            assertEquals("上一句。“他问", contextBefore("很远。上一句。“他问"))
            assertEquals("？”她回答。", contextAfter("？”她回答。再下一句。"))
            val place = requireNotNull(pub.locatorFromLink(pub.readingOrder[1]))
            val toc = TocLocation(listOf(TocItem("chapter", "章", 1, href = "b.xhtml"),
                TocItem("one", "一节", 1, href = "b.xhtml#one"), TocItem("two", "二节", 1, href = "b.xhtml#two")), place.href)
            assertEquals("chapter", toc.chapter?.id)
            assertNull(toc.explicit(place))
            assertEquals("one", toc.explicit(place.copy(locations = Locator.Locations(fragments = listOf("one"))))?.id)
            val onlySections = TocLocation(listOf(TocItem("one", "一节", 1, href = "b.xhtml#one")), place.href)
            assertNull(onlySections.chapter)
        } finally { pub.close() }
    }

    @Test fun externalOpenMountsNavigatorAfterActivityRecreation() {
        val uri = document("rotation", epub("旋转测试"))
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/epub+zip")
        val activity = org.robolectric.Robolectric.buildActivity(MainActivity::class.java, intent).setup().visible()
        val main = shadowOf(android.os.Looper.getMainLooper())
        fun navigator(): org.readium.r2.navigator.epub.EpubNavigatorFragment {
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(25)
            while (System.nanoTime() < deadline) {
                main.idleFor(32, java.util.concurrent.TimeUnit.MILLISECONDS)
                val nav = activity.get().supportFragmentManager.fragments
                    .filterIsInstance<org.readium.r2.navigator.epub.EpubNavigatorFragment>()
                    .firstOrNull { it.isAdded && it.view != null }
                if (nav != null) return nav
                Thread.sleep(12)
            }
            error("A real EPUB navigator was not attached")
        }
        try {
            val first = navigator()
            activity.get().lifecycleScope.launch {
                (context.applicationContext as ReaderApplication).library.updatePreferences {
                    it.copy(pageTurnMode = com.example.model.PageTurnMode.SCROLLED)
                }
            }
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8)
            while (!first.settings.value.scroll && System.nanoTime() < deadline) {
                main.idleFor(32, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(10)
            }
            assertTrue("Reading panel preferences reached the navigator", first.settings.value.scroll)
            activity.recreate()
            assertNotSame(first, navigator())
        } finally { activity.pause().stop().destroy() }
    }

    @Test fun batchReportsBadFileAndImportsFollowingFiles() {
        val app = context.applicationContext as ReaderApplication
        val importer = ImportViewModel(app, androidx.lifecycle.SavedStateHandle())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val result = scope.async { importer.results.first() }
            importer.submit(listOf(
                document("one", epub("批量甲")),
                document("unreadable", byteArrayOf(1, 2, 3)),
                document("two", epub("批量乙")),
            ))
            val main = shadowOf(android.os.Looper.getMainLooper())
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15)
            while (!result.isCompleted && System.nanoTime() < deadline) {
                main.idleFor(32, java.util.concurrent.TimeUnit.MILLISECONDS)
                Thread.sleep(10)
            }
            assertTrue(result.isCompleted)
            val message = runBlocking { result.await().message }
            assertTrue(message, message.contains("已导入 2 本") && message.contains("失败 1 本"))
            val titles = runBlocking { app.library.books.first().map { it.title } }
            assertTrue(titles.containsAll(listOf("批量甲", "批量乙")))
        } finally { scope.cancel() }
    }

    @Test fun fileManagerCanResolveEpubIntent() {
        val cases = listOf(
            "content://books.test/42" to "application/epub+zip",
            "content://books.test/document/novel.epub" to "application/octet-stream",
            "file:///sdcard/Books/novel.epub" to null,
        )
        for ((uri, type) in cases) {
            val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), type)
            val activities = context.packageManager.queryIntentActivities(intent, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
            assertTrue("$uri ($type)", activities.any { it.activityInfo.name == MainActivity::class.java.name })
        }
    }

    @Test fun chineseTitlesAndAuthorsSortByPinyinOrder() {
        val lib = (context.applicationContext as ReaderApplication).library
        // Unicode order would be 北(5317) < 中(4E2D) < 阿(963F); Chinese collation orders 阿(a) < 北(bei) < 中(zhong).
        runBlocking {
            lib.import(document("z", epub("中国科幻", author = "周作人")))
            lib.import(document("b", epub("北京折叠", author = "白居易")))
            lib.import(document("a", epub("阿西莫夫", author = "阿城")))
        }
        val vm = com.example.ui.bookshelf.BookshelfViewModel(lib)
        val main = shadowOf(android.os.Looper.getMainLooper())
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        scope.launch { vm.uiState.collect {} }
        fun awaitOrder(order: com.example.ui.bookshelf.BookshelfSortOrder): com.example.ui.bookshelf.BookshelfUiState {
            vm.setSortOrder(order)
            val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
            while (System.nanoTime() < deadline) {
                main.idleFor(32, java.util.concurrent.TimeUnit.MILLISECONDS)
                val current = vm.uiState.value
                if (current.sortOrder == order && current.books.size == 3) return current
                Thread.sleep(10)
            }
            error("Timed out waiting for sort order $order: ${vm.uiState.value}")
        }
        try {
            assertEquals(
                listOf("阿西莫夫", "北京折叠", "中国科幻"),
                awaitOrder(com.example.ui.bookshelf.BookshelfSortOrder.TITLE).books.map { it.title },
            )
            assertEquals(
                listOf("阿城", "白居易", "周作人"),
                awaitOrder(com.example.ui.bookshelf.BookshelfSortOrder.AUTHOR).books.map { it.author },
            )
        } finally {
            scope.cancel()
        }
    }

}
