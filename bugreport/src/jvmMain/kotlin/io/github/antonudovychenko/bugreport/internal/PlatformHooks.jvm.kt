package io.github.antonudovychenko.bugreport.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
internal actual fun ObserveSystemScreenshots(enabled: Boolean, onScreenshot: () -> Unit) = Unit

private val PickableExtensions = setOf("png", "jpg", "jpeg", "webp", "bmp", "gif")

@Composable
internal actual fun PlatformImagePicker(
    maxItems: Int,
    onPicked: (List<ByteArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val latestOnPicked by rememberUpdatedState(onPicked)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(Unit) {
        val frame = Frame()
        val files = try {
            val dialog = FileDialog(frame, null as String?, FileDialog.LOAD).apply {
                isMultipleMode = maxItems > 1
                setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in PickableExtensions }
            }
            // A modal AWT dialog pumps events while it is open, so this does not freeze the UI.
            dialog.isVisible = true
            dialog.files.orEmpty().toList()
        } finally {
            frame.dispose()
        }
        val images = withContext(Dispatchers.IO) {
            files.take(maxItems.coerceAtLeast(1)).mapNotNull { file: File -> runCatching { file.readBytes() }.getOrNull() }
        }
        if (images.isEmpty()) latestOnDismiss() else latestOnPicked(images)
    }
}
