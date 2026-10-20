package io.github.antonudovychenko.bugreport.internal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSNotificationCenter
import platform.Foundation.NSOperationQueue
import platform.PhotosUI.PHPickerConfiguration
import platform.PhotosUI.PHPickerFilter
import platform.PhotosUI.PHPickerResult
import platform.PhotosUI.PHPickerViewController
import platform.PhotosUI.PHPickerViewControllerDelegateProtocol
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationUserDidTakeScreenshotNotification
import platform.UIKit.UIGraphicsImageRenderer
import platform.UIKit.UIGraphicsImageRendererFormat
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.UIKit.UIViewController
import platform.UIKit.UIWindow
import platform.UIKit.UIWindowScene
import platform.UniformTypeIdentifiers.UTTypeImage
import platform.darwin.NSObject
import platform.posix.memcpy
import kotlin.coroutines.resume
import kotlin.math.max

@Composable
internal actual fun ObserveSystemScreenshots(enabled: Boolean, onScreenshot: () -> Unit) {
    if (!enabled) return
    val latestOnScreenshot by rememberUpdatedState(onScreenshot)
    DisposableEffect(Unit) {
        val observer = NSNotificationCenter.defaultCenter.addObserverForName(
            name = UIApplicationUserDidTakeScreenshotNotification,
            `object` = null,
            queue = NSOperationQueue.mainQueue,
        ) { _ -> latestOnScreenshot() }
        onDispose { NSNotificationCenter.defaultCenter.removeObserver(observer) }
    }
}

/** Upper bound for a picked photo before it reaches Skia; the host scales it further. */
private const val PickedImageMaxDimension = 2048.0

@Composable
internal actual fun PlatformImagePicker(
    maxItems: Int,
    onPicked: (List<ByteArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val latestOnPicked by rememberUpdatedState(onPicked)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    // PHPickerViewController holds its delegate weakly; remembering it keeps it alive while shown.
    val delegate = remember {
        object : NSObject(), PHPickerViewControllerDelegateProtocol {
            override fun picker(picker: PHPickerViewController, didFinishPicking: List<*>) {
                picker.dismissViewControllerAnimated(true, null)
                val results = didFinishPicking.filterIsInstance<PHPickerResult>().take(maxItems.coerceAtLeast(1))
                if (results.isEmpty()) {
                    latestOnDismiss()
                    return
                }
                scope.launch {
                    val images = withContext(Dispatchers.IO) { results.mapNotNull { it.loadJpegBytes() } }
                    if (images.isEmpty()) latestOnDismiss() else latestOnPicked(images)
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        val picker = PHPickerViewController(
            configuration = PHPickerConfiguration().apply {
                selectionLimit = maxItems.coerceAtLeast(1).toLong()
                filter = PHPickerFilter.imagesFilter
            },
        )
        picker.delegate = delegate
        val presenter = topViewController() ?: run {
            latestOnDismiss()
            return@LaunchedEffect
        }
        presenter.presentViewController(picker, animated = true, completion = null)
    }
}

private fun topViewController(): UIViewController? {
    val window = UIApplication.sharedApplication.connectedScenes
        .filterIsInstance<UIWindowScene>()
        .flatMap { scene -> scene.windows.filterIsInstance<UIWindow>() }
        .firstOrNull { it.isKeyWindow() }
        ?: UIApplication.sharedApplication.windows.filterIsInstance<UIWindow>().firstOrNull()
    var top = window?.rootViewController
    while (top?.presentedViewController != null) top = top.presentedViewController
    return top
}

private suspend fun PHPickerResult.loadJpegBytes(): ByteArray? {
    val data = suspendCancellableCoroutine<NSData?> { continuation ->
        itemProvider.loadDataRepresentationForTypeIdentifier(UTTypeImage.identifier) { data, _ ->
            continuation.resume(data)
        }
    } ?: return null
    val image = UIImage(data = data)
    return image.normalized()?.let { UIImageJPEGRepresentation(it, 0.9) }?.toByteArray()
}

/**
 * Redraws the photo upright (applying its EXIF orientation, which Skia would ignore) and no larger
 * than [PickedImageMaxDimension]. Re-encoding as JPEG also turns HEIC, which Skia cannot decode,
 * into something it can.
 */
@OptIn(ExperimentalForeignApi::class)
private fun UIImage.normalized(): UIImage? {
    val (width, height) = size.useContents { width to height }
    if (width <= 0.0 || height <= 0.0) return null
    val scale = minOf(1.0, PickedImageMaxDimension / max(width, height))
    val format = UIGraphicsImageRendererFormat.defaultFormat().apply { this.scale = 1.0 }
    val target = CGSizeMake(width * scale, height * scale)
    return UIGraphicsImageRenderer(size = target, format = format).imageWithActions { _ ->
        drawInRect(CGRectMake(0.0, 0.0, width * scale, height * scale))
    }
}

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
private fun NSData.toByteArray(): ByteArray? {
    val length = length.toInt()
    if (length == 0) return null
    return ByteArray(length).apply {
        usePinned { pinned -> memcpy(pinned.addressOf(0), bytes, this@toByteArray.length) }
    }
}
