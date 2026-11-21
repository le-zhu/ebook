package com.example.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal const val ButtonGroupGapFraction = 0.04f

/** Equal slots share the row with a single gap budget. Wrap whole buttons if their labels need more room. */
@Composable
internal fun EqualButtonGroup(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Layout(content, modifier.fillMaxWidth()) { buttons, constraints ->
        val width = constraints.maxWidth
        val gaps = (width * ButtonGroupGapFraction).roundToInt()
        val widest = buttons.maxOfOrNull { it.maxIntrinsicWidth(Constraints.Infinity) } ?: 0
        val columns = (buttons.size downTo 2).firstOrNull { (width - gaps) / it >= widest } ?: 1
        val usable = if (columns == 1) width else width - gaps
        fun left(i: Int) = i * usable / columns + if (columns == 1) 0 else i * gaps / (columns - 1)
        val rows = buttons.chunked(columns).map { row ->
            row.mapIndexed { i, button ->
                val slot = (i + 1) * usable / columns - i * usable / columns
                button.measure(constraints.copy(minWidth = slot, maxWidth = slot, minHeight = 0))
            }
        }
        val rowGap = 8.dp.roundToPx()
        val height = rows.sumOf { row -> row.maxOf { it.height } } + (rows.size - 1).coerceAtLeast(0) * rowGap
        layout(width, constraints.constrainHeight(height)) {
            var y = 0
            for (row in rows) {
                val rowHeight = row.maxOf { it.height }
                val inset = (width - left(row.lastIndex) - row.last().width) / 2
                row.forEachIndexed { i, button -> button.placeRelative(inset + left(i), y + (rowHeight - button.height) / 2) }
                y += rowHeight + rowGap
            }
        }
    }
}
