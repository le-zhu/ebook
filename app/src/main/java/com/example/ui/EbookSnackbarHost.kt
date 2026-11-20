package com.example.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarVisuals
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.example.ui.theme.FloatingPanelElevation

internal class BookmarkNotice(override val message: String) : SnackbarVisuals {
    override val actionLabel: String? = null
    override val withDismissAction = false
    override val duration = SnackbarDuration.Short
}

@Composable
internal fun EbookSnackbarHost(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SnackbarHost(hostState, modifier) { data ->
        if (data.visuals is BookmarkNotice) {
            EbookNotice(data.visuals.message, Modifier.testTag("bookmark_notice"))
        } else {
            Snackbar(
                snackbarData = data,
                shape = MaterialTheme.shapes.medium,
                containerColor = colors.surface,
                contentColor = colors.onSurfaceVariant,
                actionColor = colors.primary,
                actionContentColor = colors.primary,
                dismissActionContentColor = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun EbookNotice(message: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shadowElevation = FloatingPanelElevation,
    ) {
        Box(Modifier.heightIn(min = layoutChoiceHeight()).padding(actionContentPadding), contentAlignment = Alignment.Center) {
            Text(message, style = actionLabelStyle)
        }
    }
}
