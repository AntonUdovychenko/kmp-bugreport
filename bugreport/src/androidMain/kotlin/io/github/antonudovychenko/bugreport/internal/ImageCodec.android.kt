package io.github.antonudovychenko.bugreport.internal

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.roundToInt

internal actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap {
    val bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        // ImageDecoder applies EXIF orientation (BitmapFactory does not, so portrait photos would
        // arrive sideways) and decodes directly at the reduced size.
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = max(info.size.width, info.size.height)
            if (longest > maxDimension) {
                val scale = maxDimension.toFloat() / longest
                decoder.setTargetSize(
                    (info.size.width * scale).roundToInt().coerceAtLeast(1),
                    (info.size.height * scale).roundToInt().coerceAtLeast(1),
                )
            }
        }
    } else {
        decodeSampled(bytes, maxDimension)
    }
    return bitmap.asImageBitmap().scaledToMax(maxDimension)
}

private fun decodeSampled(bytes: ByteArray, maxDimension: Int): Bitmap {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sampleSize = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sampleSize * 2) >= maxDimension) sampleSize *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
    return requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)) {
        "Not a decodable image"
    }
}

internal actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray =
    ByteArrayOutputStream().use { output ->
        check(toSoftwareBitmap().asAndroidBitmap().compress(Bitmap.CompressFormat.JPEG, quality.coerceIn(0, 100), output)) {
            "Bitmap.compress refused to encode the image"
        }
        output.toByteArray()
    }

internal actual fun ImageBitmap.toSoftwareBitmap(): ImageBitmap {
    val bitmap = asAndroidBitmap()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && bitmap.config == Bitmap.Config.HARDWARE) {
        checkNotNull(bitmap.copy(Bitmap.Config.ARGB_8888, false)).asImageBitmap()
    } else {
        this
    }
}
