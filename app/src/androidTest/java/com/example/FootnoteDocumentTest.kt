package com.example

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit.SECONDS
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FootnoteDocumentTest {
    @Test fun cssHintUsesTheDeclaredAttributeAndLeavesPlainLinksAndCountersAlone() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val script = context.resources.openRawResource(R.raw.footnote_gestures).bufferedReader().use { it.readText() }
        val loaded = CompletableFuture<Unit>()
        val result = CompletableFuture<String>()
        lateinit var web: WebView
        instrumentation.runOnMainSync {
            web = WebView(context)
            web.settings.javaScriptEnabled = true
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) { loaded.complete(Unit) }
            }
            web.loadDataWithBaseURL(null, """
                <style>.hint:hover::after { content: attr(data-definition) }
                .counter::before { content: attr(data-num) }</style>
                <i class="hint" data-definition="原书提示"></i>
                <a class="hint" data-definition="链接" href="#dest">*</a>
                <span class="counter" id="dest" data-num="1"></span>
            """.trimIndent(), "text/html", "UTF-8", null)
        }
        try {
            loaded.get(10, SECONDS)
            instrumentation.runOnMainSync {
                web.evaluateJavascript("""
                    window.EbookFootnotes = {showNote: text => window.note = text};
                    $script
                    document.querySelector("button").click();
                    [window.note, document.querySelector("a").getAttribute("href"), document.querySelector("#dest").localName]
                """.trimIndent()) { result.complete(it) }
            }
            assertEquals("""["原书提示","#dest","span"]""", result.get(10, SECONDS))
        } finally { instrumentation.runOnMainSync { web.destroy() } }
    }
}
