package com.example

import com.example.ui.bookshelf.synopsisText
import org.junit.Assert.assertEquals
import org.junit.Test

class BookSynopsisTest {
    @Test fun htmlKeepsParagraphsLineBreaksAndInlineText() {
        val html = "<div><p>第一段有<b>重点</b>。</p><p>A &amp; B<br>下一行</p></div>"
        assertEquals("第一段有重点。\nA & B\n下一行", synopsisText(html))
        assertEquals("", synopsisText("<div><br><img src='cover.jpg'><script>unused()</script></div>"))
        assertEquals("hello world", synopsisText("<p>hello\n  world</p>"))
        assertEquals("", synopsisText("<!-- no description -->"))
    }

    @Test fun plainTextKeepsItsBlankLinesAndLiteralComparisons() {
        assertEquals("上篇\n\n下篇", synopsisText(" 上篇\n\n下篇 "))
        assertEquals("2 < 3，类型 <T>", synopsisText("2 < 3，类型 <T>"))
        assertEquals("", synopsisText(" \n "))
    }
}
