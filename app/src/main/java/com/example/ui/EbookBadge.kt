package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun EbookBadge(text: String) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = text,
        modifier = Modifier.background(colors.secondaryContainer, MaterialTheme.shapes.small)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        style = MaterialTheme.typography.labelMedium,
        color = colors.onSecondaryContainer,
    )
}
