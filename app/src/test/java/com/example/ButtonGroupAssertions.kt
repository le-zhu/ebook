package com.example

import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import org.junit.Assert.assertEquals

internal fun ComposeTestRule.assertFourPercentRow(vararg tags: String) {
    val boxes = tags.map { onNodeWithTag(it).getUnclippedBoundsInRoot() }
    val span = (boxes.last().right - boxes.first().left).value
    val tolerance = with(density) { 2f.toDp().value }
    for (box in boxes) {
        assertEquals(boxes.first().top.value, box.top.value, tolerance)
        assertEquals(span * .96f / boxes.size, (box.right - box.left).value, tolerance)
    }
    boxes.zipWithNext().forEach { (left, right) ->
        assertEquals(span * .04f / (boxes.size - 1), (right.left - left.right).value, tolerance)
    }
}
