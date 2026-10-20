package io.github.antonudovychenko.bugreport

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import io.github.antonudovychenko.bugreport.editor.ImageMarkupEditor
import io.github.antonudovychenko.bugreport.internal.BugReportForm
import io.github.antonudovychenko.bugreport.internal.ObserveSystemScreenshots
import io.github.antonudovychenko.bugreport.internal.PlatformImagePicker
import io.github.antonudovychenko.bugreport.internal.decodeImage
import io.github.antonudovychenko.bugreport.internal.encodeJpeg
import io.github.antonudovychenko.bugreport.internal.openImageEditor
import io.github.antonudovychenko.bugreport.internal.scaledToMax
import io.github.antonudovychenko.bugreport.internal.shouldShowForm
import io.github.antonudovychenko.bugreport.internal.toSoftwareBitmap
import io.github.antonudovychenko.bugreport.internal.LocalBugReportStrings
import io.github.antonudovychenko.bugreport.internal.rememberBugReportStrings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val DefaultConfig = BugReportConfig()

/**
 * Wraps the app's [content] and adds in-app bug reporting on top of it.
 *
 * When the form opens — from [BugReportController.open], or by itself when the user takes a system
 * screenshot (see [BugReportConfig.openOnSystemScreenshot]) — the library captures [content],
 * attaches it, and lets the user describe the problem in Markdown, mark up or crop the images, add
 * more, and send it through [sender].
 *
 * Place it inside your `MaterialTheme`: the form uses the theme, and the theme's background is
 * painted under the capture (JPEG has no transparency; a transparent window would come out black).
 *
 * @param sender delivers the finished [BugReport]. Throw from it to keep the draft and show an error.
 * @param controller opens and closes the form; hoist it to trigger reporting from anywhere.
 * @param config limits and behaviour; see [BugReportConfig].
 * @param logs read once when the form opens, so the logs describe the moment of the bug rather than
 *   the moment of sending. Return `null` for none. When given, the form offers an opt-out checkbox.
 * @param metadata read when the report is sent — build number, environment, user id, ...
 */
@Composable
public fun BugReportHost(
    sender: BugReportSender,
    modifier: Modifier = Modifier,
    controller: BugReportController = rememberBugReportController(),
    config: BugReportConfig = DefaultConfig,
    logs: (() -> String?)? = null,
    metadata: () -> Map<String, String> = { emptyMap() },
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val captureLayer = rememberGraphicsLayer()
    val captureBackground = MaterialTheme.colorScheme.background
    var recording by remember { mutableStateOf(false) }
    val latestLogs by rememberUpdatedState(logs)
    val latestMetadata by rememberUpdatedState(metadata)
    val latestSender by rememberUpdatedState(sender)

    // A persistent graphicsLayer record/drawLayer modifier silently breaks pointer and keyboard
    // input on web and desktop (compose-multiplatform#3230), so the content is routed through the
    // layer only for the couple of frames a capture takes.
    Box(
        modifier = modifier.then(
            if (recording) {
                Modifier.drawWithContent {
                    captureLayer.record {
                        drawRect(captureBackground)
                        this@drawWithContent.drawContent()
                    }
                    drawLayer(captureLayer)
                }
            } else {
                Modifier
            }
        ),
    ) {
        content()
    }

    ObserveSystemScreenshots(enabled = config.openOnSystemScreenshot && !controller.isOpen) {
        controller.open()
    }

    // Once per opening: snapshot the logs, then capture the content under the (already visible)
    // form. The form is its own window, so it is not in the capture, and opening it first means
    // the user sees it immediately instead of after the encode.
    LaunchedEffect(controller.openGeneration) {
        if (!controller.isOpen) return@LaunchedEffect
        controller.includeLogs = config.attachLogsByDefault
        controller.logsSnapshot = runCatching { latestLogs?.invoke() }.getOrNull()?.takeIf(String::isNotBlank)
        if (!config.captureScreenshotOnOpen || config.maxImages == 0) return@LaunchedEffect

        controller.capturing = true
        val screenshot = try {
            recording = true
            withFrameNanos { }
            withFrameNanos { }
            val raw = captureLayer.toImageBitmap()
            recording = false
            withContext(Dispatchers.Default) { raw.toSoftwareBitmap().scaledToMax(config.maxImageDimension) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            null
        } finally {
            recording = false
            controller.capturing = false
        }
        if (!controller.isOpen) return@LaunchedEffect
        if (screenshot != null) {
            controller.addImages(listOf(screenshot), config.maxImages, atStart = true)
        } else {
            controller.error = BugReportError.CaptureFailed
        }
    }

    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var pendingEditingIndex by remember { mutableStateOf<Int?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(controller.isOpen) {
        if (!controller.isOpen) {
            editingIndex = null
            pendingEditingIndex = null
            showPicker = false
        }
    }

    // Unmount the form for a frame before mounting the editor; see shouldShowForm.
    LaunchedEffect(pendingEditingIndex) {
        val index = pendingEditingIndex ?: return@LaunchedEffect
        focusManager.clearFocus(force = true)
        keyboardController?.hide()
        withFrameNanos { }
        editingIndex = index.takeIf { it in controller.images.indices }
        pendingEditingIndex = null
    }

    fun submit() {
        if (!controller.canSend) return
        scope.launch {
            val sent = controller.submit(
                sender = latestSender,
                metadata = latestMetadata,
                encodeImage = { bitmap -> withContext(Dispatchers.Default) { bitmap.encodeJpeg(config.jpegQuality) } },
            )
            if (sent && config.showSuccessDialog) controller.showSuccess = true
        }
    }

    // Resolved here, in the app's composition, and handed to the dialogs; see BugReportStrings.
    val strings = rememberBugReportStrings()
    CompositionLocalProvider(LocalBugReportStrings provides strings) {
        if (shouldShowForm(controller.isOpen, pendingEditingIndex, editingIndex)) {
            BugReportForm(
                controller = controller,
                maxImages = config.maxImages,
                allowImagePicking = config.allowImagePicking,
                onSubmit = ::submit,
                onAddImage = { showPicker = true },
                onEditImage = { index ->
                    openImageEditor(
                        index = index,
                        clearFocus = { focusManager.clearFocus(force = true) },
                        hideKeyboard = { keyboardController?.hide() },
                        selectImage = { pendingEditingIndex = it },
                    )
                },
            )
        }

        editingIndex?.let { index ->
            val image = controller.images.getOrNull(index)
            if (image == null) {
                LaunchedEffect(index) { editingIndex = null }
            } else {
                ImageMarkupEditor(
                    image = image,
                    onDone = { edited ->
                        controller.replaceImage(index, edited)
                        editingIndex = null
                    },
                    onCancel = { editingIndex = null },
                    penColor = config.penColor,
                    penWidth = config.penWidth,
                )
            }
        }

        if (showPicker) {
            PlatformImagePicker(
                maxItems = (config.maxImages - controller.images.size).coerceAtLeast(1),
                onPicked = { encoded ->
                    showPicker = false
                    scope.launch {
                        val decoded = withContext(Dispatchers.Default) {
                            encoded.map { bytes -> runCatching { decodeImage(bytes, config.maxImageDimension) }.getOrNull() }
                        }
                        controller.addImages(decoded.filterNotNull(), config.maxImages)
                        if (decoded.any { it == null }) controller.error = BugReportError.ImageUnreadable
                    }
                },
                onDismiss = { showPicker = false },
            )
        }

        if (controller.showSuccess) {
            AlertDialog(
                onDismissRequest = { controller.showSuccess = false },
                title = { Text(strings.sentTitle) },
                text = { Text(strings.sentMessage) },
                confirmButton = {
                    TextButton(onClick = { controller.showSuccess = false }) {
                        Text(strings.ok)
                    }
                },
            )
        }
    }
}
