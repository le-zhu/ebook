package com.example.ui.reader

import com.example.ui.publicationText
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.publication.Publication
import org.readium.r2.shared.util.use

private val space = Regex("\\s+")
private const val epubNamespace = "http://www.idpf.org/2007/ops"

private fun Element.isNoteBody(): Boolean {
    if (attr("role").split(space).any { it == "doc-footnote" || it == "doc-endnote" }) return true
    return attributes().any { attr ->
        (attr.key == "epub:type" || (attr.localName() == "type" && attr.namespace() == epubNamespace)) &&
            attr.value.split(space).any { it == "footnote" || it == "endnote" }
    }
}

// A link to an unmarked paragraph or backlink remains a normal navigation, even if its label is "*".
internal suspend fun Publication.noteAt(link: Link): String? {
    val url = link.url()
    val id = url.fragment ?: return null
    val type = link.mediaType ?: linkWithHref(url.removeFragment())?.mediaType
    if (type?.isHtml != true) return null
    val bytes = get(url)?.use { it.read().getOrNull() } ?: return null
    val parser = if (type.subtype == "xhtml+xml") Parser.xmlParser() else Parser.htmlParser()
    val target = Jsoup.parse(bytes.inputStream(), null, "", parser).getElementById(id) ?: return null
    val note = generateSequence(target) { it.parent() }.firstOrNull { it.isNoteBody() } ?: return null
    return publicationText(note.html()).takeIf { it.isNotBlank() }
}
