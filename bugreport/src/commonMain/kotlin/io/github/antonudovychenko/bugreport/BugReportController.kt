package io.github.antonudovychenko.bugreport

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.mohamedrejeb.richeditor.model.RichTextState
import kotlinx.coroutines.CancellationException

/**
 * The handle an app uses to open the bug report form, e.g. from a "Report a bug" menu item.
 *
 * It is a plain object, so it can live outside composition — in a DI container, next to a shake
 * detector, wherever the trigger is — and be passed to [BugReportHost]. Use it from the main thread.
 */
@Stable
public class BugReportController {

    /** Whether the report form (or its image editor) is showing. */
    public var isOpen: Boolean by mutableStateOf(false)
        private set

    /** Opens an empty report. Does nothing while one is already open. */
    public fun open() {
        if (isOpen) return
        resetDraft()
        isOpen = true
        openGeneration++
    }

    /** Closes the form and discards the draft. Ignored while a report is being sent. */
    public fun dismiss() {
        if (sending) return
        isOpen = false
        resetDraft()
    }

    // ---- Draft state, driven by BugReportHost. Internal so the public surface stays small. ----

    /** Bumped by every [open], so the host runs its per-opening work (capture, log snapshot) once. */
    internal var openGeneration by mutableIntStateOf(0)
        private set

    internal val description: RichTextState = RichTextState()
    internal val images = mutableStateListOf<ImageBitmap>()
    internal var capturing by mutableStateOf(false)
    internal var sending by mutableStateOf(false)
        private set
    internal var error by mutableStateOf<BugReportError?>(null)
    internal var logsSnapshot by mutableStateOf<String?>(null)
    internal var includeLogs by mutableStateOf(true)
    internal var showSuccess by mutableStateOf(false)

    internal val canSend: Boolean
        get() = !sending && description.annotatedString.text.isNotBlank()

    internal fun addImages(bitmaps: List<ImageBitmap>, maxImages: Int, atStart: Boolean = false) {
        val room = (maxImages - images.size).coerceAtLeast(0)
        val accepted = bitmaps.take(room)
        if (atStart) images.addAll(0, accepted) else images.addAll(accepted)
    }

    internal fun replaceImage(index: Int, bitmap: ImageBitmap) {
        if (index in images.indices) images[index] = bitmap
    }

    internal fun removeImage(index: Int) {
        if (index in images.indices) images.removeAt(index)
    }

    /**
     * Builds the report and hands it to [sender]. On success the form closes and the draft is
     * cleared; on failure it stays open with the error shown, so nothing the user typed is lost.
     */
    internal suspend fun submit(
        sender: BugReportSender,
        metadata: () -> Map<String, String>,
        encodeImage: suspend (ImageBitmap) -> ByteArray,
        device: () -> DeviceInfo = { DeviceInfo.current },
    ): Boolean {
        if (!canSend) return false
        sending = true
        error = null
        try {
            val bitmaps = images.toList()
            val report = BugReport(
                description = description.toMarkdown().trim(),
                plainTextDescription = description.toText().trim(),
                images = bitmaps.mapIndexed { index, bitmap ->
                    BugReportImage(bytes = encodeImage(bitmap), fileName = "screenshot_$index.jpg")
                },
                logs = logsSnapshot?.takeIf { includeLogs },
                device = device(),
                metadata = metadata(),
            )
            sender.send(report)
        } catch (cancelled: CancellationException) {
            sending = false
            throw cancelled
        } catch (failure: Exception) {
            sending = false
            error = BugReportError.SendFailed((failure as? BugReportSendException)?.userMessage)
            return false
        }
        sending = false
        isOpen = false
        resetDraft()
        return true
    }

    private fun resetDraft() {
        description.clear()
        images.clear()
        capturing = false
        error = null
        logsSnapshot = null
    }
}

/** Remembers a [BugReportController] for a [BugReportHost] whose trigger lives in composition. */
@Composable
public fun rememberBugReportController(): BugReportController = remember { BugReportController() }

/** Problems shown inline in the form. */
internal sealed interface BugReportError {
    /** [message] is the sender's [BugReportSendException.userMessage], or null for the generic text. */
    data class SendFailed(val message: String?) : BugReportError
    data object CaptureFailed : BugReportError
    data object ImageUnreadable : BugReportError
}
