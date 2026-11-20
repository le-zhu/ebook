package com.example

import android.app.Application
import android.net.Uri
import android.os.Bundle
import androidx.lifecycle.SavedStateHandle
import java.util.UUID
import com.example.data.FolderScan
import com.example.data.LocalLibrary
import com.example.data.scanEpubFolder
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

data class ImportResult(val readActionId: String?, val message: String)

class ImportViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private data class Request(
        val files: List<Uri> = emptyList(),
        val folder: Uri? = null,
        val open: Boolean = false,
        val token: String = UUID.randomUUID().toString(),
    ) {
        fun bundle() = Bundle().apply {
            putStringArrayList("uris", ArrayList(files.map(Uri::toString)))
            putString("folder", folder?.toString())
            putBoolean("open", open); putString("token", token)
        }
    }
    val pendingOpen = saved.getStateFlow<String?>("openBook", null)
    fun opened(id: String) { if (pendingOpen.value == id) saved["openBook"] = null }
    private fun queued() = saved.get<ArrayList<Bundle>>("imports").orEmpty()
    private val requests = Channel<Request>(Channel.UNLIMITED)
    private val outcomes = Channel<ImportResult>(Channel.UNLIMITED)
    val results = outcomes.receiveAsFlow()
    private val status = MutableStateFlow<String?>(null)
    val progress = status.asStateFlow()

    init {
        val library = (app as ReaderApplication).library
        queued().forEach { bundle ->
            val uris = bundle.getStringArrayList("uris").orEmpty().map(Uri::parse)
            val folder = bundle.getString("folder")?.let(Uri::parse)
            bundle.getString("token")?.let { token ->
                requests.trySend(Request(uris, folder, bundle.getBoolean("open"), token))
            }
        }
        viewModelScope.launch {
            for (request in requests) {
                val result = try {
                    importBooks(request, library)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    ImportResult(null, "导入失败：" + (e.localizedMessage ?: "请重新选择文件夹"))
                } finally {
                    status.value = null
                }
                if (request.open && result.readActionId != null) saved["openBook"] = result.readActionId
                else outcomes.send(result)
                saved["imports"] = ArrayList(queued().filterNot { it.getString("token") == request.token })
            }
        }
    }

    private suspend fun importBooks(request: Request, library: LocalLibrary): ImportResult {
        val scan = if (request.folder != null) {
            status.value = "正在查找文件夹和子文件夹里的 EPUB…"
            scanEpubFolder(getApplication<Application>().contentResolver, request.folder)
        } else FolderScan(request.files, 0)

        if (scan.books.isEmpty()) {
            val message = if (scan.incompleteFolders > 0) "未找到 EPUB，有文件夹未能完整读取，请重新选择或稍后重试"
                else "所选文件夹及子文件夹中没有 EPUB 书籍"
            return ImportResult(null, message)
        }

        var added = 0
        var existing = 0
        var failed = 0
        var lastError = ""
        var readId: String? = null
        for ((index, uri) in scan.books.withIndex()) {
            status.value = "正在导入 ${index + 1} / ${scan.books.size}"
            try {
                val (book, isNew) = library.import(uri)
                if (isNew) added++ else existing++
                if (request.open || scan.books.size == 1) readId = book.id
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { failed++; lastError = e.localizedMessage ?: "文件不可读" }
        }
        val message = listOfNotNull(
            "已导入 $added 本".takeIf { added > 0 },
            "书架已有 $existing 本".takeIf { existing > 0 },
            "失败 $failed 本（$lastError）".takeIf { failed > 0 },
            "${scan.incompleteFolders} 个文件夹未能完整读取".takeIf { scan.incompleteFolders > 0 },
        ).joinToString("，")
        return ImportResult(readId, message)
    }

    fun submitFolder(folder: Uri) = enqueue(Request(folder = folder))

    fun submit(uris: List<Uri>, open: Boolean = false) {
        if (uris.isNotEmpty()) enqueue(Request(files = uris.distinct(), open = open))
    }

    private fun enqueue(request: Request) {
        saved["imports"] = ArrayList(queued() + request.bundle())
        requests.trySend(request)
    }
}
