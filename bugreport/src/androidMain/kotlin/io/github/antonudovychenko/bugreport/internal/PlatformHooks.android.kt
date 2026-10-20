package io.github.antonudovychenko.bugreport.internal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LifecycleStartEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal actual fun ObserveSystemScreenshots(enabled: Boolean, onScreenshot: () -> Unit) {
    if (!enabled || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
    val activity = LocalContext.current.findActivity() ?: return
    ObserveActivityScreenshots(activity, onScreenshot)
}

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
@Composable
private fun ObserveActivityScreenshots(activity: Activity, onScreenshot: () -> Unit) {
    val latestOnScreenshot by rememberUpdatedState(onScreenshot)
    // START/STOP is the registration window Android documents for this callback. Disposal also
    // covers the form opening and Activity recreation without leaking the old Activity.
    LifecycleStartEffect(activity) {
        val callback = Activity.ScreenCaptureCallback { latestOnScreenshot() }
        // DETECT_SCREEN_CAPTURE is declared in this library's manifest; a host that strips it with
        // tools:node="remove" gets no trigger rather than a crash.
        val registered = runCatching {
            activity.registerScreenCaptureCallback(activity.mainExecutor, callback)
        }.isSuccess
        onStopOrDispose {
            if (registered) activity.unregisterScreenCaptureCallback(callback)
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
internal actual fun PlatformImagePicker(
    maxItems: Int,
    onPicked: (List<ByteArray>) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestOnPicked by rememberUpdatedState(onPicked)
    val latestOnDismiss by rememberUpdatedState(onDismiss)
    // PickMultipleVisualMedia rejects a limit below 2, so ask for at least 2 and trim afterwards.
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems.coerceAtLeast(2)),
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) {
            latestOnDismiss()
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val images = withContext(Dispatchers.IO) {
                uris.take(maxItems.coerceAtLeast(1)).mapNotNull { uri ->
                    runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
                }
            }
            if (images.isEmpty()) latestOnDismiss() else latestOnPicked(images)
        }
    }
    LaunchedEffect(Unit) {
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }
}
