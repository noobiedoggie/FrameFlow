package com.frameflow.studio.engine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.util.Base64
import androidx.core.graphics.createBitmap
import com.frameflow.studio.model.FillPatch
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StrokePoint
import java.io.ByteArrayOutputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class FloodFillResult(
    val filledCount: Int,
    val minX: Int,
    val minY: Int,
    val maxX: Int,
    val maxY: Int,
)

object FloodFillEngine {

    /**
     * Pure-Kotlin iterative scanline flood fill operating on ARGB pixel buffers.
     * Writes filled pixels into [outMaskPixels] (same size as [sourcePixels]) so the fill
     * can be stored cleanly as a transparent overlay patch behind strokes.
     */
    fun floodFillToMask(
        sourcePixels: IntArray,
        outMaskPixels: IntArray,
        width: Int,
        height: Int,
        seedX: Int,
        seedY: Int,
        fillColor: Int,
        tolerance: Int = 44,
    ): FloodFillResult {
        if (seedX !in 0 until width || seedY !in 0 until height) {
            return FloodFillResult(0, 0, 0, -1, -1)
        }
        val seedColor = sourcePixels[seedY * width + seedX]
        if (colorsClose(seedColor, fillColor, tolerance = 8) && (seedColor ushr 24) > 230) {
            return FloodFillResult(0, 0, 0, -1, -1)
        }

        val visited = BooleanArray(width * height)
        val stack = IntArray(width * height * 2)
        var sp = 0
        stack[sp++] = seedX
        stack[sp++] = seedY

        var count = 0
        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1

        while (sp > 0) {
            val y = stack[--sp]
            val x = stack[--sp]
            var left = x
            while (left >= 0 && !visited[y * width + left] && colorsClose(sourcePixels[y * width + left], seedColor, tolerance)) {
                left--
            }
            left++

            var right = x
            while (right < width && !visited[y * width + right] && colorsClose(sourcePixels[y * width + right], seedColor, tolerance)) {
                right++
            }
            right--

            if (left > right) continue

            var spanAbove = false
            var spanBelow = false
            for (px in left..right) {
                val idx = y * width + px
                if (!visited[idx]) {
                    visited[idx] = true
                    outMaskPixels[idx] = fillColor
                    count++
                    if (px < minX) minX = px
                    if (px > maxX) maxX = px
                    if (y < minY) minY = y
                    if (y > maxY) maxY = y
                }

                if (y > 0) {
                    val upIdx = (y - 1) * width + px
                    val matchUp = !visited[upIdx] && colorsClose(sourcePixels[upIdx], seedColor, tolerance)
                    if (!spanAbove && matchUp && sp + 2 < stack.size) {
                        stack[sp++] = px
                        stack[sp++] = y - 1
                        spanAbove = true
                    } else if (spanAbove && !matchUp) {
                        spanAbove = false
                    }
                }
                if (y < height - 1) {
                    val downIdx = (y + 1) * width + px
                    val matchDown = !visited[downIdx] && colorsClose(sourcePixels[downIdx], seedColor, tolerance)
                    if (!spanBelow && matchDown && sp + 2 < stack.size) {
                        stack[sp++] = px
                        stack[sp++] = y + 1
                        spanBelow = true
                    } else if (spanBelow && !matchDown) {
                        spanBelow = false
                    }
                }
            }
        }

        // Dilate 1px into semi-transparent anti-aliased edges so there's no white halo around ink strokes
        if (count > 0) {
            val dilated = outMaskPixels.copyOf()
            val startY = max(1, minY - 1)
            val endY = min(height - 2, maxY + 1)
            val startX = max(1, minX - 1)
            val endX = min(width - 2, maxX + 1)
            for (y in startY..endY) {
                for (x in startX..endX) {
                    val idx = y * width + x
                    if (outMaskPixels[idx] == 0) {
                        val hasNeighbor =
                            outMaskPixels[idx - 1] != 0 ||
                                outMaskPixels[idx + 1] != 0 ||
                                outMaskPixels[idx - width] != 0 ||
                                outMaskPixels[idx + width] != 0
                        if (hasNeighbor && (sourcePixels[idx] ushr 24) < 220) {
                            dilated[idx] = fillColor
                            if (x < minX) minX = x
                            if (x > maxX) maxX = x
                            if (y < minY) minY = y
                            if (y > maxY) maxY = y
                        }
                    }
                }
            }
            dilated.copyInto(outMaskPixels)
        }

        return FloodFillResult(count, minX, minY, maxX, maxY)
    }

    private fun colorsClose(c1: Int, c2: Int, tolerance: Int): Boolean {
        val a1 = (c1 ushr 24) and 0xFF
        val a2 = (c2 ushr 24) and 0xFF
        if (abs(a1 - a2) > tolerance) return false
        if (a1 < 20 && a2 < 20) return true
        val r1 = (c1 ushr 16) and 0xFF
        val g1 = (c1 ushr 8) and 0xFF
        val b1 = c1 and 0xFF
        val r2 = (c2 ushr 16) and 0xFF
        val g2 = (c2 ushr 8) and 0xFF
        val b2 = c2 and 0xFF
        return abs(r1 - r2) <= tolerance &&
            abs(g1 - g2) <= tolerance &&
            abs(b1 - b2) <= tolerance
    }

    /**
     * Renders [layerContent] onto an offscreen proportional buffer, performs scanline flood fill
     * at canvas coordinates ([canvasX], [canvasY]), and returns a compact [FillPatch] or null.
     */
    fun computeLayerFillPatch(
        layerContent: LayerContent,
        canvasWidth: Float,
        canvasHeight: Float,
        canvasX: Float,
        canvasY: Float,
        fillColorArgb: Long,
        boundaryContents: List<LayerContent> = listOf(layerContent),
    ): FillPatch? {
        if (canvasX < 0f || canvasX > canvasWidth || canvasY < 0f || canvasY > canvasHeight) {
            return null
        }
        val maxDim = 540f
        val scale = min(1f, maxDim / max(canvasWidth, canvasHeight))
        val bufW = (canvasWidth * scale).toInt().coerceAtLeast(64)
        val bufH = (canvasHeight * scale).toInt().coerceAtLeast(64)

        val bitmap = createBitmap(bufW, bufH)
        val canvas = Canvas(bitmap)
        val contentsToRender = if (boundaryContents.isEmpty()) listOf(layerContent) else boundaryContents
        for (content in contentsToRender) {
            FrameRenderer.drawLayerToAndroidCanvas(
                canvas = canvas,
                content = content,
                scaleX = bufW / canvasWidth,
                scaleY = bufH / canvasHeight,
                layerOpacity = 1f,
                tintOverride = null,
            )
        }

        val pixels = IntArray(bufW * bufH)
        bitmap.getPixels(pixels, 0, bufW, 0, 0, bufW, bufH)
        bitmap.recycle()

        val seedX = (canvasX * (bufW / canvasWidth)).toInt().coerceIn(0, bufW - 1)
        val seedY = (canvasY * (bufH / canvasHeight)).toInt().coerceIn(0, bufH - 1)
        val mask = IntArray(bufW * bufH)

        val result = floodFillToMask(
            sourcePixels = pixels,
            outMaskPixels = mask,
            width = bufW,
            height = bufH,
            seedX = seedX,
            seedY = seedY,
            fillColor = fillColorArgb.toInt(),
        )
        if (result.filledCount == 0 || result.maxX < result.minX || result.maxY < result.minY) {
            return null
        }

        val patchW = (result.maxX - result.minX + 1).coerceAtLeast(1)
        val patchH = (result.maxY - result.minY + 1).coerceAtLeast(1)
        val patchPixels = IntArray(patchW * patchH)
        for (py in 0 until patchH) {
            val srcOffset = (result.minY + py) * bufW + result.minX
            System.arraycopy(mask, srcOffset, patchPixels, py * patchW, patchW)
        }

        val patchBitmap = createBitmap(patchW, patchH)
        patchBitmap.setPixels(patchPixels, 0, patchW, 0, 0, patchW, patchH)
        val pngBytes = ByteArrayOutputStream().use { baos ->
            patchBitmap.compress(Bitmap.CompressFormat.PNG, 100, baos)
            baos.toByteArray()
        }
        patchBitmap.recycle()

        val invScaleX = canvasWidth / bufW
        val invScaleY = canvasHeight / bufH

        return FillPatch(
            colorArgb = fillColorArgb,
            maskPngBase64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP),
            left = result.minX * invScaleX,
            top = result.minY * invScaleY,
            width = patchW * invScaleX,
            height = patchH * invScaleY,
        )
    }

    /**
     * Erases pixels (Split mode) or hits (Whole mode) from raster [FillPatch] overlays.
     */
    fun eraseFromFillPatches(
        fills: List<FillPatch>,
        eraserPath: List<StrokePoint>,
        eraserRadius: Float,
        wholeStroke: Boolean,
    ): List<FillPatch> {
        if (fills.isEmpty() || eraserPath.isEmpty()) return fills

        var minEx = Float.POSITIVE_INFINITY
        var minEy = Float.POSITIVE_INFINITY
        var maxEx = Float.NEGATIVE_INFINITY
        var maxEy = Float.NEGATIVE_INFINITY
        for (pt in eraserPath) {
            if (pt.x < minEx) minEx = pt.x
            if (pt.y < minEy) minEy = pt.y
            if (pt.x > maxEx) maxEx = pt.x
            if (pt.y > maxEy) maxEy = pt.y
        }
        minEx -= eraserRadius
        minEy -= eraserRadius
        maxEx += eraserRadius
        maxEy += eraserRadius

        val rSq = eraserRadius * eraserRadius

        return fills.mapNotNull { patch ->
            if (patch.width <= 0f || patch.height <= 0f) return@mapNotNull null
            if (patch.left + patch.width < minEx || patch.left > maxEx ||
                patch.top + patch.height < minEy || patch.top > maxEy
            ) {
                return@mapNotNull patch
            }

            val rawBytes = try {
                Base64.decode(patch.maskPngBase64, Base64.DEFAULT)
            } catch (_: Exception) {
                return@mapNotNull patch
            }
            val bmp = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
                ?: return@mapNotNull patch

            val w = bmp.width
            val h = bmp.height
            val pixels = IntArray(w * h)
            bmp.getPixels(pixels, 0, w, 0, 0, w, h)
            bmp.recycle()

            val pxToCanvasX = patch.width / w.toFloat()
            val pxToCanvasY = patch.height / h.toFloat()
            val canvasToPxX = w.toFloat() / patch.width
            val canvasToPxY = h.toFloat() / patch.height

            val pyStart = floor(((minEy - patch.top) * canvasToPxY)).toInt().coerceIn(0, h - 1)
            val pyEnd = ceil(((maxEy - patch.top) * canvasToPxY)).toInt().coerceIn(0, h - 1)
            val pxStart = floor(((minEx - patch.left) * canvasToPxX)).toInt().coerceIn(0, w - 1)
            val pxEnd = ceil(((maxEx - patch.left) * canvasToPxX)).toInt().coerceIn(0, w - 1)

            var modified = false
            for (py in pyStart..pyEnd) {
                val cy = patch.top + (py + 0.5f) * pxToCanvasY
                for (px in pxStart..pxEnd) {
                    val idx = py * w + px
                    val alpha = (pixels[idx] ushr 24) and 0xFF
                    if (alpha <= 10) continue

                    val cx = patch.left + (px + 0.5f) * pxToCanvasX
                    if (distanceSqToPolyline(cx, cy, eraserPath) <= rSq) {
                        if (wholeStroke) {
                            return@mapNotNull null
                        }
                        pixels[idx] = 0
                        modified = true
                    }
                }
            }

            if (!modified) return@mapNotNull patch

            var hasOpaque = false
            for (p in pixels) {
                if (((p ushr 24) and 0xFF) > 10) {
                    hasOpaque = true
                    break
                }
            }
            if (!hasOpaque) return@mapNotNull null

            val outBmp = createBitmap(w, h)
            outBmp.setPixels(pixels, 0, w, 0, 0, w, h)
            val pngBytes = ByteArrayOutputStream().use { baos ->
                outBmp.compress(Bitmap.CompressFormat.PNG, 100, baos)
                baos.toByteArray()
            }
            outBmp.recycle()

            FillPatch(
                colorArgb = patch.colorArgb,
                maskPngBase64 = Base64.encodeToString(pngBytes, Base64.NO_WRAP),
                left = patch.left,
                top = patch.top,
                width = patch.width,
                height = patch.height,
            )
        }
    }

    private fun distanceSqToPolyline(x: Float, y: Float, poly: List<StrokePoint>): Float {
        if (poly.size == 1) {
            val dx = x - poly[0].x
            val dy = y - poly[0].y
            return dx * dx + dy * dy
        }
        var best = Float.POSITIVE_INFINITY
        for (i in 0 until poly.lastIndex) {
            val a = poly[i]
            val b = poly[i + 1]
            val vx = b.x - a.x
            val vy = b.y - a.y
            val lenSq = vx * vx + vy * vy
            val t = if (lenSq < 1e-6f) {
                0f
            } else {
                (((x - a.x) * vx + (y - a.y) * vy) / lenSq).coerceIn(0f, 1f)
            }
            val projX = a.x + t * vx
            val projY = a.y + t * vy
            val dx = x - projX
            val dy = y - projY
            val dSq = dx * dx + dy * dy
            if (dSq < best) best = dSq
        }
        return best
    }
}
