package com.example.ui.bookshelf

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import kotlin.math.abs
import kotlin.math.max

/** Give one finger to the cards and scroller; own a pinch until every finger lifts. */
internal suspend fun PointerInputScope.detectColumnPinches(onStep: (Int) -> Unit) {
    awaitEachGesture {
        var initialSpan = 0f
        var pinching = false
        var finished = false
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val span = (pressed[0].position - pressed[1].position).getDistance()
                if (!pinching) {
                    initialSpan = span
                    pinching = true
                }
                if (pressed.size != 2) finished = true
                val threshold = max(viewConfiguration.touchSlop * 2f, initialSpan * 0.18f)
                if (!finished && abs(span - initialSpan) >= threshold) {
                    finished = true
                    onStep(if (span < initialSpan) 1 else -1)
                }
            } else if (pinching) {
                finished = true
            }
            if (pinching) event.changes.forEach { it.consume() }
        } while (pressed.isNotEmpty())
    }
}
