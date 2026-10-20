package io.github.antonudovychenko.bugreport

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import io.github.antonudovychenko.bugreport.internal.BugReportTags
import io.github.antonudovychenko.bugreport.internal.decodeImage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class BugReportHostTest {
    @Test
    fun openingCapturesTheContentAndSendingDeliversTheReport() = runComposeUiTest {
        val controller = BugReportController()
        var received: BugReport? = null
        setContent {
            MaterialTheme {
                BugReportHost(
                    sender = { received = it },
                    controller = controller,
                    logs = { "INFO [Test] hello\n" },
                    metadata = { mapOf("buildVersion" to "9.9.9") },
                ) {
                    Box(Modifier.fillMaxSize().background(Color.Magenta)) { Text("App content") }
                }
            }
        }

        controller.open()
        waitForIdle()
        waitUntil(timeoutMillis = 5_000) { controller.images.isNotEmpty() }

        onNodeWithText("Report a bug").assertIsDisplayed()
        onNodeWithText("Attach diagnostic logs").assertIsDisplayed()
        onNodeWithTag(BugReportTags.Thumbnail).assertIsDisplayed()
        onNodeWithTag(BugReportTags.Send).assertIsNotEnabled()

        runOnIdle { controller.description.setMarkdown("The *save* button does nothing") }
        onNodeWithTag(BugReportTags.Send).assertIsEnabled().performClick()
        waitUntil(timeoutMillis = 5_000) { received != null }

        val report = assertNotNull(received)
        assertEquals("The *save* button does nothing", report.description)
        assertEquals("INFO [Test] hello\n", report.logs)
        assertEquals(mapOf("buildVersion" to "9.9.9"), report.metadata)
        assertEquals(BugReportPlatform.Desktop, report.device.platform)
        // The capture is the app content, painted magenta, encoded as a real JPEG.
        val screenshot = decodeImage(report.images.single().bytes, maxDimension = 4096).toPixelMap()
        val corner = screenshot[screenshot.width - 4, screenshot.height - 4]
        assertTrue(corner.red > 0.85f && corner.green < 0.15f && corner.blue > 0.85f, "expected magenta, was $corner")
        assertFalse(controller.isOpen)
        onNodeWithText("Report sent").assertIsDisplayed()
    }
}
