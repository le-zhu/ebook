package com.example.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.dismiss
import androidx.compose.ui.semantics.semantics
import com.example.ui.theme.FloatingPanelElevation
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EbookBottomSheet(
    onDismiss: () -> Unit,
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
    val gutter = FloatingPanelElevation * 2
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        sheetMaxWidth = BottomSheetDefaults.SheetMaxWidth + gutter * 2,
        containerColor = Color.Transparent,
        shape = RectangleShape,
        scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.22f),
        dragHandle = null,
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
    ) {
        // Keep the shadow inside the moving sheet window, outside the visible panel.
        Surface(
            modifier = Modifier.align(Alignment.CenterHorizontally)
                .widthIn(max = BottomSheetDefaults.SheetMaxWidth).fillMaxWidth().padding(top = gutter),
            shape = BottomSheetDefaults.ExpandedShape,
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = FloatingPanelElevation,
        ) {
            Column(Modifier.windowInsetsPadding(BottomSheetDefaults.modalWindowInsets)) {
                Box(
                    Modifier.align(Alignment.CenterHorizontally)
                        .clickable(onClickLabel = "关闭面板", onClick = ::close)
                        .semantics {
                            dismiss {
                                close()
                                true
                            }
                        },
                ) {
                    BottomSheetDefaults.DragHandle()
                }
                content()
            }
        }
    }
}
