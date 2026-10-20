package io.github.antonudovychenko.bugreport.internal

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.antonudovychenko.bugreport.BugReportController
import io.github.antonudovychenko.bugreport.BugReportError
import io.github.antonudovychenko.bugreport.resources.Res
import io.github.antonudovychenko.bugreport.resources.bugreport_add_image
import io.github.antonudovychenko.bugreport.resources.bugreport_attach_logs
import io.github.antonudovychenko.bugreport.resources.bugreport_capture_failed
import io.github.antonudovychenko.bugreport.resources.bugreport_close
import io.github.antonudovychenko.bugreport.resources.bugreport_description_placeholder
import io.github.antonudovychenko.bugreport.resources.bugreport_image_unreadable
import io.github.antonudovychenko.bugreport.resources.bugreport_remove_image
import io.github.antonudovychenko.bugreport.resources.bugreport_screenshot
import io.github.antonudovychenko.bugreport.resources.bugreport_send
import io.github.antonudovychenko.bugreport.resources.bugreport_send_failed
import io.github.antonudovychenko.bugreport.resources.bugreport_title
import org.jetbrains.compose.resources.stringResource

private val FormMaxWidth = 560.dp
private val ThumbnailSize = 80.dp

/** Test tags for the form, for apps that drive it from UI tests. */
internal object BugReportTags {
    const val Form = "bugreport-form"
    const val Send = "bugreport-send"
    const val AddImage = "bugreport-add-image"
    const val Thumbnail = "bugreport-thumbnail"
}

/** The centred, rounded report dialog: header, Markdown description, images, log opt-in. */
@Composable
internal fun BugReportForm(
    controller: BugReportController,
    maxImages: Int,
    allowImagePicking: Boolean,
    onSubmit: () -> Unit,
    onAddImage: () -> Unit,
    onEditImage: (Int) -> Unit,
) {
    Dialog(
        onDismissRequest = controller::dismiss,
        // Closing discards the draft, so a stray tap beside the form must not close it; the close
        // button and Back/Escape still do.
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false),
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = FormMaxWidth)
                .fillMaxWidth()
                .padding(16.dp)
                .imePadding()
                .testTag(BugReportTags.Form)
                .onPreviewKeyEvent { event ->
                    if (isSubmitShortcut(event.key, event.type, event.isMetaPressed, event.isCtrlPressed) && controller.canSend) {
                        onSubmit()
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                Header(controller = controller, onSubmit = onSubmit)
                Spacer(Modifier.height(12.dp))
                MarkdownField(
                    state = controller.description,
                    placeholder = stringResource(Res.string.bugreport_description_placeholder),
                    modifier = Modifier.fillMaxWidth(),
                )
                controller.error?.let { error ->
                    Text(
                        text = error.message(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (controller.capturing) CapturingTile()
                    controller.images.forEachIndexed { index, bitmap ->
                        Thumbnail(
                            bitmap = bitmap,
                            onClick = { onEditImage(index) },
                            onRemove = { controller.removeImage(index) },
                            enabled = !controller.sending,
                        )
                    }
                    if (allowImagePicking && controller.images.size < maxImages && !controller.sending) {
                        AddImageTile(onClick = onAddImage)
                    }
                }
                if (controller.logsSnapshot != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = !controller.sending) { controller.includeLogs = !controller.includeLogs },
                    ) {
                        Checkbox(
                            checked = controller.includeLogs,
                            onCheckedChange = { controller.includeLogs = it },
                            enabled = !controller.sending,
                        )
                        Text(
                            text = stringResource(Res.string.bugreport_attach_logs),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(controller: BugReportController, onSubmit: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = controller::dismiss, enabled = !controller.sending) {
            Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.bugreport_close))
        }
        Text(
            text = stringResource(Res.string.bugreport_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onSubmit,
            enabled = controller.canSend,
            modifier = Modifier.testTag(BugReportTags.Send),
        ) {
            if (controller.sending) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(Res.string.bugreport_send))
            }
        }
    }
}

@Composable
private fun BugReportError.message(): String = when (this) {
    is BugReportError.SendFailed -> message ?: stringResource(Res.string.bugreport_send_failed)
    BugReportError.CaptureFailed -> stringResource(Res.string.bugreport_capture_failed)
    BugReportError.ImageUnreadable -> stringResource(Res.string.bugreport_image_unreadable)
}

@Composable
private fun Thumbnail(bitmap: ImageBitmap, onClick: () -> Unit, onRemove: () -> Unit, enabled: Boolean) {
    Box(modifier = Modifier.size(ThumbnailSize + 8.dp).padding(top = 8.dp, end = 8.dp)) {
        Image(
            bitmap = bitmap,
            contentDescription = stringResource(Res.string.bugreport_screenshot),
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(ThumbnailSize)
                .clip(RoundedCornerShape(8.dp))
                .clickable(enabled = enabled, onClick = onClick)
                .testTag(BugReportTags.Thumbnail),
        )
        if (enabled) {
            // A plain clickable Box, not an IconButton: IconButton pads itself out to the 48 dp
            // minimum touch target and the badge would draw at twice its size over the image.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(Res.string.bugreport_remove_image),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun CapturingTile() {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(ThumbnailSize)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
    }
}

@Composable
private fun AddImageTile(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(top = 8.dp)
            .size(ThumbnailSize)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .testTag(BugReportTags.AddImage),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.AddPhotoAlternate,
            contentDescription = stringResource(Res.string.bugreport_add_image),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Cmd+Enter / Ctrl+Enter sends; a plain Enter stays a newline in the description. */
internal fun isSubmitShortcut(
    key: Key,
    eventType: KeyEventType,
    isMetaPressed: Boolean,
    isCtrlPressed: Boolean,
): Boolean = eventType == KeyEventType.KeyDown && key == Key.Enter && (isMetaPressed || isCtrlPressed)

/**
 * The form is unmounted for as long as the image editor is opening or open. On iOS, keeping both
 * dialogs mounted leaves the description field as the native first responder, so the keyboard
 * survives every request to hide it and sits on top of the editor.
 */
internal fun shouldShowForm(isOpen: Boolean, pendingEditingIndex: Int?, editingIndex: Int?): Boolean =
    isOpen && pendingEditingIndex == null && editingIndex == null

/** Drops focus and the keyboard before handing over to the editor; order matters on iOS. */
internal fun openImageEditor(index: Int, clearFocus: () -> Unit, hideKeyboard: () -> Unit, selectImage: (Int) -> Unit) {
    clearFocus()
    hideKeyboard()
    selectImage(index)
}
