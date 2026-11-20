package com.example.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.ControlHeight

internal val actionVerticalPadding = 6.dp
internal val actionContentPadding = PaddingValues(horizontal = 16.dp, vertical = actionVerticalPadding)
internal val actionLabelStyle: TextStyle
    @Composable get() = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)

@Composable
internal fun layoutChoiceHeight(): Dp = with(LocalDensity.current) {
    maxOf(ControlHeight, MaterialTheme.typography.labelLarge.lineHeight.toDp() + actionVerticalPadding * 2)
}

@Composable
internal fun EbookTextButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TextButton(onClick, modifier.heightIn(min = layoutChoiceHeight()), enabled = enabled, shape = MaterialTheme.shapes.medium) {
        Text(label, style = actionLabelStyle)
    }
}

@Composable
internal fun EbookActionButton(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconModifier: Modifier = Modifier.size(18.dp),
) {
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { role = Role.Button },
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Row(
            modifier = Modifier.heightIn(min = layoutChoiceHeight()).padding(actionContentPadding),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, modifier = iconModifier)
            Spacer(Modifier.width(6.dp))
            Text(label, style = actionLabelStyle)
        }
    }
}
