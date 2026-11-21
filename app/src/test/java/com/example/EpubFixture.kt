package com.example

import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

internal fun epub(title: String, extraText: String = "", withFrontMatter: Boolean = false, rtl: Boolean = false, description: String? = null): ByteArray {
    val bytes = ByteArrayOutputStream()
    ZipOutputStream(bytes).use { zip ->
        fun file(path: String, text: String) {
            zip.putNextEntry(ZipEntry(path)); zip.write(text.toByteArray()); zip.closeEntry()
        }
        val mime = "application/epub+zip".toByteArray()
        val entry = ZipEntry("mimetype").apply {
            method = ZipEntry.STORED; size = mime.size.toLong()
            crc = java.util.zip.CRC32().apply { update(mime) }.value
        }
        zip.putNextEntry(entry); zip.write(mime); zip.closeEntry()
        file("META-INF/container.xml", """<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0"><rootfiles><rootfile full-path="book.opf" media-type="application/oebps-package+xml"/></rootfiles></container>""")
        val prefaceItem = if (withFrontMatter) """<item id="p" href="p.xhtml" media-type="application/xhtml+xml"/>""" else ""
        val frontSpine = if (withFrontMatter) """<itemref idref="nav"/><itemref idref="p"/>""" else ""
        val frontNav = if (withFrontMatter) """<li><a href="p.xhtml">前言</a></li>""" else ""
        if (withFrontMatter) file("p.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>前言</title></head><body><h1>前言</h1></body></html>""")
        val direction = if (rtl) " page-progression-direction=\"rtl\"" else ""
        val synopsis = description?.let { "<dc:description>${android.text.TextUtils.htmlEncode(it)}</dc:description>" }.orEmpty()
        file("book.opf", """<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="id">
            <metadata xmlns:dc="http://purl.org/dc/elements/1.1/"><dc:identifier id="id">fixture</dc:identifier><dc:title>$title</dc:title><dc:creator>作者甲</dc:creator><dc:language>zh</dc:language>$synopsis</metadata>
            <manifest>$prefaceItem<item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/><item id="a" href="a.xhtml" media-type="application/xhtml+xml"/><item id="b" href="b.xhtml" media-type="application/xhtml+xml"/></manifest><spine$direction>$frontSpine<itemref idref="a"/><itemref idref="b"/></spine></package>""")
        val subsections = if (withFrontMatter) """<ol><li><a href="b.xhtml#detail">细目</a></li></ol>""" else ""
        file("nav.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops"><head><title>目录</title></head><body><nav epub:type="toc"><ol>$frontNav<li><a href="a.xhtml">上章</a></li><li><a href="b.xhtml">下章</a><ol><li><a href="b.xhtml#section">小节</a>$subsections</li></ol></li></ol></nav></body></html>""")
        for (chapter in listOf("a", "b")) file("$chapter.xhtml", """<html xmlns="http://www.w3.org/1999/xhtml"><head><title>章节</title></head><body><p id="section">正文。$extraText<span id="detail"></span></p></body></html>""")
    }
    return bytes.toByteArray()
}
