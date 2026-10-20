package io.github.antonudovychenko.bugreport.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.browser.document
import kotlinx.browser.window
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import org.w3c.dom.HTMLInputElement
import org.w3c.dom.events.Event
import org.w3c.files.File
import org.w3c.files.FileList
import org.w3c.files.FileReader
import org.w3c.files.get
import kotlin.coroutines.resume

@Composable
internal actual fun ObserveSystemScreenshots(enabled: Boolean, onScreenshot: () -> Unit) = Unit

/** Browsers do not report a cancelled file dialog; no selection this long after refocus means cancel. */
private const val CancelDetectionDelayMs = 500L

@Composable
internal actual fun PlatformImagePicker(
    maxItems: Int,
    onPicked: (List<ByteArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnPicked by rememberUpdatedState(onPicked)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(Unit) {
        val input = document.createElement("input") as HTMLInputElement
        input.type = "file"
        input.accept = "image/*"
        input.multiple = maxItems > 1
        input.style.display = "none"
        var settled = false

        input.onchange = { _: Event ->
            settled = true
            val files = input.files
            scope.launch {
                val images = readFiles(files, maxItems.coerceAtLeast(1))
                if (images.isEmpty()) latestOnDismiss() else latestOnPicked(images)
            }
            null
        }
        val onFocusBack: (Event) -> Unit = {
            scope.launch {
                delay(CancelDetectionDelayMs)
                if (!settled) {
                    settled = true
                    latestOnDismiss()
                }
            }
        }
        window.addEventListener("focus", onFocusBack)
        document.body?.appendChild(input)
        input.click()

        onDispose {
            window.removeEventListener("focus", onFocusBack)
            input.remove()
        }
    }
}

private suspend fun readFiles(files: FileList?, maxItems: Int): List<ByteArray> {
    if (files == null) return emptyList()
    val result = mutableListOf<ByteArray>()
    for (index in 0 until files.length) {
        if (result.size >= maxItems) break
        val file = files[index] ?: continue
        if (!file.type.startsWith("image/")) continue
        readBytes(file)?.let(result::add)
    }
    return result
}

private suspend fun readBytes(file: File): ByteArray? = suspendCancellableCoroutine { continuation ->
    val reader = FileReader()
    reader.onload = { _: Event ->
        val buffer = reader.result as? ArrayBuffer
        continuation.resume(buffer?.let { Int8Array(it) }?.let { view -> ByteArray(view.length) { view[it] } })
        null
    }
    reader.onerror = { _: Event ->
        continuation.resume(null)
        null
    }
    reader.readAsArrayBuffer(file)
}
