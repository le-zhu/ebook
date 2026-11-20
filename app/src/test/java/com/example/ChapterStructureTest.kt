package com.example

import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.example.data.withContents
import com.example.model.Book
import com.example.ui.reader.ReadingLocations
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.readium.r2.shared.publication.Locator
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ChapterStructureTest {
    private val app = ApplicationProvider.getApplicationContext<ReaderApplication>()
    private fun open(files: Map<String, String>, toc: String = "", landmarks: String = "", guide: String = ""): Book = runBlocking {
        fun html(body: String) = """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title></title></head><body>$body</body></html>"""
        val nav = html("""<nav epub:type="toc"><ol>$toc</ol></nav>$landmarks""")
        val names = (files.keys + "nav.xhtml").distinct()
        val items = names.mapIndexed { i, name ->
            """<item id="i$i" href="$name" media-type="application/xhtml+xml" ${if (name == "nav.xhtml") "properties=\"nav\"" else ""}/>"""
        }.joinToString("")
        val spine = files.keys.indices.joinToString("") { """<itemref idref="i$it"/>""" }
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            fun add(path: String, text: String) {
                zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
            }
            add("mimetype", "application/epub+zip")
            add("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
            add("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id"><metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">sections</dc:identifier><dc:title>测试合集</dc:title><dc:language>zh</dc:language></metadata><manifest>$items</manifest><spine>$spine</spine>$guide</package>""")
            for ((name, body) in files) if (name != "nav.xhtml") add(name, html(body))
            add("nav.xhtml", nav)
        }
        val uri = Uri.parse("content://reader.test/sections.epub")
        shadowOf(app.contentResolver).registerInputStreamSupplier(uri) { bytes.toByteArray().inputStream() }
        val book = app.library.import(uri).first
        val pub = app.library.open(book.id)
        try { book.withContents(pub) } finally { pub.close() }
    }

    @Test fun onlyPublishedEntriesAreListedAndNestedTargetsKeepTheirAnchors() {
        val book = open(linkedMapOf(
            "front" to "<h1>封面</h1>",
            "a" to "<h1>第一章</h1><h2 id='sub'>1</h2><h3 id='deep'>甲</h3>",
            "continuation" to "<h1>不在目录里</h1>",
            "b" to "<h1>第二章</h1>"
        ), """<li><span>上册</span><ol><li><a href="a">第一章</a><ol><li><a href="a#sub">1</a>
            <ol><li><a href="a#deep">甲</a></li></ol></li></ol></li></ol></li><li><a href="b">第二章</a></li>""")
        assertEquals(listOf("上册", "第一章", "1", "甲", "第二章"), book.tocEntries.map { it.title })
        assertEquals(listOf(0, 1, 2, 3, 0), book.tocEntries.map { it.level })
        assertEquals(listOf("第一章", "1", "甲", "第二章"), book.navigationTargets.map { it.title })
        runBlocking {
            val pub = app.library.open(book.id)
            try {
                val files = ReadingLocations(book, pub).files
                assertNull(files[0].chapter)
                assertEquals("甲", files[2].chapter?.title)
                val place = requireNotNull(pub.locatorFromLink(pub.readingOrder[1]))
                val nested = files[1].explicit(place.copy(locations = Locator.Locations(fragments = listOf("sub"))))
                assertEquals("1", nested?.title)
                assertEquals("甲", book.adjacentTarget(nested, 1)?.title)
            } finally { pub.close() }
        }
    }

    @Test fun headingsAndLandmarksDoNotGenerateAMissingToc() {
        val book = open(mapOf("a" to "<section epub:type='chapter' id='ch'><h1>第一章</h1></section>"),
            landmarks = """<nav epub:type="landmarks"><ol><li><a epub:type="bodymatter" href="a#ch">正文</a></li></ol></nav>""")
        assertTrue(book.toc.isEmpty())
        assertNull(book.adjacentTarget(null, 1))
    }
}
