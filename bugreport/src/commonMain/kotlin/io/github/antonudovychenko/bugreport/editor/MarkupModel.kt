package io.github.antonudovychenko.bugreport.editor

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min

/*
 * The editor's document model. Every coordinate here is in pixels of the ORIGINAL image, never of
 * the screen: a stroke drawn before a crop stays exactly where it was drawn, a crop can be undone
 * without losing the strokes outside it, and a window resize or rotation cannot shift anything.
 * Screen coordinates exist only inside [FitTransform], at the moment of drawing or hit-testing.
 */

/** One undoable step. */
internal sealed interface MarkupOp {
    /** A freehand line; [width] is in image pixels so it bakes at the thickness it was drawn. */
    class Stroke(val points: List<Offset>, val width: Float, val color: Color) : MarkupOp

    /** Keep only [rect]; always integral and inside the image (see [Rect.snappedTo]). */
    class Crop(val rect: Rect) : MarkupOp
}

/** The part of the image the latest crop kept, or all of it. */
internal fun List<MarkupOp>.visibleRect(imageSize: Size): Rect =
    (lastOrNull { it is MarkupOp.Crop } as MarkupOp.Crop?)?.rect ?: Rect(Offset.Zero, imageSize)

/**
 * Fits [source] (a region of the image) into [box] (screen pixels) the way `ContentScale.Fit` would
 * — centred, letterboxed, aspect preserved — and maps points between the two spaces.
 */
internal class FitTransform(val source: Rect, box: Rect) {
    val scale: Float =
        if (source.width <= 0f || source.height <= 0f) 1f
        else min(box.width / source.width, box.height / source.height)
    private val originX = box.left + (box.width - source.width * scale) / 2f
    private val originY = box.top + (box.height - source.height * scale) / 2f

    fun toScreen(point: Offset): Offset =
        Offset((point.x - source.left) * scale + originX, (point.y - source.top) * scale + originY)

    fun toImage(point: Offset): Offset =
        Offset((point.x - originX) / scale + source.left, (point.y - originY) / scale + source.top)

    fun toScreen(rect: Rect): Rect = Rect(toScreen(rect.topLeft), toScreen(rect.bottomRight))

    /** Where [source] lands on screen. */
    val destination: Rect get() = toScreen(source)
}

/** What a drag on the crop overlay moves. */
internal enum class CropHandle { TopLeft, Top, TopRight, Right, BottomRight, Bottom, BottomLeft, Left, Move }

/**
 * Which part of [rect] (screen pixels) a touch at [point] grabs. Corners win over edges so the
 * small corner targets stay reachable; inside the rectangle moves it; outside grabs nothing.
 */
internal fun hitCropHandle(rect: Rect, point: Offset, touchRadius: Float): CropHandle? {
    fun near(a: Float, b: Float) = abs(a - b) <= touchRadius
    val left = near(point.x, rect.left)
    val right = near(point.x, rect.right)
    val top = near(point.y, rect.top)
    val bottom = near(point.y, rect.bottom)
    val withinX = point.x >= rect.left - touchRadius && point.x <= rect.right + touchRadius
    val withinY = point.y >= rect.top - touchRadius && point.y <= rect.bottom + touchRadius
    return when {
        left && top -> CropHandle.TopLeft
        right && top -> CropHandle.TopRight
        right && bottom -> CropHandle.BottomRight
        left && bottom -> CropHandle.BottomLeft
        top && withinX -> CropHandle.Top
        bottom && withinX -> CropHandle.Bottom
        left && withinY -> CropHandle.Left
        right && withinY -> CropHandle.Right
        rect.contains(point) -> CropHandle.Move
        else -> null
    }
}

/**
 * Applies a drag of [delta] (image pixels) on [handle] to [rect], keeping the result inside
 * [bounds] and at least [minSize] on each side (or as large as [bounds] allows, if smaller).
 */
internal fun dragCropRect(rect: Rect, handle: CropHandle, delta: Offset, bounds: Rect, minSize: Float): Rect {
    if (handle == CropHandle.Move) {
        val dx = delta.x.clamp(bounds.left - rect.left, bounds.right - rect.right)
        val dy = delta.y.clamp(bounds.top - rect.top, bounds.bottom - rect.bottom)
        return rect.translate(dx, dy)
    }
    val min = min(minSize, min(bounds.width, bounds.height))
    var left = rect.left
    var top = rect.top
    var right = rect.right
    var bottom = rect.bottom
    if (handle in LeftHandles) left = (left + delta.x).clamp(bounds.left, right - min)
    if (handle in RightHandles) right = (right + delta.x).clamp(left + min, bounds.right)
    if (handle in TopHandles) top = (top + delta.y).clamp(bounds.top, bottom - min)
    if (handle in BottomHandles) bottom = (bottom + delta.y).clamp(top + min, bounds.bottom)
    return Rect(left, top, right, bottom)
}

private val LeftHandles = setOf(CropHandle.TopLeft, CropHandle.Left, CropHandle.BottomLeft)
private val RightHandles = setOf(CropHandle.TopRight, CropHandle.Right, CropHandle.BottomRight)
private val TopHandles = setOf(CropHandle.TopLeft, CropHandle.Top, CropHandle.TopRight)
private val BottomHandles = setOf(CropHandle.BottomLeft, CropHandle.Bottom, CropHandle.BottomRight)

/** `coerceIn` that tolerates an empty range (it then pins to [low]) instead of throwing. */
private fun Float.clamp(low: Float, high: Float): Float = if (low >= high) low else coerceIn(low, high)

/** Rounds outwards to whole pixels and clips to an image of [imageSize], so baking matches display. */
internal fun Rect.snappedTo(imageSize: Size): Rect = Rect(
    left = floor(left).coerceIn(0f, imageSize.width),
    top = floor(top).coerceIn(0f, imageSize.height),
    right = ceil(right).coerceIn(0f, imageSize.width),
    bottom = ceil(bottom).coerceIn(0f, imageSize.height),
)

/** The path of a stroke, with every point passed through [map] (identity when baking). */
internal inline fun strokePath(points: List<Offset>, map: (Offset) -> Offset): Path = Path().apply {
    val start = map(points.first())
    moveTo(start.x, start.y)
    for (index in 1 until points.size) {
        val point = map(points[index])
        lineTo(point.x, point.y)
    }
}

/**
 * Bakes [ops] into a new bitmap: the visible (cropped) region of [original] with every stroke drawn
 * over it. Returns [original] itself when there is nothing to apply.
 */
internal fun renderMarkup(original: ImageBitmap, ops: List<MarkupOp>): ImageBitmap {
    val imageSize = Size(original.width.toFloat(), original.height.toFloat())
    val visible = ops.visibleRect(imageSize).snappedTo(imageSize)
    val strokes = ops.filterIsInstance<MarkupOp.Stroke>().filter { it.points.isNotEmpty() }
    val isFullImage = visible.width.toInt() == original.width && visible.height.toInt() == original.height
    if (strokes.isEmpty() && isFullImage) return original

    val width = visible.width.toInt().coerceAtLeast(1)
    val height = visible.height.toInt().coerceAtLeast(1)
    val output = ImageBitmap(width, height)
    val canvas = Canvas(output)
    canvas.drawImageRect(
        image = original,
        srcOffset = IntOffset(visible.left.toInt(), visible.top.toInt()),
        srcSize = IntSize(width, height),
        dstOffset = IntOffset.Zero,
        dstSize = IntSize(width, height),
        paint = Paint(),
    )
    canvas.translate(-visible.left, -visible.top)
    strokes.forEach { stroke ->
        if (stroke.points.size == 1) {
            canvas.drawCircle(stroke.points.first(), stroke.width / 2f, Paint().apply {
                color = stroke.color
                style = PaintingStyle.Fill
                isAntiAlias = true
            })
        } else {
            canvas.drawPath(strokePath(stroke.points) { it }, Paint().apply {
                color = stroke.color
                style = PaintingStyle.Stroke
                strokeWidth = stroke.width
                strokeCap = StrokeCap.Round
                strokeJoin = StrokeJoin.Round
                isAntiAlias = true
            })
        }
    }
    return output
}
