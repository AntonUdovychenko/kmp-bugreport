package io.github.antonudovychenko.bugreport.internal

import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Decodes PNG/JPEG/WebP (and HEIC where the platform can) into a drawable bitmap whose longest side
 * is at most [maxDimension]. Throws when [bytes] are not an image the platform can read.
 *
 * Android decodes straight to the target size and honours EXIF orientation; a 50 MP camera photo
 * decoded at full size first would not fit in the heap of a mid-range phone.
 */
internal expect fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap

/** Encodes as JPEG at [quality] (0..100). JPEG has no alpha: draw an opaque background first. */
internal expect fun ImageBitmap.encodeJpeg(quality: Int): ByteArray

/**
 * Returns pixels a software [Canvas] can read. Android's `GraphicsLayer` captures are HARDWARE
 * bitmaps that a bitmap-backed Canvas cannot draw from, so they are copied; elsewhere a no-op.
 */
internal expect fun ImageBitmap.toSoftwareBitmap(): ImageBitmap

/**
 * A copy scaled so its longest side is at most [maxDimension], aspect ratio preserved; bitmaps
 * already within the limit are returned as-is.
 */
internal fun ImageBitmap.scaledToMax(maxDimension: Int): ImageBitmap {
    val longest = max(width, height)
    if (longest <= maxDimension) return this
    val scale = maxDimension.toFloat() / longest
    val output = ImageBitmap(
        (width * scale).roundToInt().coerceAtLeast(1),
        (height * scale).roundToInt().coerceAtLeast(1),
    )
    Canvas(output).drawImageRect(
        image = this,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(output.width, output.height),
        paint = Paint().apply {
            isAntiAlias = true
            filterQuality = FilterQuality.Medium
        },
    )
    return output
}
