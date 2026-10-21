package io.github.antonudovychenko.bugreport.internal

import androidx.compose.runtime.Composable
import androidx.compose.ui.window.DialogProperties

/**
 * Calls [onScreenshot] when the user takes a system screenshot while [enabled].
 *
 * - Android 14+ (API 34): `Activity.ScreenCaptureCallback`, registered between ON_START and ON_STOP
 *   as the platform requires. Older Android has no reliable signal (watching MediaStore fires for
 *   every saved photo and needs a storage permission), so it is a no-op there.
 * - iOS: `UIApplicationUserDidTakeScreenshotNotification`.
 * - Desktop and web: no such event; a no-op.
 */
@Composable
internal expect fun ObserveSystemScreenshots(enabled: Boolean, onScreenshot: () -> Unit)

/**
 * Presents the platform's image picker once, as soon as it enters composition, and reports the
 * encoded bytes of up to [maxItems] chosen images through [onPicked] — or [onDismiss] if the user
 * chose nothing. Remove it from composition after either callback.
 */
@Composable
internal expect fun PlatformImagePicker(
    maxItems: Int,
    onPicked: (List<ByteArray>) -> Unit,
    onDismiss: () -> Unit,
)

/**
 * Properties for a dialog that covers the whole window, under the system bars, and pads its own
 * content for them. On iOS a dialog is otherwise laid out inside the safe area, so a full-screen
 * background stops short of the status bar and the home indicator and the scrim shows there.
 */
internal expect fun fullScreenDialogProperties(): DialogProperties
