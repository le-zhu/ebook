package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sign

private class SheetEdge(private val horizontal: Boolean, private val limit: Float) : NestedScrollConnection {
    private val resistance = if (horizontal) 0.24f else 0.42f
    private val axis = if (horizontal) Offset(1f, 0f) else Offset(0f, 1f)
    private var startedAtTop = false
    private var pullDown = false
    var offset by mutableFloatStateOf(0f)
        private set

    fun beginDrag(atTop: Boolean) {
        startedAtTop = atTop
        pullDown = false
    }

    fun dragBy(delta: Float) = pull(delta - unwind(delta))

    private fun unwind(delta: Float): Float {
        if (offset * delta >= 0f) return 0f
        val consumed = delta.sign * minOf(abs(offset), abs(delta))
        offset += consumed
        return consumed
    }

    private fun pull(delta: Float) {
        if (delta == 0f) return
        val fraction = 1f - exp(-abs(delta) * resistance / limit)
        offset += delta.sign * (limit - abs(offset)) * fraction
    }

    suspend fun release(speed: Float) {
        if (offset == 0f && (horizontal || speed == 0f)) return
        try {
            animate(
                initialValue = offset,
                targetValue = 0f,
                initialVelocity = speed.coerceIn(-limit * 8, limit * 8),
                animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
            ) { value, _ -> offset = value.coerceIn(-limit, limit) }
        } finally {
            offset = 0f
        }
    }

    override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
        if (source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (horizontal) available.x else available.y
        if (startedAtTop && delta != 0f) {
            pullDown = delta > 0f
            startedAtTop = false
        }
        return axis * unwind(delta)
    }

    override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
        if (pullDown || source != NestedScrollSource.UserInput) return Offset.Zero
        val delta = if (horizontal) available.x else available.y
        pull(delta)
        return axis * delta
    }

    override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
        if (pullDown) return Velocity.Zero
        val speed = if (horizontal) available.x else available.y
        release(speed)
        return if (horizontal) Velocity(speed, 0f) else Velocity(0f, speed)
    }
}

@Composable
private fun rememberSheetEdge(horizontal: Boolean): SheetEdge {
    val limit = with(LocalDensity.current) { (if (horizontal) 20.dp else 52.dp).toPx() }
    return remember(horizontal, limit) { SheetEdge(horizontal, limit) }
}

@Composable
internal fun Modifier.sheetPagerGestures(): Modifier {
    val edge = rememberSheetEdge(horizontal = true)
    return clipToBounds().graphicsLayer { translationX = edge.offset }.nestedScroll(edge)
}

@Composable
internal fun Modifier.sheetScrollGestures(state: ScrollableState, sidePull: Boolean = false): Modifier {
    val vertical = rememberSheetEdge(horizontal = false)
    val side = if (sidePull) rememberSheetEdge(horizontal = true) else null
    val viewport = clipToBounds().graphicsLayer {
        translationX = side?.offset ?: 0f
        translationY = vertical.offset
    }
    val content = if (side == null) viewport else {
        val drag = rememberDraggableState(side::dragBy)
        viewport.draggable(drag, Orientation.Horizontal, onDragStopped = { speed -> drag.drag { side.release(speed) } })
    }
    return content.nestedScroll(vertical).pointerInput(state, vertical) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            // Only a fresh downward pull from the top may move the sheet.
            vertical.beginDrag(atTop = !state.canScrollBackward)
        }
    }
}
