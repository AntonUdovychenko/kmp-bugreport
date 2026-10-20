package io.github.antonudovychenko.bugreport

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.click
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.antonudovychenko.bugreport.editor.FitTransform
import io.github.antonudovychenko.bugreport.editor.ImageMarkupEditorCanvasTag
import io.github.antonudovychenko.bugreport.editor.ImageMarkupEditorContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class ImageMarkupEditorTest {
    private val image = ImageBitmap(400, 300).also {
        Canvas(it).drawRect(Rect(0f, 0f, 400f, 300f), Paint().apply { color = Color.White })
    }

    @Test
    fun doneWithoutChangesReturnsTheSameImage() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        onNodeWithText("Done").performClick()

        assertSame(image, result)
    }

    @Test
    fun draggingTheCropCornerAndPressingDoneCropsTheImage() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        onNodeWithText("Crop").performClick()
        val destination = imageOnScreen()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            swipe(destination.topLeft + Offset(2f, 2f), destination.topLeft + Offset(80f, 60f), durationMillis = 300)
        }
        onNodeWithText("Done").performClick()

        val cropped = assertNotNull(result)
        assertTrue(cropped.width in 1 until image.width, "width ${cropped.width}")
        assertTrue(cropped.height in 1 until image.height, "height ${cropped.height}")
    }

    @Test
    fun drawingBakesARedStrokeAtTheOriginalSize() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        val destination = imageOnScreen()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            swipe(destination.center - Offset(60f, 0f), destination.center + Offset(60f, 0f), durationMillis = 300)
        }
        onNodeWithText("Done").performClick()

        val drawn = assertNotNull(result).toPixelMap()
        assertEquals(image.width, drawn.width)
        val middle = drawn[drawn.width / 2, drawn.height / 2]
        assertTrue(middle.red > 0.8f && middle.green < 0.4f, "expected the pen colour, was $middle")
    }

    @Test
    fun undoRemovesTheLastStep() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        val destination = imageOnScreen()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            swipe(destination.center - Offset(60f, 0f), destination.center + Offset(60f, 0f), durationMillis = 300)
        }
        onNodeWithContentDescription("Undo").performClick()
        onNodeWithText("Done").performClick()

        assertSame(image, result)
    }

    @Test
    fun aStrokeStartsWhereThePointerWentDown() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        // One coarse move, the way a browser or a fast flick delivers it. A drag detector that waits
        // for the touch slop starts the stroke at the move, losing everything before it.
        val destination = imageOnScreen()
        val start = destination.center - Offset(100f, 0f)
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            down(start)
            moveTo(destination.center + Offset(100f, 0f))
            up()
        }
        onNodeWithText("Done").performClick()

        val drawn = assertNotNull(result).toPixelMap()
        val scale = image.width / destination.width
        val atStart = drawn[((start.x - destination.left) * scale).toInt() + 2, ((start.y - destination.top) * scale).toInt()]
        assertTrue(atStart.red > 0.8f && atStart.green < 0.4f, "the stroke should begin at the press point, was $atStart")
    }

    @Test
    fun aCropHandleIsGrabbedWhereThePointerWentDown() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        onNodeWithText("Crop").performClick()
        val destination = imageOnScreen()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            down(destination.topLeft + Offset(2f, 2f))
            moveTo(destination.topLeft + Offset(80f, 60f))
            up()
        }
        onNodeWithText("Done").performClick()

        val cropped = assertNotNull(result)
        assertTrue(cropped.width < image.width && cropped.height < image.height, "was ${cropped.width}x${cropped.height}")
    }

    @Test
    fun aTapLeavesADot() = runComposeUiTest {
        var result: ImageBitmap? = null
        setEditor(onDone = { result = it })

        val destination = imageOnScreen()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput { click(destination.center) }
        onNodeWithText("Done").performClick()

        val dot = assertNotNull(result).toPixelMap().let { it[it.width / 2, it.height / 2] }
        assertTrue(dot.red > 0.8f && dot.green < 0.4f, "expected the pen colour, was $dot")
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setEditor(onDone: (ImageBitmap) -> Unit) {
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme {
                    ImageMarkupEditorContent(
                        image = image,
                        onDone = onDone,
                        onCancel = {},
                        penColor = BugReportConfig.DefaultPenColor,
                        penWidth = 8.dp,
                    )
                }
            }
        }
        waitForIdle()
    }

    /** Where the editor draws the image inside its canvas: fitted, after the 16 dp inset. */
    private fun androidx.compose.ui.test.ComposeUiTest.imageOnScreen(): Rect {
        val size = onNodeWithTag(ImageMarkupEditorCanvasTag).fetchSemanticsNode().size
        val inset = 16f
        return FitTransform(
            source = Rect(Offset.Zero, Size(image.width.toFloat(), image.height.toFloat())),
            box = Rect(inset, inset, size.width - inset, size.height - inset),
        ).destination
    }
}
