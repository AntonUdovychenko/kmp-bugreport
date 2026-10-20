package io.github.antonudovychenko.bugreport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import io.github.antonudovychenko.bugreport.editor.FitTransform
import io.github.antonudovychenko.bugreport.editor.ImageMarkupEditorCanvasTag
import io.github.antonudovychenko.bugreport.editor.ImageMarkupEditorContent
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import java.io.File
import kotlin.test.Test

/**
 * Renders the form and the editor to `build/screenshots/` for visual review (README, PRs). They
 * assert nothing beyond "it renders"; the behaviour is covered by the other tests.
 */
@OptIn(ExperimentalTestApi::class)
class ScreenshotsTest {
    @Test
    fun reportForm() = runComposeUiTest {
        val controller = BugReportController()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(colorScheme = lightColorScheme()) {
                    BugReportHost(sender = {}, controller = controller, logs = { "INFO [App] started\n" }) {
                        FakeApp()
                    }
                }
            }
        }
        controller.open()
        waitUntil(timeoutMillis = 5_000) { controller.images.isNotEmpty() }
        runOnIdle {
            controller.description.setMarkdown(
                "Tapping **Save** on a new record does nothing.\n\n" +
                    "1. Open a car\n2. Add a `fuel` record\n3. Tap *Save*",
            )
        }
        waitForIdle()
        saveLastRoot("form.png")
    }

    @Test
    fun editorCropMode() = runComposeUiTest {
        val image = fakeScreenshot()
        setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f)) {
                MaterialTheme(colorScheme = darkColorScheme()) {
                    // What ImageMarkupEditor puts around the content, minus the Dialog window.
                    Surface(color = Color.Black) {
                        ImageMarkupEditorContent(image, onDone = {}, onCancel = {}, BugReportConfig.DefaultPenColor, 4.dp)
                    }
                }
            }
        }
        waitForIdle()
        val size = onNodeWithTag(ImageMarkupEditorCanvasTag).fetchSemanticsNode().size
        val transform = FitTransform(
            source = Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            box = Rect(16f, 16f, size.width - 16f, size.height - 16f),
        )
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            swipe(transform.toScreen(Offset(60f, 150f)), transform.toScreen(Offset(300f, 150f)), 300)
            swipe(transform.toScreen(Offset(300f, 150f)), transform.toScreen(Offset(300f, 230f)), 300)
        }
        onNodeWithText("Crop").performClick()
        onNodeWithTag(ImageMarkupEditorCanvasTag).performTouchInput {
            val corner = transform.destination.topLeft
            swipe(corner + Offset(2f, 2f), corner + Offset(30f, 70f), 300)
        }
        waitForIdle()
        saveLastRoot("editor-crop.png")
    }

    private fun ComposeUiTest.saveLastRoot(name: String) {
        val roots = onAllNodes(isRoot())
        val last = roots[roots.fetchSemanticsNodes().lastIndex]
        val data = Image.makeFromBitmap(last.captureToImage().asSkiaBitmap()).encodeToData(EncodedImageFormat.PNG)
            ?: error("could not encode $name")
        File("build/screenshots/$name").apply { parentFile?.mkdirs() }.writeBytes(data.bytes)
    }

    private fun fakeScreenshot(): ImageBitmap = ImageBitmap(360, 640).also { bitmap ->
        val canvas = Canvas(bitmap)
        fun rect(l: Float, t: Float, r: Float, b: Float, color: Color) =
            canvas.drawRect(Rect(l, t, r, b), Paint().apply { this.color = color })
        rect(0f, 0f, 360f, 640f, Color(0xFFF4F6FA))
        rect(0f, 0f, 360f, 92f, Color(0xFF485E98))
        rect(20f, 120f, 340f, 310f, Color.White)
        rect(20f, 334f, 340f, 454f, Color.White)
        rect(42f, 362f, 286f, 374f, Color(0xFFC6CCD6))
    }
}

@androidx.compose.runtime.Composable
private fun FakeApp() {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F6FA))) {
        Box(Modifier.background(Color(0xFF485E98)).padding(24.dp)) {
            Text("My Garage", color = Color.White, style = MaterialTheme.typography.headlineSmall)
        }
        Text("Fuel · 42.1 L · €78.40", modifier = Modifier.padding(24.dp))
    }
}
