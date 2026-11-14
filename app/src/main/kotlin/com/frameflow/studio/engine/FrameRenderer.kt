package com.frameflow.studio.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath
import android.graphics.RectF
import android.util.Base64
import android.util.LruCache
import androidx.core.graphics.createBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.FillPatch
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.LayerMeta
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

object FillBitmapCache {
    private val androidCache = object : LruCache<String, Bitmap>(64) {}
    private val composeCache = object : LruCache<String, ImageBitmap>(64) {}

    fun getAndroidBitmap(patch: FillPatch): Bitmap? {
        androidCache.get(patch.id)?.let { if (!it.isRecycled) return it }
        return try {
            val bytes = Base64.decode(patch.maskPngBase64, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also {
                androidCache.put(patch.id, it)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun getComposeImage(patch: FillPatch): ImageBitmap? {
        composeCache.get(patch.id)?.let { return it }
        val bmp = getAndroidBitmap(patch) ?: return null
        return bmp.asImageBitmap().also { composeCache.put(patch.id, it) }
    }
}

object FrameRenderer {

    /**
     * Renders an entire frame in Compose [DrawScope], mapping virtual canvas coordinates
     * (0..project.aspectRatio.canvasWidth x canvasHeight) into [DrawScope.size].
     */
    fun DrawScope.drawProjectFrame(
        project: AnimationProject,
        frameIndex: Int,
        onionSkin: OnionSkinSettings,
        guides: GuideSettings,
        inProgressStroke: VectorStroke? = null,
        activeLayerId: String? = null,
        aiGhostStrokes: List<VectorStroke> = emptyList(),
        selectedStrokeIds: Set<String> = emptySet(),
        isPlaying: Boolean = false,
    ) {
        val cw = project.aspectRatio.canvasWidth
        val ch = project.aspectRatio.canvasHeight
        val sx = size.width / cw
        val sy = size.height / ch

        // 1. Paper background
        drawRect(color = Color(project.backgroundColorArgb.toInt()))

        // 2. Subtle grid guide (if enabled and not playing)
        if (guides.showGrid && !isPlaying) {
            drawGrid(cw, ch, sx, sy, project.backgroundColorArgb)
        }

        // 3. Onion skinning (past & future frames)
        if (onionSkin.enabled && !isPlaying && project.frames.size > 1) {
            for (offset in onionSkin.prevFrames downTo 1) {
                val idx = frameIndex - offset
                if (idx in project.frames.indices) {
                    val alpha = onionSkin.opacity * (1f - (offset - 1) * 0.35f).coerceAtLeast(0.2f)
                    drawFrameLayers(
                        frame = project.frames[idx],
                        layers = project.layers,
                        sx = sx,
                        sy = sy,
                        overrideTint = Color(onionSkin.pastColorArgb.toInt()).copy(alpha = alpha),
                    )
                }
            }
            for (offset in 1..onionSkin.nextFrames) {
                val idx = frameIndex + offset
                if (idx in project.frames.indices) {
                    val alpha = onionSkin.opacity * (1f - (offset - 1) * 0.35f).coerceAtLeast(0.2f)
                    drawFrameLayers(
                        frame = project.frames[idx],
                        layers = project.layers,
                        sx = sx,
                        sy = sy,
                        overrideTint = Color(onionSkin.futureColorArgb.toInt()).copy(alpha = alpha),
                    )
                }
            }
        }

        // 4. AI Ghost next-frame prediction overlay
        if (aiGhostStrokes.isNotEmpty() && !isPlaying) {
            for (ghost in aiGhostStrokes) {
                drawVectorStroke(
                    stroke = ghost,
                    sx = sx,
                    sy = sy,
                    tintOverride = Color(0xFF34E8C7).copy(alpha = 0.42f),
                    dashed = true,
                )
            }
        }

        // 5. Current frame layers + active in-progress stroke
        val currentFrame = project.frames.getOrNull(frameIndex)
        if (currentFrame != null) {
            for (layer in project.layers) {
                if (!layer.visible) continue
                val content = currentFrame.contentFor(layer.id)
                drawLayerContent(
                    content = content,
                    sx = sx,
                    sy = sy,
                    layerOpacity = layer.opacity,
                    tintOverride = null,
                    selectedStrokeIds = if (!isPlaying) selectedStrokeIds else emptySet(),
                )
                if (inProgressStroke != null && layer.id == activeLayerId) {
                    drawVectorStroke(inProgressStroke, sx, sy, tintOverride = null)
                    if (guides.mirrorHorizontal && !isPlaying) {
                        val cw = project.aspectRatio.canvasWidth
                        drawVectorStroke(
                            inProgressStroke.copy(points = inProgressStroke.points.map { it.copy(x = cw - it.x) }),
                            sx,
                            sy,
                            tintOverride = null,
                        )
                    }
                }
            }
        } else if (inProgressStroke != null) {
            drawVectorStroke(inProgressStroke, sx, sy, tintOverride = null)
            if (guides.mirrorHorizontal && !isPlaying) {
                val cw = project.aspectRatio.canvasWidth
                drawVectorStroke(
                    inProgressStroke.copy(points = inProgressStroke.points.map { it.copy(x = cw - it.x) }),
                    sx,
                    sy,
                    tintOverride = null,
                )
            }
        }

        // 6. Horizontal mirror symmetry guide line
        if (guides.mirrorHorizontal && !isPlaying) {
            val midX = size.width * 0.5f
            drawLine(
                color = Color(0xFF7C8BFF).copy(alpha = 0.55f),
                start = Offset(midX, 0f),
                end = Offset(midX, size.height),
                strokeWidth = 2f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)),
            )
        }
    }

    fun DrawScope.drawThumbnailFrame(
        frame: AnimationFrame,
        layers: List<LayerMeta>,
        canvasWidth: Float,
        canvasHeight: Float,
        backgroundColorArgb: Long,
    ) {
        drawRect(color = Color(backgroundColorArgb.toInt()))
        drawFrameLayers(
            frame = frame,
            layers = layers,
            sx = size.width / canvasWidth,
            sy = size.height / canvasHeight,
            overrideTint = null,
        )
    }

    private fun DrawScope.drawGrid(
        cw: Float,
        ch: Float,
        sx: Float,
        sy: Float,
        bgArgb: Long,
    ) {
        val r = ((bgArgb shr 16) and 0xFF).toInt()
        val g = ((bgArgb shr 8) and 0xFF).toInt()
        val b = (bgArgb and 0xFF).toInt()
        val luminance = (0.299f * r + 0.587f * g + 0.114f * b)
        val gridColor = if (luminance > 128f) Color.Black.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.09f)

        val step = 120f
        var x = step
        while (x < cw) {
            drawLine(
                color = gridColor,
                start = Offset(x * sx, 0f),
                end = Offset(x * sx, size.height),
                strokeWidth = 1f,
            )
            x += step
        }
        var y = step
        while (y < ch) {
            drawLine(
                color = gridColor,
                start = Offset(0f, y * sy),
                end = Offset(size.width, y * sy),
                strokeWidth = 1f,
            )
            y += step
        }
        // Subtle rule-of-thirds accent
        val thirdColor = gridColor.copy(alpha = gridColor.alpha * 1.8f)
        for (k in 1..2) {
            val tx = size.width * (k / 3f)
            val ty = size.height * (k / 3f)
            drawLine(thirdColor, Offset(tx, 0f), Offset(tx, size.height), strokeWidth = 1.2f)
            drawLine(thirdColor, Offset(0f, ty), Offset(size.width, ty), strokeWidth = 1.2f)
        }
    }

    private fun DrawScope.drawFrameLayers(
        frame: AnimationFrame,
        layers: List<LayerMeta>,
        sx: Float,
        sy: Float,
        overrideTint: Color?,
    ) {
        for (layer in layers) {
            if (!layer.visible) continue
            val content = frame.contentFor(layer.id)
            drawLayerContent(
                content = content,
                sx = sx,
                sy = sy,
                layerOpacity = layer.opacity,
                tintOverride = overrideTint,
                selectedStrokeIds = emptySet(),
            )
        }
    }

    private fun DrawScope.drawLayerContent(
        content: LayerContent,
        sx: Float,
        sy: Float,
        layerOpacity: Float,
        tintOverride: Color?,
        selectedStrokeIds: Set<String>,
    ) {
        // Draw flood fills behind strokes on this layer
        for (patch in content.fills) {
            val img = FillBitmapCache.getComposeImage(patch) ?: continue
            val alpha = (tintOverride?.alpha ?: 1f) * layerOpacity
            drawImage(
                image = img,
                dstOffset = IntOffset((patch.left * sx).roundToInt(), (patch.top * sy).roundToInt()),
                dstSize = IntSize(
                    max(1, (patch.width * sx).roundToInt()),
                    max(1, (patch.height * sy).roundToInt()),
                ),
                alpha = alpha.coerceIn(0f, 1f),
            )
        }

        for (stroke in content.strokes) {
            drawVectorStroke(
                stroke = stroke,
                sx = sx,
                sy = sy,
                layerOpacity = layerOpacity,
                tintOverride = tintOverride,
                isSelected = stroke.id in selectedStrokeIds,
            )
        }
    }

    fun DrawScope.drawVectorStroke(
        stroke: VectorStroke,
        sx: Float,
        sy: Float,
        layerOpacity: Float = 1f,
        tintOverride: Color? = null,
        isSelected: Boolean = false,
        dashed: Boolean = false,
    ) {
        val pts = stroke.points
        if (pts.isEmpty()) return

        val scale = (sx + sy) * 0.5f
        val brushAlpha = when (stroke.brushType) {
            BrushType.HIGHLIGHTER -> minOf(stroke.opacity, 0.38f)
            BrushType.PENCIL -> stroke.opacity * 0.86f
            else -> stroke.opacity
        } * layerOpacity

        val strokeColor = if (tintOverride != null) {
            tintOverride.copy(alpha = (tintOverride.alpha * brushAlpha).coerceIn(0f, 1f))
        } else {
            val base = Color(stroke.colorArgb.toInt())
            base.copy(alpha = (base.alpha * brushAlpha).coerceIn(0f, 1f))
        }

        // Closed shape fill
        if (stroke.fillColorArgb != null && pts.size >= 3 && tintOverride == null) {
            val fillPath = buildSmoothComposePath(pts, sx, sy, closed = true)
            val fc = Color(stroke.fillColorArgb.toInt())
            drawPath(
                path = fillPath,
                color = fc.copy(alpha = (fc.alpha * layerOpacity).coerceIn(0f, 1f)),
                style = Fill,
            )
        }

        if (pts.size == 1) {
            val r = max(1f, stroke.baseWidth * pts[0].pressure * scale * 0.5f)
            drawCircle(
                color = strokeColor,
                radius = r,
                center = Offset(pts[0].x * sx, pts[0].y * sy),
            )
            return
        }

        if (isSelected) {
            val highlightPath = buildSmoothComposePath(pts, sx, sy, closed = stroke.isClosed)
            drawPath(
                path = highlightPath,
                color = Color(0xFF7C8BFF).copy(alpha = 0.45f),
                style = Stroke(
                    width = max(4f, stroke.baseWidth * scale + 10f * scale.coerceAtMost(1f)),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }

        val hasPressureVariation = stroke.brushType == BrushType.PEN && !dashed && hasVaryingPressure(pts)
        if (hasPressureVariation) {
            drawVariableWidthComposeStroke(pts, sx, sy, stroke.baseWidth * scale, strokeColor)
        } else {
            val path = buildSmoothComposePath(pts, sx, sy, closed = stroke.isClosed)
            drawPath(
                path = path,
                color = strokeColor,
                style = Stroke(
                    width = max(1f, stroke.baseWidth * scale),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                    pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(12f, 8f)) else null,
                ),
            )
        }
    }

    private fun hasVaryingPressure(pts: List<StrokePoint>): Boolean {
        var minP = Float.POSITIVE_INFINITY
        var maxP = Float.NEGATIVE_INFINITY
        for (p in pts) {
            if (p.pressure < minP) minP = p.pressure
            if (p.pressure > maxP) maxP = p.pressure
        }
        return (maxP - minP) > 0.06f
    }

    private fun DrawScope.drawVariableWidthComposeStroke(
        pts: List<StrokePoint>,
        sx: Float,
        sy: Float,
        baseWidthPx: Float,
        color: Color,
    ) {
        for (i in 1 until pts.size) {
            val a = pts[i - 1]
            val b = pts[i]
            val avgP = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.25f, 1.35f)
            drawLine(
                color = color,
                start = Offset(a.x * sx, a.y * sy),
                end = Offset(b.x * sx, b.y * sy),
                strokeWidth = max(1f, baseWidthPx * avgP),
                cap = StrokeCap.Round,
            )
        }
    }

    fun buildSmoothComposePath(
        pts: List<StrokePoint>,
        sx: Float,
        sy: Float,
        closed: Boolean = false,
    ): Path {
        val path = Path()
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].x * sx, pts[0].y * sy)
        if (pts.size == 2) {
            path.lineTo(pts[1].x * sx, pts[1].y * sy)
        } else {
            for (i in 1 until pts.size - 1) {
                val curr = pts[i]
                val next = pts[i + 1]
                val midX = (curr.x + next.x) * 0.5f * sx
                val midY = (curr.y + next.y) * 0.5f * sy
                path.quadraticTo(curr.x * sx, curr.y * sy, midX, midY)
            }
            val last = pts.last()
            path.lineTo(last.x * sx, last.y * sy)
        }
        if (closed) path.close()
        return path
    }

    // --- Android Canvas Rendering (used for GIF/MP4/PNG Export & Flood Fill & AI Vision Snapshots) ---

    fun renderFrameToBitmap(
        project: AnimationProject,
        frameIndex: Int,
        outWidth: Int,
        outHeight: Int,
    ): Bitmap {
        val bitmap = createBitmap(outWidth, outHeight)
        val canvas = AndroidCanvas(bitmap)
        canvas.drawColor(project.backgroundColorArgb.toInt())

        val frame = project.frames.getOrNull(frameIndex) ?: return bitmap
        val sx = outWidth / project.aspectRatio.canvasWidth
        val sy = outHeight / project.aspectRatio.canvasHeight

        for (layer in project.layers) {
            if (!layer.visible) continue
            drawLayerToAndroidCanvas(
                canvas = canvas,
                content = frame.contentFor(layer.id),
                scaleX = sx,
                scaleY = sy,
                layerOpacity = layer.opacity,
                tintOverride = null,
            )
        }
        return bitmap
    }

    fun drawLayerToAndroidCanvas(
        canvas: AndroidCanvas,
        content: LayerContent,
        scaleX: Float,
        scaleY: Float,
        layerOpacity: Float,
        tintOverride: Int?,
    ) {
        val bitmapPaint = AndroidPaint(AndroidPaint.FILTER_BITMAP_FLAG or AndroidPaint.ANTI_ALIAS_FLAG)
        bitmapPaint.alpha = (layerOpacity * 255f).roundToInt().coerceIn(0, 255)
        for (patch in content.fills) {
            val bmp = FillBitmapCache.getAndroidBitmap(patch) ?: continue
            val dst = RectF(
                patch.left * scaleX,
                patch.top * scaleY,
                (patch.left + patch.width) * scaleX,
                (patch.top + patch.height) * scaleY,
            )
            canvas.drawBitmap(bmp, null, dst, bitmapPaint)
        }

        val paint = AndroidPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
            strokeCap = AndroidPaint.Cap.ROUND
            strokeJoin = AndroidPaint.Join.ROUND
        }
        val scale = (scaleX + scaleY) * 0.5f

        for (stroke in content.strokes) {
            val pts = stroke.points
            if (pts.isEmpty()) continue

            val brushAlpha = when (stroke.brushType) {
                BrushType.HIGHLIGHTER -> minOf(stroke.opacity, 0.38f)
                BrushType.PENCIL -> stroke.opacity * 0.86f
                else -> stroke.opacity
            } * layerOpacity

            val argb = tintOverride ?: stroke.colorArgb.toInt()
            val alpha = (((argb ushr 24) and 0xFF) * brushAlpha).roundToInt().coerceIn(0, 255)

            if (stroke.fillColorArgb != null && pts.size >= 3 && tintOverride == null) {
                val fillArgb = stroke.fillColorArgb.toInt()
                val fillAlpha = (((fillArgb ushr 24) and 0xFF) * layerOpacity).roundToInt().coerceIn(0, 255)
                paint.style = AndroidPaint.Style.FILL
                paint.color = (fillArgb and 0x00FFFFFF) or (fillAlpha shl 24)
                canvas.drawPath(buildSmoothAndroidPath(pts, scaleX, scaleY, closed = true), paint)
            }

            paint.style = AndroidPaint.Style.STROKE
            paint.color = (argb and 0x00FFFFFF) or (alpha shl 24)

            if (pts.size == 1) {
                paint.style = AndroidPaint.Style.FILL
                val r = max(1f, stroke.baseWidth * pts[0].pressure * scale * 0.5f)
                canvas.drawCircle(pts[0].x * scaleX, pts[0].y * scaleY, r, paint)
                continue
            }

            if (stroke.brushType == BrushType.PEN && hasVaryingPressure(pts)) {
                for (i in 1 until pts.size) {
                    val a = pts[i - 1]
                    val b = pts[i]
                    val avgP = ((a.pressure + b.pressure) * 0.5f).coerceIn(0.25f, 1.35f)
                    paint.strokeWidth = max(1f, stroke.baseWidth * scale * avgP)
                    canvas.drawLine(a.x * scaleX, a.y * scaleY, b.x * scaleX, b.y * scaleY, paint)
                }
            } else {
                paint.strokeWidth = max(1f, stroke.baseWidth * scale)
                canvas.drawPath(buildSmoothAndroidPath(pts, scaleX, scaleY, closed = stroke.isClosed), paint)
            }
        }
    }

    private fun buildSmoothAndroidPath(
        pts: List<StrokePoint>,
        sx: Float,
        sy: Float,
        closed: Boolean,
    ): AndroidPath {
        val path = AndroidPath()
        if (pts.isEmpty()) return path
        path.moveTo(pts[0].x * sx, pts[0].y * sy)
        if (pts.size == 2) {
            path.lineTo(pts[1].x * sx, pts[1].y * sy)
        } else {
            for (i in 1 until pts.size - 1) {
                val curr = pts[i]
                val next = pts[i + 1]
                val midX = (curr.x + next.x) * 0.5f * sx
                val midY = (curr.y + next.y) * 0.5f * sy
                path.quadTo(curr.x * sx, curr.y * sy, midX, midY)
            }
            val last = pts.last()
            path.lineTo(last.x * sx, last.y * sy)
        }
        if (closed) path.close()
        return path
    }
}
