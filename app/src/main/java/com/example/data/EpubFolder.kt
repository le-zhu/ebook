package com.example.data

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

internal data class FolderScan(val books: List<Uri>, val incompleteFolders: Int)

internal suspend fun scanEpubFolder(resolver: ContentResolver, tree: Uri): FolderScan = withContext(Dispatchers.IO) {
    val root = DocumentsContract.getTreeDocumentId(tree)
    val pending = ArrayDeque<String>()
    pending.add(root)
    val visited = hashSetOf(root)
    val books = mutableListOf<Uri>()
    var incomplete = 0
    val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME)

    while (pending.isNotEmpty()) {
        ensureActive()
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, pending.removeFirst())
        try {
            val cursor = resolver.query(children, columns, null, null, null) ?: throw IOException("目录不可读")
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(Document.COLUMN_DOCUMENT_ID)
                val typeColumn = it.getColumnIndexOrThrow(Document.COLUMN_MIME_TYPE)
                val nameColumn = it.getColumnIndexOrThrow(Document.COLUMN_DISPLAY_NAME)
                while (it.moveToNext()) {
                    ensureActive()
                    val id = it.getString(idColumn) ?: continue
                    if (!visited.add(id)) continue
                    val mime = it.getString(typeColumn)
                    val name = it.getString(nameColumn).orEmpty()
                    if (mime == Document.MIME_TYPE_DIR) {
                        pending.addLast(id)
                    } else if (mime.equals("application/epub+zip", ignoreCase = true) ||
                        name.endsWith(".epub", ignoreCase = true)) {
                        books.add(DocumentsContract.buildDocumentUriUsingTree(tree, id))
                    }
                }
                if (it.extras.getBoolean(DocumentsContract.EXTRA_LOADING) ||
                    it.extras.getString(DocumentsContract.EXTRA_ERROR) != null) incomplete++
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // A removed or inaccessible subdirectory need not interrupt its siblings.
            incomplete++
        }
    }
    FolderScan(books, incomplete)
}

