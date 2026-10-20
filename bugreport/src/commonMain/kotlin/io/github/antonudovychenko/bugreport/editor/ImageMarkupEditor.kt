package io.github.antonudovychenko.bugreport.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.antonudovychenko.bugreport.BugReportConfig
import io.github.antonudovychenko.bugreport.resources.Res
import io.github.antonudovychenko.bugreport.resources.bugreport_cancel
import io.github.antonudovychenko.bugreport.resources.bugreport_crop
import io.github.antonudovychenko.bugreport.resources.bugreport_crop_hint
import io.github.antonudovychenko.bugreport.resources.bugreport_done
import io.github.antonudovychenko.bugreport.resources.bugreport_draw
import io.github.antonudovychenko.bugreport.resources.bugreport_draw_hint
import io.github.antonudovychenko.bugreport.resources.bugreport_reset
import io.github.antonudovychenko.bugreport.resources.bugreport_undo
import org.jetbrains.compose.resources.stringResource
import kotlin.math.roundToInt

private enum class EditorMode { Draw, Crop }

private val ImageInset = 16.dp
private val CropTouchRadius = 24.dp
private val CropMinSize = 48.dp

/** Test tag of the editor's drawing surface. */
public const val ImageMarkupEditorCanvasTag: String = "bugreport-markup-canvas"

/**
 * A full-screen editor for marking up an image: freehand pen strokes to circle a problem, and a
 * crop to cut away what does not matter (or what is private). Every step can be undone.
 *
 * [onDone] receives a new bitmap with the crop applied and the strokes baked in, or [image] itself
 * when nothing was changed; [onCancel] discards everything.
 *
 * Usable on its own, outside a bug report — it depends on nothing else in this library.
 */
@Composable
public fun ImageMarkupEditor(
    image: ImageBitmap,
    onDone: (ImageBitmap) -> Unit,
    onCancel: () -> Unit,
    penColor: Color = BugReportConfig.DefaultPenColor,
    penWidth: Dp = 4.dp,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
            ImageMarkupEditorContent(
                image = image,
                onDone = onDone,
                onCancel = onCancel,
                penColor = penColor,
                penWidth = penWidth,
            )
        }
    }
}

@Composable
internal fun ImageMarkupEditorContent(
    image: ImageBitmap,
    onDone: (ImageBitmap) -> Unit,
    onCancel: () -> Unit,
    penColor: Color,
    penWidth: Dp,
) {
    val imageSize = remember(image) { Size(image.width.toFloat(), image.height.toFloat()) }
    val ops = remember(image) { mutableStateListOf<MarkupOp>() }
    val currentStroke = remember(image) { mutableStateListOf<Offset>() }
    var mode by remember { mutableStateOf(EditorMode.Draw) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val visible = ops.visibleRect(imageSize)
    // The crop being dragged, in image pixels. Starts as everything currently visible.
    var pendingCrop by remember(image) { mutableStateOf(visible) }
    LaunchedEffect(visible) { pendingCrop = visible }

    val density = LocalDensity.current
    val insetPx = with(density) { ImageInset.toPx() }
    val transform = remember(visible, canvasSize, insetPx) {
        canvasSize.takeIf { it.width > 0 && it.height > 0 }?.let {
            FitTransform(
                source = visible,
                box = Rect(insetPx, insetPx, it.width - insetPx, it.height - insetPx),
            )
        }
    }
    // Gesture handlers outlive recomposition, so they read the latest values through these.
    val latestTransform by rememberUpdatedState(transform)
    val latestPendingCrop by rememberUpdatedState(pendingCrop)
    val penWidthPx = with(density) { penWidth.toPx() }

    fun applyPendingCrop() {
        val snapped = pendingCrop.snappedTo(imageSize)
        if (snapped != visible && snapped.width >= 1f && snapped.height >= 1f) {
            ops.add(MarkupOp.Crop(snapped))
        }
    }

    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onCancel) {
                Text(stringResource(Res.string.bugreport_cancel), color = Color.White)
            }
            Row {
                EditorIconButton(
                    icon = Icons.AutoMirrored.Filled.Undo,
                    description = stringResource(Res.string.bugreport_undo),
                    enabled = ops.isNotEmpty(),
                    onClick = { ops.removeAt(ops.lastIndex) },
                )
                EditorIconButton(
                    icon = Icons.Default.RestartAlt,
                    description = stringResource(Res.string.bugreport_reset),
                    enabled = ops.isNotEmpty() || pendingCrop != visible,
                    onClick = {
                        ops.clear()
                        pendingCrop = Rect(Offset.Zero, imageSize)
                    },
                )
            }
            TextButton(onClick = {
                if (mode == EditorMode.Crop) applyPendingCrop()
                onDone(renderMarkup(image, ops.toList()))
            }) {
                Text(stringResource(Res.string.bugreport_done), color = Color.White)
            }
        }

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(ImageMarkupEditorCanvasTag)
                .onSizeChanged { canvasSize = it }
                .pointerInput(mode) {
                    when (mode) {
                        EditorMode.Draw -> detectDragGestures(
                            onDragStart = { position ->
                                currentStroke.clear()
                                latestTransform?.let { currentStroke.add(it.toImage(position)) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                latestTransform?.let { currentStroke.add(it.toImage(change.position)) }
                            },
                            onDragEnd = {
                                val t = latestTransform
                                if (currentStroke.isNotEmpty() && t != null) {
                                    ops.add(MarkupOp.Stroke(currentStroke.toList(), penWidthPx / t.scale, penColor))
                                }
                                currentStroke.clear()
                            },
                            onDragCancel = { currentStroke.clear() },
                        )

                        EditorMode.Crop -> {
                            var handle: CropHandle? = null
                            detectDragGestures(
                                onDragStart = { position ->
                                    handle = latestTransform?.let { t ->
                                        hitCropHandle(t.toScreen(latestPendingCrop), position, CropTouchRadius.toPx())
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    val grabbed = handle
                                    val t = latestTransform
                                    if (grabbed != null && t != null) {
                                        change.consume()
                                        pendingCrop = dragCropRect(
                                            rect = latestPendingCrop,
                                            handle = grabbed,
                                            delta = dragAmount / t.scale,
                                            bounds = t.source,
                                            minSize = CropMinSize.toPx() / t.scale,
                                        )
                                    }
                                },
                                onDragEnd = { handle = null },
                                onDragCancel = { handle = null },
                            )
                        }
                    }
                },
        ) {
            val t = transform ?: return@Canvas
            val destination = t.destination
            drawImage(
                image = image,
                srcOffset = IntOffset(visible.left.roundToInt(), visible.top.roundToInt()),
                srcSize = IntSize(visible.width.roundToInt(), visible.height.roundToInt()),
                dstOffset = IntOffset(destination.left.roundToInt(), destination.top.roundToInt()),
                dstSize = IntSize(destination.width.roundToInt(), destination.height.roundToInt()),
            )
            clipRect(destination.left, destination.top, destination.right, destination.bottom) {
                ops.forEach { op -> if (op is MarkupOp.Stroke) drawStroke(op.points, op.width, op.color, t) }
                if (currentStroke.isNotEmpty()) drawStroke(currentStroke, penWidthPx / t.scale, penColor, t)
            }
            if (mode == EditorMode.Crop) drawCropOverlay(t.toScreen(pendingCrop), destination)
        }

        Text(
            text = stringResource(
                if (mode == EditorMode.Draw) Res.string.bugreport_draw_hint else Res.string.bugreport_crop_hint
            ),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        ) {
            ModeButton(
                icon = Icons.Default.Draw,
                label = stringResource(Res.string.bugreport_draw),
                selected = mode == EditorMode.Draw,
                onClick = {
                    // Leaving crop mode keeps the crop the user framed: what was dimmed is cut.
                    if (mode == EditorMode.Crop) applyPendingCrop()
                    mode = EditorMode.Draw
                },
            )
            ModeButton(
                icon = Icons.Default.Crop,
                label = stringResource(Res.string.bugreport_crop),
                selected = mode == EditorMode.Crop,
                onClick = {
                    pendingCrop = visible
                    mode = EditorMode.Crop
                },
            )
        }
    }
}

private fun DrawScope.drawStroke(points: List<Offset>, imageWidth: Float, color: Color, t: FitTransform) {
    if (points.isEmpty()) return
    val width = imageWidth * t.scale
    if (points.size == 1) {
        drawCircle(color, radius = width / 2f, center = t.toScreen(points.first()))
        return
    }
    drawPath(
        path = strokePath(points, t::toScreen),
        color = color,
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun DrawScope.drawCropOverlay(crop: Rect, image: Rect) {
    val scrim = Color.Black.copy(alpha = 0.6f)
    drawRect(scrim, Offset(image.left, image.top), Size(image.width, crop.top - image.top))
    drawRect(scrim, Offset(image.left, crop.bottom), Size(image.width, image.bottom - crop.bottom))
    drawRect(scrim, Offset(image.left, crop.top), Size(crop.left - image.left, crop.height))
    drawRect(scrim, Offset(crop.right, crop.top), Size(image.right - crop.right, crop.height))

    val guide = Color.White.copy(alpha = 0.45f)
    val thin = 1.dp.toPx()
    for (step in 1..2) {
        val x = crop.left + crop.width * step / 3f
        val y = crop.top + crop.height * step / 3f
        drawLine(guide, Offset(x, crop.top), Offset(x, crop.bottom), thin)
        drawLine(guide, Offset(crop.left, y), Offset(crop.right, y), thin)
    }
    drawRect(Color.White, crop.topLeft, crop.size, style = Stroke(width = thin))

    // L-shaped corner grips and short edge bars mark what can be dragged.
    val grip = 3.dp.toPx()
    val arm = minOf(20.dp.toPx(), crop.width / 3f, crop.height / 3f)
    val corners = listOf(
        crop.topLeft to Offset(1f, 1f),
        crop.topRight to Offset(-1f, 1f),
        crop.bottomRight to Offset(-1f, -1f),
        crop.bottomLeft to Offset(1f, -1f),
    )
    corners.forEach { (corner, direction) ->
        drawLine(Color.White, corner, corner + Offset(arm * direction.x, 0f), grip, StrokeCap.Square)
        drawLine(Color.White, corner, corner + Offset(0f, arm * direction.y), grip, StrokeCap.Square)
    }
    val bar = arm / 2f
    val center = crop.center
    drawLine(Color.White, Offset(center.x - bar, crop.top), Offset(center.x + bar, crop.top), grip)
    drawLine(Color.White, Offset(center.x - bar, crop.bottom), Offset(center.x + bar, crop.bottom), grip)
    drawLine(Color.White, Offset(crop.left, center.y - bar), Offset(crop.left, center.y + bar), grip)
    drawLine(Color.White, Offset(crop.right, center.y - bar), Offset(crop.right, center.y + bar), grip)
}

@Composable
private fun EditorIconButton(icon: ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(icon, contentDescription = description, tint = if (enabled) Color.White else Color.Gray)
    }
}

@Composable
private fun ModeButton(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) Color.White.copy(alpha = 0.18f) else Color.Transparent, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        Box(Modifier.padding(top = 2.dp)) {
            Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}
