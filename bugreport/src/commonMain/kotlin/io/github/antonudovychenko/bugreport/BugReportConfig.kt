package io.github.antonudovychenko.bugreport

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Behaviour knobs for [BugReportHost]. Every value has a default that suits most apps.
 *
 * @property maxImages how many images one report may carry, screenshot included.
 * @property openOnSystemScreenshot open the form when the user takes a system screenshot. Works on
 *   iOS and on Android 14+ (API 34, `Activity.ScreenCaptureCallback`); desktop and web have no such
 *   signal, so open it from a menu item with [BugReportController.open] there.
 * @property captureScreenshotOnOpen attach a capture of the app's content when the form opens.
 * @property allowImagePicking show the "+" tile that adds images from the platform picker.
 * @property maxImageDimension longest side, in pixels, images are scaled down to. Keeps JPEG
 *   encoding fast — a full-resolution encode blocks the single-threaded web runtime for seconds —
 *   and the upload small.
 * @property jpegQuality 0..100 quality for the encoded images.
 * @property penColor colour of the markup pen in the image editor.
 * @property penWidth on-screen width of the markup pen.
 * @property showSuccessDialog confirm a successful send with a dialog.
 * @property attachLogsByDefault initial state of the "Attach diagnostic logs" checkbox.
 */
@Immutable
public class BugReportConfig(
    public val maxImages: Int = 4,
    public val openOnSystemScreenshot: Boolean = true,
    public val captureScreenshotOnOpen: Boolean = true,
    public val allowImagePicking: Boolean = true,
    public val maxImageDimension: Int = 1600,
    public val jpegQuality: Int = 80,
    public val penColor: Color = DefaultPenColor,
    public val penWidth: Dp = 4.dp,
    public val showSuccessDialog: Boolean = true,
    public val attachLogsByDefault: Boolean = true,
) {
    init {
        require(maxImages >= 0) { "maxImages must not be negative" }
        require(maxImageDimension > 0) { "maxImageDimension must be positive" }
        require(jpegQuality in 0..100) { "jpegQuality must be within 0..100" }
    }

    public companion object {
        /** A red that reads on both light and dark screenshots. */
        public val DefaultPenColor: Color = Color(0xFFE53935)
    }
}
