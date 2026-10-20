package io.github.antonudovychenko.bugreport.sample

import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SkikoComposeUiTest
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runSkikoComposeUiTest
import androidx.compose.ui.unit.Density
import io.github.antonudovychenko.bugreport.BugReportController
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Renders the README's desktop images from the real sample: a booking fails, the report opens over
 * it with the capture attached, and a Markdown description is filled in. Writes to build/media/.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopMediaTest {
    @Test
    fun lightReportForm() = renderReportForm(darkTheme = false, file = "desktop-light.png")

    @Test
    fun darkReportForm() = renderReportForm(darkTheme = true, file = "desktop-dark.png")

    private fun renderReportForm(darkTheme: Boolean, file: String) =
        runSkikoComposeUiTest(size = Size(1920f, 1300f), density = Density(2f)) {
            val controller = BugReportController()
            setContent { SampleApp(darkTheme = darkTheme, controller = controller) }
            onAllNodesWithText("Book")[0].performClick()
            waitUntil(timeoutMillis = 5_000) { onAllNodesWithText("Booking failed. Please try again later.").fetchSemanticsNodes().isNotEmpty() }

            controller.open()
            waitUntil(timeoutMillis = 5_000) {
                onAllNodesWithContentDescription("Screenshot").fetchSemanticsNodes().isNotEmpty()
            }
            // Typed through the real toolbar, so the image shows what users actually get.
            val editor = onNode(hasSetTextAction())
            editor.performClick()
            editor.performTextInput("Tapping ")
            onNodeWithContentDescription("Bold").performClick()
            editor.performTextInput("Book")
            onNodeWithContentDescription("Bold").performClick()
            editor.performTextInput(" fails for every trip.")
            editor.performKeyInput { pressKey(Key.Enter) }
            onNodeWithContentDescription("Numbered list").performClick()
            editor.performTextInput("Open Trips")
            editor.performKeyInput { pressKey(Key.Enter) }
            editor.performTextInput("Tap Book on Lisbon")
            waitForIdle()
            save(file)
        }

    private fun SkikoComposeUiTest.save(name: String) {
        val roots = onAllNodes(isRoot())
        val image = roots[roots.fetchSemanticsNodes().lastIndex].captureToImage()
        val data = Image.makeFromBitmap(image.asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
            ?: error("could not encode $name")
        File("build/media/$name").apply { parentFile?.mkdirs() }.writeBytes(data.bytes)
    }
}
