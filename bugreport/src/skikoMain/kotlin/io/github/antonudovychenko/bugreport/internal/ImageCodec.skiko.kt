package io.github.antonudovychenko.bugreport.internal

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asSkiaBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image

internal actual fun decodeImage(bytes: ByteArray, maxDimension: Int): ImageBitmap {
    val image = Image.makeFromEncoded(bytes)
    val bitmap = try {
        image.toComposeImageBitmap()
    } finally {
        image.close()
    }
    return bitmap.scaledToMax(maxDimension)
}

internal actual fun ImageBitmap.encodeJpeg(quality: Int): ByteArray {
    val image = Image.makeFromBitmap(asSkiaBitmap())
    try {
        val data = checkNotNull(image.encodeToData(EncodedImageFormat.JPEG, quality.coerceIn(0, 100))) {
            "Skia could not encode the image as JPEG"
        }
        try {
            return data.bytes
        } finally {
            data.close()
        }
    } finally {
        image.close()
    }
}

internal actual fun ImageBitmap.toSoftwareBitmap(): ImageBitmap = this
