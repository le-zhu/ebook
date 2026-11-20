package com.example

import androidx.activity.BackEventCompat
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.EbookBottomSheet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SheetPredictiveBackTest {
    @get:Rule val ui = createComposeRule()

    @Test fun tallSheetCoversTheBottomDuringBackPreview() = checkBackPreview(640.dp)

    @Test fun shortSheetCoversTheBottomDuringBackPreview() = checkBackPreview(160.dp)

    private fun checkBackPreview(height: Dp) {
        val open = mutableStateOf(true)
        ui.setContent {
            MaterialTheme(colorScheme = lightColorScheme(surface = Color.White)) {
                if (open.value) EbookBottomSheet({ open.value = false }) {
                    Spacer(Modifier.height(height))
                }
            }
        }
        val sheet = ui.onNode(isDialog())
        val dispatcher = (ShadowDialog.getLatestDialog() as ComponentDialog).onBackPressedDispatcher
        fun back(progress: Float) = BackEventCompat(0f, 0f, progress, BackEventCompat.EDGE_RIGHT)
        fun checkBottomAndGetTop(held: Boolean): Int {
            val pixels = sheet.captureToImage().toPixelMap()
            val y = pixels.height - with(ui.density) { 6.dp.roundToPx() }
            val x = pixels.width / 3
            assertEquals("bottom of $height sheet", Color.White, pixels[x, y])
            val side = pixels[2, y]
            if (held) assertNotEquals(Color.White, side) else assertEquals(Color.White, side)
            return (0..y).first { pixels[x, it] == Color.White }
        }
        val top = checkBottomAndGetTop(held = false)
        ui.runOnIdle { dispatcher.dispatchOnBackStarted(back(0f)) }
        for (progress in listOf(.35f, .9f)) {
            ui.runOnIdle { dispatcher.dispatchOnBackProgressed(back(progress)) }
            assertTrue(checkBottomAndGetTop(held = true) > top)
        }
        // Android animates progress back to zero before delivering cancellation.
        ui.runOnIdle { dispatcher.dispatchOnBackProgressed(back(0f)) }
        ui.runOnIdle { dispatcher.dispatchOnBackCancelled() }
        assertEquals(top, checkBottomAndGetTop(held = false))
        ui.runOnIdle { dispatcher.dispatchOnBackStarted(back(0f)) }
        ui.runOnIdle { dispatcher.dispatchOnBackProgressed(back(.9f)) }
        ui.runOnIdle { dispatcher.onBackPressed() }
        ui.onAllNodes(isDialog()).assertCountEquals(0)
    }
}
