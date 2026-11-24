package com.example.ui

import org.jsoup.Jsoup
import org.jsoup.nodes.TextNode
import org.jsoup.parser.Parser

internal fun publicationText(source: String): String {
    if ('<' !in source) return Parser.unescapeEntities(source, false).trim()
    val body = Jsoup.parseBodyFragment(source).body()
    if (body.childrenSize() == 0) return body.wholeText().trim()
    if (body.getAllElements().none { it !== body && it.tag().isKnownTag }) {
        return Parser.unescapeEntities(source, false).trim()
    }
    body.select("script, style, template").remove()
    // Collapse whitespace inside HTML text, keeping only structural line breaks.
    for (element in body.getAllElements()) {
        element.textNodes().forEach { it.text(it.text()) }
        if (element !== body && element.isBlock) {
            element.before(TextNode("\n"))
            element.after(TextNode("\n"))
        }
    }
    return body.wholeText().lineSequence().map(String::trim).filter(String::isNotEmpty).joinToString("\n")
}
