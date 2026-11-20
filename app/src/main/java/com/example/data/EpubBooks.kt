package com.example.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.OpenableColumns
import android.util.Size
import com.example.model.Book
import com.example.model.TocItem
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.publication.indexOfFirstWithHref
import org.readium.r2.shared.publication.services.coverFitting
import org.readium.r2.shared.publication.services.isRestricted
import org.readium.r2.shared.util.Url
import org.readium.r2.shared.util.asset.AssetRetriever
import org.readium.r2.shared.util.getOrElse
import org.readium.r2.shared.util.http.DefaultHttpClient
import org.readium.r2.streamer.PublicationOpener
import org.readium.r2.streamer.parser.epub.EpubParser

internal class EpubBooks(private val context: Context) {
    val directory = File(context.filesDir, "epub-library").apply { mkdirs() }
    private val http = DefaultHttpClient()
    private val assets = AssetRetriever(context.contentResolver, http)
    private val opener = PublicationOpener(EpubParser(http))

    fun file(id: String) = File(directory, "$id.epub")
    fun cover(id: String) = File(directory, "$id.jpg")

    suspend fun open(file: File): Publication {
        val asset = assets.retrieve(file).getOrElse { throw IOException("无法识别 EPUB 文件") }
        val pub = try {
            opener.open(asset, allowUserInteraction = false).getOrElse { throw IOException("EPUB 损坏、加密或格式不受支持") }
        } catch (error: Throwable) {
            asset.close()
            throw error
        }
        if (pub.isRestricted || pub.readingOrder.isEmpty()) {
            pub.close()
            throw IOException("书籍受保护或没有可阅读的正文")
        }
        return pub
    }

    fun displayName(uri: Uri): String {
        val fallback = uri.lastPathSegment.orEmpty()
        if (uri.scheme != "content") return fallback
        return runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst() && !it.isNull(0)) it.getString(0) else null
            }
        }.getOrNull() ?: fallback
    }

    fun copyAndIdentify(uri: Uri, destination: File): String {
        require(uri.scheme == "content" || uri.scheme == "file") { "请选择本地 EPUB 文件" }
        if (uri.scheme == "file") {
            val file = File(requireNotNull(uri.path)).canonicalFile
            val roots = if (Build.VERSION.SDK_INT >= 30) {
                context.getSystemService(StorageManager::class.java).storageVolumes.mapNotNull { it.directory }
            } else listOf(Environment.getExternalStorageDirectory())
            require(roots.any { file.path.startsWith(it.canonicalPath + File.separator) }) {
                "请通过系统文件选择器打开这本书"
            }
        }
        val digest = MessageDigest.getInstance("SHA-256")
        val input = context.contentResolver.openInputStream(uri) ?: throw IOException("无法打开文件")
        input.use { source ->
            destination.outputStream().buffered().use { output ->
                val bytes = ByteArray(64 * 1024)
                while (true) {
                    val size = source.read(bytes)
                    if (size < 0) break
                    digest.update(bytes, 0, size)
                    output.write(bytes, 0, size)
                }
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    suspend fun saveCover(publication: Publication, id: String): Boolean {
        val target = cover(id)
        try {
            val bitmap = publication.coverFitting(Size(480, 720)) ?: return false
            return target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (_: Exception) {
            target.delete()
            return false
        }
    }
}

internal fun Book.withContents(pub: Publication): Book {
    val spine = pub.readingOrder
    val paths = mutableMapOf<Url, Int>()
    spine.forEachIndexed { index, link -> paths.putIfAbsent(link.url(), index) }
    fun spineIndex(url: Url) = paths[url] ?: paths[url.removeFragment()] ?: spine.indexOfFirstWithHref(url)
    fun entries(links: List<Link>, level: Int, prefix: String): List<TocItem> =
        links.mapIndexed { i, link ->
            val id = "$prefix.$i"
            val children = entries(link.children, level + 1, id)
            val url = link.url()
            TocItem(id, link.title.orEmpty(), spineIndex(url), level, children = children, href = url.toString())
        }
    return copy(toc = entries(pub.tableOfContents, 0, "toc"))
}
