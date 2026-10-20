package io.github.antonudovychenko.bugreport

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class BugReportControllerTest {
    private val device = DeviceInfo(BugReportPlatform.Desktop, "TestOS", "1.0")

    @Test
    fun openIsIdempotentAndBumpsTheGenerationOnce() {
        val controller = BugReportController()

        controller.open()
        controller.open()

        assertTrue(controller.isOpen)
        assertEquals(1, controller.openGeneration)
    }

    @Test
    fun aBlankDescriptionCannotBeSent() = runTest {
        val controller = BugReportController().apply { open() }
        var sent = false

        val result = controller.submit(BugReportSender { sent = true }, { emptyMap() }, { ByteArray(0) }, { device })

        assertFalse(result)
        assertFalse(sent)
        assertTrue(controller.isOpen)
    }

    @Test
    fun submitSendsMarkdownLogsAndMetadataThenClosesAndClears() = runTest {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("**Crash** when saving a `record`")
        controller.logsSnapshot = "INFO [App] started\n"
        var received: BugReport? = null

        val result = controller.submit(
            sender = { received = it },
            metadata = { mapOf("buildVersion" to "1.2.3") },
            encodeImage = { ByteArray(0) },
            device = { device },
        )

        assertTrue(result)
        val report = received!!
        assertEquals("**Crash** when saving a `record`", report.description)
        assertEquals("Crash when saving a record", report.plainTextDescription)
        assertEquals("INFO [App] started\n", report.logs)
        assertEquals(mapOf("buildVersion" to "1.2.3"), report.metadata)
        assertEquals(device, report.device)
        assertFalse(controller.isOpen)
        assertEquals("", controller.description.annotatedString.text)
    }

    @Test
    fun untickingLogsLeavesThemOut() = runTest {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("Broken")
        controller.logsSnapshot = "secret-ish diagnostics"
        controller.includeLogs = false
        var received: BugReport? = null

        controller.submit({ received = it }, { emptyMap() }, { ByteArray(0) }, { device })

        assertNull(received!!.logs)
    }

    @Test
    fun imagesAreEncodedInOrderAndNamed() = runTest {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("Two images")
        controller.addImages(listOf(ImageBitmap(2, 2), ImageBitmap(3, 3)), maxImages = 4)
        var received: BugReport? = null

        controller.submit({ received = it }, { emptyMap() }, { bitmap -> byteArrayOf(bitmap.width.toByte()) }, { device })

        val images = received!!.images
        assertEquals(listOf("screenshot_0.jpg", "screenshot_1.jpg"), images.map { it.fileName })
        assertEquals(listOf(2.toByte(), 3.toByte()), images.map { it.bytes.single() })
        assertTrue(images.all { it.mimeType == "image/jpeg" })
    }

    @Test
    fun aFailedSendKeepsTheDraftAndShowsTheSendersMessage() = runTest {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("Keep me")

        val result = controller.submit(
            sender = { throw BugReportSendException("Too many reports, try later") },
            metadata = { emptyMap() },
            encodeImage = { ByteArray(0) },
            device = { device },
        )

        assertFalse(result)
        assertTrue(controller.isOpen)
        assertFalse(controller.sending)
        assertEquals("Keep me", controller.description.toText().trim())
        assertEquals(BugReportError.SendFailed("Too many reports, try later"), controller.error)
    }

    @Test
    fun anUnexpectedFailureShowsTheGenericMessage() = runTest {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("x")

        controller.submit({ error("boom") }, { emptyMap() }, { ByteArray(0) }, { device })

        assertEquals(BugReportError.SendFailed(null), controller.error)
    }

    @Test
    fun imagesNeverExceedTheLimitAndTheScreenshotGoesFirst() {
        val controller = BugReportController().apply { open() }
        val picked = List(5) { ImageBitmap(1 + it, 1) }
        val screenshot = ImageBitmap(10, 1)

        controller.addImages(picked, maxImages = 3)
        controller.addImages(listOf(screenshot), maxImages = 3, atStart = true)
        assertEquals(listOf(1, 2, 3), controller.images.map { it.width })

        controller.removeImage(0)
        controller.addImages(listOf(screenshot), maxImages = 3, atStart = true)
        assertEquals(listOf(10, 2, 3), controller.images.map { it.width })
    }

    @Test
    fun dismissClosesAndClearsTheDraft() {
        val controller = BugReportController().apply { open() }
        controller.description.setMarkdown("draft")
        controller.addImages(listOf(ImageBitmap(1, 1)), maxImages = 4)

        controller.dismiss()

        assertFalse(controller.isOpen)
        assertTrue(controller.images.isEmpty())
        assertEquals("", controller.description.annotatedString.text)
    }
}
