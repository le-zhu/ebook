package com.example

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Bundle
import android.os.Looper
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class FolderImportTest {
    private val app = ApplicationProvider.getApplicationContext<ReaderApplication>()
    private val authority = "local.books.test"
    private fun tree(id: String) = DocumentsContract.buildTreeDocumentUri(authority, id)
    private data class Row(val id: String, val name: String, val mime: String)
    private val directory = Document.MIME_TYPE_DIR

    private fun provide(folders: Map<String, List<Row>>) {
        val provider = object : ContentProvider() {
            override fun onCreate() = true
            override fun query(uri: Uri, projection: Array<out String>?, selection: String?,
                               selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
                val parent = DocumentsContract.getDocumentId(uri)
                val rows = folders[parent] ?: throw SecurityException("目录已不可访问")
                val columns = requireNotNull(projection).toList().toTypedArray()
                return MatrixCursor(columns).apply {
                    for (row in rows) addRow(columns.map { column ->
                        when (column) {
                            Document.COLUMN_DOCUMENT_ID -> row.id
                            Document.COLUMN_DISPLAY_NAME -> row.name
                            Document.COLUMN_MIME_TYPE -> row.mime
                            else -> null
                        }
                    })
                }
            }
            override fun getType(uri: Uri): String? = null
            override fun insert(uri: Uri, values: ContentValues?): Uri? = null
            override fun delete(uri: Uri, selection: String?, args: Array<out String>?) = 0
            override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?) = 0
        }
        provider.attachInfo(app, ProviderInfo().apply { authority = this@FolderImportTest.authority })
        ShadowContentResolver.registerProviderInternal(authority, provider)
    }

    private fun stream(root: Uri, id: String, bytes: ByteArray) {
        val uri = DocumentsContract.buildDocumentUriUsingTree(root, id)
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.inputStream() }
    }

    private fun awaitResult(importer: ImportViewModel): ImportResult {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val outcome = scope.async { importer.results.first() }
            val main = shadowOf(Looper.getMainLooper())
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15)
            while (!outcome.isCompleted && System.nanoTime() < deadline) {
                main.idleFor(20, TimeUnit.MILLISECONDS)
                Thread.sleep(8)
            }
            assertTrue("Folder import did not finish", outcome.isCompleted)
            return runBlocking { outcome.await() }
        } finally { scope.cancel() }
    }

    @Test fun importsNestedEpubsAndContinuesPastUnreadableFoldersAndDamagedFiles() {
        val root = tree("root")
        val a = Row("a", "甲.EPUB", "application/octet-stream")
        val nested = Row("sub", "短篇.epub", directory)
        val children = mutableListOf(a, nested, Row("pdf", "其他.pdf", "application/pdf"))
        provide(mapOf("root" to children, "sub" to listOf(
            Row("bad", "坏书.epub", "application/epub+zip"),
            Row("deep", "合集", directory), Row("locked", "无权读取", directory), a
        ), "deep" to listOf(
            Row("b", "无扩展名", "application/epub+zip"),
            Row("a-copy", "另一份.epub", "application/zip"), Row("root", "父目录", directory)
        )))
        val first = epub("文件夹甲")
        stream(root, "a", first); stream(root, "a-copy", first)
        stream(root, "b", epub("文件夹乙")); stream(root, "bad", byteArrayOf(1, 2, 3))
        val importer = ImportViewModel(app, SavedStateHandle())
        importer.submitFolder(root)
        val message = awaitResult(importer).message
        for (expected in listOf("已导入 2 本", "书架已有 1 本", "失败 1 本", "1 个文件夹未能完整读取"))
            assertTrue(message, message.contains(expected))
        assertEquals(setOf("文件夹甲", "文件夹乙"), runBlocking { app.library.books.first().map { it.title }.toSet() })

        children.add(Row("c", "新增.epub", "application/epub+zip"))
        stream(root, "c", epub("新增"))
        importer.submitFolder(root)
        val rescan = awaitResult(importer).message
        assertTrue(rescan, rescan.contains("已导入 1 本") && rescan.contains("书架已有 3 本"))
        assertEquals(3, runBlocking { app.library.books.first().size })
    }

    @Test fun restoresQueuedTreeAndDistinguishesEmptyFromUnreadable() {
        provide(mapOf("empty" to listOf(Row("txt", "说明.txt", "text/plain"))))
        val pending = Bundle().apply { putString("folder", tree("empty").toString()); putString("token", "queued") }
        val importer = ImportViewModel(app, SavedStateHandle(mapOf("imports" to arrayListOf(pending))))
        val empty = awaitResult(importer)
        assertNull(empty.readActionId)
        assertTrue(empty.message, empty.message.contains("没有 EPUB"))
        importer.submitFolder(tree("revoked"))
        val inaccessible = awaitResult(importer).message
        assertTrue(inaccessible, inaccessible.contains("未能完整读取"))
    }
}

