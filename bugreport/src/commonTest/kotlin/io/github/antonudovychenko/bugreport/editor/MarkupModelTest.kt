package io.github.antonudovychenko.bugreport.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MarkupModelTest {
    private val image = Size(400f, 200f)

    @Test
    fun fitTransformLetterboxesAndRoundTrips() {
        // A 400x200 image in a 200x200 box scales by 0.5 and sits in the vertical middle.
        val transform = FitTransform(Rect(Offset.Zero, image), Rect(0f, 0f, 200f, 200f))

        assertEquals(0.5f, transform.scale)
        assertEquals(Rect(0f, 50f, 200f, 150f), transform.destination)
        val point = Offset(123f, 77f)
        assertClose(point, transform.toImage(transform.toScreen(point)))
    }

    @Test
    fun fitTransformMapsACroppedRegionToTheWholeBox() {
        val transform = FitTransform(Rect(100f, 50f, 200f, 150f), Rect(10f, 10f, 210f, 210f))

        assertEquals(2f, transform.scale)
        assertClose(Offset(10f, 10f), transform.toScreen(Offset(100f, 50f)))
        assertClose(Offset(150f, 100f), transform.toImage(Offset(110f, 110f)))
    }

    @Test
    fun visibleRectIsTheLatestCropOrTheWholeImage() {
        assertEquals(Rect(Offset.Zero, image), emptyList<MarkupOp>().visibleRect(image))

        val ops = listOf(
            MarkupOp.Crop(Rect(10f, 10f, 300f, 150f)),
            MarkupOp.Stroke(listOf(Offset(20f, 20f)), 4f, Color.Red),
            MarkupOp.Crop(Rect(20f, 20f, 100f, 100f)),
        )
        assertEquals(Rect(20f, 20f, 100f, 100f), ops.visibleRect(image))
        assertEquals(Rect(10f, 10f, 300f, 150f), ops.dropLast(1).visibleRect(image))
    }

    @Test
    fun cornersWinOverEdgesAndInsideMoves() {
        val rect = Rect(100f, 100f, 300f, 300f)

        assertEquals(CropHandle.TopLeft, hitCropHandle(rect, Offset(110f, 95f), 20f))
        assertEquals(CropHandle.BottomRight, hitCropHandle(rect, Offset(305f, 290f), 20f))
        assertEquals(CropHandle.Top, hitCropHandle(rect, Offset(200f, 105f), 20f))
        assertEquals(CropHandle.Left, hitCropHandle(rect, Offset(90f, 200f), 20f))
        assertEquals(CropHandle.Move, hitCropHandle(rect, Offset(200f, 200f), 20f))
        assertNull(hitCropHandle(rect, Offset(10f, 10f), 20f))
    }

    @Test
    fun draggingAnEdgeStopsAtTheBoundsAndTheMinimumSize() {
        val bounds = Rect(0f, 0f, 400f, 200f)
        val rect = Rect(50f, 50f, 350f, 150f)

        // Past the image edge: pinned to it.
        assertEquals(Rect(0f, 50f, 350f, 150f), dragCropRect(rect, CropHandle.Left, Offset(-500f, 0f), bounds, 20f))
        // Past the opposite edge: pinned to the minimum size.
        assertEquals(Rect(330f, 50f, 350f, 150f), dragCropRect(rect, CropHandle.Left, Offset(500f, 0f), bounds, 20f))
        // A corner moves two edges at once.
        assertEquals(Rect(60f, 40f, 350f, 150f), dragCropRect(rect, CropHandle.TopLeft, Offset(10f, -10f), bounds, 20f))
    }

    @Test
    fun movingKeepsTheSizeAndStaysInside() {
        val bounds = Rect(0f, 0f, 400f, 200f)
        val rect = Rect(50f, 50f, 150f, 150f)

        val moved = dragCropRect(rect, CropHandle.Move, Offset(1000f, 1000f), bounds, 20f)

        assertEquals(Rect(300f, 100f, 400f, 200f), moved)
        assertEquals(rect.size, moved.size)
    }

    @Test
    fun minimumSizeLargerThanTheBoundsDoesNotThrow() {
        val bounds = Rect(0f, 0f, 30f, 30f)
        val result = dragCropRect(Rect(0f, 0f, 30f, 30f), CropHandle.Right, Offset(-100f, 0f), bounds, 100f)
        assertTrue(result.width >= 0f)
    }

    @Test
    fun snappingRoundsOutwardsAndClipsToTheImage() {
        assertEquals(Rect(10f, 0f, 400f, 121f), Rect(10.6f, -5f, 420f, 120.2f).snappedTo(image))
    }

    private fun assertClose(expected: Offset, actual: Offset) {
        assertTrue(abs(expected.x - actual.x) < 0.01f && abs(expected.y - actual.y) < 0.01f, "expected $expected, was $actual")
    }
}
