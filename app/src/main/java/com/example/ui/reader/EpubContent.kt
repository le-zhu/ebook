@file:OptIn(org.readium.r2.shared.ExperimentalReadiumApi::class)

package com.example.ui.reader

import android.view.View
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnAttach
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentContainerView
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.withStarted
import com.example.model.LayoutMode
import com.example.model.PageTurnMode
import com.example.ui.theme.ReaderColorPalette
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.suspendCancellableCoroutine
import com.example.R
import org.readium.r2.navigator.html.HtmlDecorationTemplate
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.HyperlinkNavigator
import org.readium.r2.navigator.epub.*
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.ReadingProgression
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.util.AbsoluteUrl
import kotlin.coroutines.resume

// Register once so the stylesheet also reaches preloaded chapters.
private interface ReaderDocumentStyle : Decoration.Style

@Composable
internal fun EpubContent(
    activity: FragmentActivity,
    model: ReaderViewModel,
    state: ReaderUiState,
    palette: ReaderColorPalette,
    modifier: Modifier,
) {
    val publication = model.publication ?: return
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val configuration = remember(activity) {
        val css = activity.resources.openRawResource(R.raw.reader_document).bufferedReader().use { it.readText() }
        EpubNavigatorFragment.Configuration().apply {
            decorationTemplates[ReaderDocumentStyle::class] =
                HtmlDecorationTemplate(layout = HtmlDecorationTemplate.Layout.BOUNDS, stylesheet = css)
        }
    }
    val p = state.preferences
    val original = state.layoutMode == LayoutMode.ORIGINAL
    val settings = remember(p, palette, original) {
        EpubPreferences(
            publisherStyles = original,
            verticalText = if (original) null else false,
            readingProgression = if (original) null else ReadingProgression.LTR,
            fontSize = if (original) null else p.fontSizeSp / 16.0,
            lineHeight = if (original) null else p.lineHeightMultiplier.toDouble(),
            // The custom stylesheet uses a 1 CSS px gutter, so this factor is the chosen margin in dp.
            pageMargins = if (original) null else p.horizontalMarginDp.toDouble(),
            scroll = p.pageTurnMode == PageTurnMode.SCROLLED,
            theme = if (palette.isDark) Theme.DARK else Theme.LIGHT,
            backgroundColor = if (original) null else Color(palette.background.toArgb()),
            textColor = if (original) null else Color(palette.contentText.toArgb()),
        )
    }
    val latestSettings by rememberUpdatedState(settings)
    var container by remember { mutableStateOf<FragmentContainerView?>(null) }
    var navigator by remember { mutableStateOf<EpubNavigatorFragment?>(null) }
    AndroidView(
        modifier = modifier.background(palette.background)
            .pointerInput(model) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    model.readingSurfaceTouched()
                }
            },
        factory = { context -> FragmentContainerView(context).apply {
            id = View.generateViewId()
            container = this
        } },
    )

    LaunchedEffect(container, publication, lifecycle) {
        val host = container ?: return@LaunchedEffect
        if (!host.isAttachedToWindow) suspendCancellableCoroutine { continuation ->
            host.doOnAttach { if (continuation.isActive) continuation.resume(Unit) }
        }
        val manager = activity.supportFragmentManager
        val nav = lifecycle.withStarted {
            val factory = EpubNavigatorFactory(publication).createFragmentFactory(
                initialLocator = model.locator, initialPreferences = latestSettings,
                configuration = configuration,
                listener = object : EpubNavigatorFragment.Listener {
                    override fun shouldFollowInternalLink(
                        link: Link,
                        context: HyperlinkNavigator.LinkContext?,
                    ): Boolean = !model.followInternalLink(link)

                    override fun onExternalLinkActivated(url: AbsoluteUrl) {}
                },
                paginationListener = object : EpubNavigatorFragment.PaginationListener {
                    override fun onPageChanged(pageIndex: Int, totalPages: Int, locator: org.readium.r2.shared.publication.Locator) {
                        if (model.awaitingSeek) model.moved(locator, totalPages)
                    }
                },
            )
            val fragment = factory.instantiate(activity.classLoader, EpubNavigatorFragment::class.java.name) as EpubNavigatorFragment
            manager.beginTransaction().setReorderingAllowed(true).add(host.id, fragment).commitNow()
            fragment
        }
        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val x = event.point.x / host.width.coerceAtLeast(1)
                if (nav.overflow.value.scroll || x in 0.25f..0.75f) model.toggleControls()
                else if ((x > 0.75f) == (nav.overflow.value.readingProgression == ReadingProgression.LTR)) nav.goForward(true)
                else nav.goBackward(true)
                return true
            }
        }
        try {
            model.attach(nav)
            navigator = nav
            nav.addInputListener(listener)
            nav.currentLocator.collect { model.moved(it) }
        } finally {
            nav.removeInputListener(listener)
            model.detach(nav)
            navigator = null
            if (!manager.isDestroyed && nav.isAdded) manager.beginTransaction().remove(nav).commitNowAllowingStateLoss()
        }
    }
    LaunchedEffect(navigator, state.searchHighlight) {
        val decorations = state.searchHighlight?.let {
            listOf(Decoration("match", it, Decoration.Style.Highlight(0xffffbd48.toInt())))
        }.orEmpty()
        navigator?.applyDecorations(decorations, "book-search")
    }
    LaunchedEffect(navigator, settings) { navigator?.submitPreferences(settings) }
}
