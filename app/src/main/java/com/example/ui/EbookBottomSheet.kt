package com.example.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheet
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scrim
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.example.R
import com.example.ui.theme.FloatingPanelElevation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EbookBottomSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val state = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val scope = rememberCoroutineScope()
    fun close() {
        scope.launch {
            state.hide()
            if (!state.isVisible) onDismiss()
        }
    }
    Dialog(
        onDismissRequest = ::close,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            scrimAlpha = 0f,
        ),
    ) {
        val view = LocalView.current
        val lightSurface = MaterialTheme.colorScheme.surface.luminance() > 0.5f
        SideEffect {
            val window = (view.parent as DialogWindowProvider).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = lightSurface
                isAppearanceLightNavigationBars = lightSurface
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            val dim = animateFloatAsState(
                if (state.targetValue == SheetValue.Hidden) 0f else 0.22f,
                MaterialTheme.motionScheme.defaultEffectsSpec(),
            )
            Scrim(
                contentDescription = stringResource(R.string.sheet_close),
                onClick = ::close,
                alpha = { dim.value },
                color = MaterialTheme.colorScheme.scrim,
            )
            BottomSheet(
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top)),
                state = state,
                onDismissRequest = onDismiss,
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 0.dp,
                shadowElevation = FloatingPanelElevation,
            ) {
                Column(modifier.fillMaxWidth(), content = content)
            }
        }
        if (state.hasExpandedState) LaunchedEffect(state) {
            if (state.targetValue == SheetValue.Hidden) state.show()
        }
    }
}

@Composable
internal fun SheetFixedContent(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = SheetSidePadding), content = content)
}

internal val SheetSidePadding = 24.dp

@Composable
internal fun ColumnScope.SheetScrollSection(fill: Boolean = true, content: @Composable (Modifier) -> Unit) {
    HorizontalDivider(Modifier.padding(horizontal = SheetSidePadding).testTag("sheet_section_divider"))
    content(Modifier.fillMaxWidth().weight(1f, fill = fill))
}

@Composable
internal fun ColumnScope.SheetScrollBody(
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scroll = rememberScrollState()
    SheetScrollSection(fill = false) { viewport ->
        Column(
            viewport.then(modifier).sheetScrollGestures(scroll, sidePull = true)
                .verticalScroll(scroll, overscrollEffect = null)
                .padding(horizontal = SheetSidePadding).padding(contentPadding),
            content = content,
        )
    }
}
