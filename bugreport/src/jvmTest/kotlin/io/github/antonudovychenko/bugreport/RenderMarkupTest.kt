package io.github.antonudovychenko.bugreport

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import io.github.antonudovychenko.bugreport.editor.MarkupOp
import io.github.antonudovychenko.bugreport.editor.renderMarkup
import io.github.antonudovychenko.bugreport.internal.decodeImage
import io.github.antonudovychenko.bugreport.internal.encodeJpeg
import io.github.antonudovychenko.bugreport.internal.scaledToMax
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame
import kotlin.test.assertTrue

class RenderMarkupTest {
    @Test
    fun nothingToApplyReturnsTheOriginal() {
        val original = solid(100, 50, Color.White)
        assertSame(original, renderMarkup(original, emptyList()))
    }

    @Test
    fun cropKeepsOnlyTheRegionAndStrokesStayWhereTheyWereDrawn() {
        // Left half blue, right half white; a red stroke drawn in image coordinates at x = 150.
        val original = solid(200, 100, Color.White).also { bitmap ->
            Canvas(bitmap).drawRect(Rect(0f, 0f, 100f, 100f), Paint().apply { color = Color.Blue })
        }
        val ops = listOf(
            MarkupOp.Stroke(listOf(Offset(150f, 10f), Offset(150f, 90f)), width = 6f, color = Color.Red),
            MarkupOp.Crop(Rect(50f, 0f, 200f, 100f)),
        )

        val pixels = renderMarkup(original, ops).toPixelMap()

        assertEquals(150, pixels.width)
        assertEquals(100, pixels.height)
        assertClose(Color.Blue, pixels[10, 50])   // x = 60 in the original: blue half
        assertClose(Color.White, pixels[140, 50]) // x = 190: white, untouched
        assertClose(Color.Red, pixels[100, 50])   // the stroke moved with the crop, to x = 150 - 50
    }

    @Test
    fun undoingACropBringsBackTheStrokesOutsideIt() {
        val original = solid(200, 100, Color.White)
        val stroke = MarkupOp.Stroke(listOf(Offset(20f, 20f), Offset(20f, 80f)), width = 6f, color = Color.Red)
        val withCrop = listOf(stroke, MarkupOp.Crop(Rect(100f, 0f, 200f, 100f)))

        assertEquals(100, renderMarkup(original, withCrop).width)
        val undone = renderMarkup(original, withCrop.dropLast(1)).toPixelMap()
        assertEquals(200, undone.width)
        assertClose(Color.Red, undone[20, 50])
    }

    @Test
    fun jpegRoundTripAndDownscaleKeepTheAspectRatio() {
        val big = solid(3200, 1600, Color.Green)
        val bytes = big.scaledToMax(1600).encodeJpeg(quality = 80)

        assertTrue(bytes.size > 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte(), "not a JPEG")
        val decoded = decodeImage(bytes, maxDimension = 800)
        assertEquals(800, decoded.width)
        assertEquals(400, decoded.height)
    }

    private fun solid(width: Int, height: Int, color: Color): ImageBitmap = ImageBitmap(width, height).also {
        Canvas(it).drawRect(Rect(0f, 0f, width.toFloat(), height.toFloat()), Paint().apply { this.color = color })
    }

    private fun assertClose(expected: Color, actual: Color) {
        val close = kotlin.math.abs(expected.red - actual.red) < 0.1f &&
            kotlin.math.abs(expected.green - actual.green) < 0.1f &&
            kotlin.math.abs(expected.blue - actual.blue) < 0.1f
        assertTrue(close, "expected $expected, was $actual")
    }
}
