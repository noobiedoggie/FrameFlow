package com.frameflow.studio.engine

import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.ShapeKind
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.model.newId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class StrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) * 0.5f
    val centerY: Float get() = (top + bottom) * 0.5f

    fun intersects(other: StrokeBounds, padding: Float = 0f): Boolean =
        right + padding >= other.left &&
            left - padding <= other.right &&
            bottom + padding >= other.top &&
            top - padding <= other.bottom
}

object StrokeGeometry {

    fun distance(a: StrokePoint, b: StrokePoint): Float =
        hypot(a.x - b.x, a.y - b.y)

    fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float =
        hypot(ax - bx, ay - by)

    fun computeBounds(stroke: VectorStroke): StrokeBounds? {
        if (stroke.points.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        for (pt in stroke.points) {
            if (pt.x < minX) minX = pt.x
            if (pt.y < minY) minY = pt.y
            if (pt.x > maxX) maxX = pt.x
            if (pt.y > maxY) maxY = pt.y
        }
        val pad = stroke.baseWidth * 0.5f
        return StrokeBounds(minX - pad, minY - pad, maxX + pad, maxY + pad)
    }

    fun computeBounds(strokes: List<VectorStroke>): StrokeBounds? {
        if (strokes.isEmpty()) return null
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var any = false
        for (s in strokes) {
            val b = computeBounds(s) ?: continue
            any = true
            minX = min(minX, b.left)
            minY = min(minY, b.top)
            maxX = max(maxX, b.right)
            maxY = max(maxY, b.bottom)
        }
        return if (any) StrokeBounds(minX, minY, maxX, maxY) else null
    }

    fun computeCentroid(stroke: VectorStroke): StrokePoint {
        if (stroke.points.isEmpty()) return StrokePoint(0f, 0f)
        var sx = 0f
        var sy = 0f
        for (p in stroke.points) {
            sx += p.x
            sy += p.y
        }
        val inv = 1f / stroke.points.size
        return StrokePoint(sx * inv, sy * inv)
    }

    fun pathLength(points: List<StrokePoint>, closed: Boolean = false): Float {
        if (points.size < 2) return 0f
        var len = 0f
        for (i in 1 until points.size) {
            len += distance(points[i - 1], points[i])
        }
        if (closed && points.size > 2) {
            len += distance(points.last(), points.first())
        }
        return len
    }

    fun distancePointToSegment(
        px: Float,
        py: Float,
        ax: Float,
        ay: Float,
        bx: Float,
        by: Float,
    ): Float {
        val dx = bx - ax
        val dy = by - ay
        val lenSq = dx * dx + dy * dy
        if (lenSq <= 1e-6f) return hypot(px - ax, py - ay)
        val t = (((px - ax) * dx + (py - ay) * dy) / lenSq).coerceIn(0f, 1f)
        val projX = ax + t * dx
        val projY = ay + t * dy
        return hypot(px - projX, py - projY)
    }

    /**
     * Subdivides long segments so partial erasing and stroke morphing always have fine resolution.
     */
    fun densifyPoints(points: List<StrokePoint>, maxStep: Float): List<StrokePoint> {
        if (points.size <= 1 || maxStep <= 0.5f) return points
        val out = ArrayList<StrokePoint>(points.size * 2)
        out.add(points.first())
        for (i in 1 until points.size) {
            val a = points[i - 1]
            val b = points[i]
            val d = distance(a, b)
            if (d > maxStep) {
                val steps = (d / maxStep).toInt().coerceIn(2, 64)
                for (s in 1 until steps) {
                    val t = s.toFloat() / steps
                    out.add(
                        StrokePoint(
                            x = a.x + (b.x - a.x) * t,
                            y = a.y + (b.y - a.y) * t,
                            pressure = a.pressure + (b.pressure - a.pressure) * t,
                        )
                    )
                }
            }
            out.add(b)
        }
        return out
    }

    /**
     * Resamples a polyline to [targetCount] equidistant points along its arc length.
     */
    fun resamplePoints(
        points: List<StrokePoint>,
        targetCount: Int,
        closed: Boolean = false,
    ): List<StrokePoint> {
        if (points.isEmpty() || targetCount <= 0) return emptyList()
        if (points.size == 1) return List(targetCount) { points.first() }

        val src = if (closed && distance(points.first(), points.last()) > 1f) {
            points + points.first()
        } else {
            points
        }

        val cumDist = FloatArray(src.size)
        for (i in 1 until src.size) {
            cumDist[i] = cumDist[i - 1] + distance(src[i - 1], src[i])
        }
        val totalLen = cumDist.last()
        if (totalLen <= 1e-4f) {
            return List(targetCount) { src.first() }
        }

        val out = ArrayList<StrokePoint>(targetCount)
        if (targetCount == 1) {
            out.add(src.first())
            return out
        }

        var segIdx = 0
        for (i in 0 until targetCount) {
            val targetD = (i.toFloat() / (targetCount - 1)) * totalLen
            while (segIdx < src.size - 2 && cumDist[segIdx + 1] < targetD) {
                segIdx++
            }
            val segStart = cumDist[segIdx]
            val segEnd = cumDist[segIdx + 1]
            val span = (segEnd - segStart).coerceAtLeast(1e-5f)
            val t = ((targetD - segStart) / span).coerceIn(0f, 1f)
            val a = src[segIdx]
            val b = src[segIdx + 1]
            out.add(
                StrokePoint(
                    x = a.x + (b.x - a.x) * t,
                    y = a.y + (b.y - a.y) * t,
                    pressure = a.pressure + (b.pressure - a.pressure) * t,
                )
            )
        }
        return out
    }

    /**
     * Finger-friendly velocity-to-pressure curve for expressive Pen strokes.
     */
    fun estimateFingerPressure(prev: StrokePoint?, currX: Float, currY: Float, dtMs: Long): Float {
        if (prev == null) return 0.78f
        val d = hypot(currX - prev.x, currY - prev.y)
        val dt = dtMs.coerceIn(4L, 120L).toFloat()
        val speed = d / dt // canvas px per ms
        // Faster movement slightly thins line, slow deliberate drawing thickens slightly
        val target = (0.95f - speed * 0.045f).coerceIn(0.42f, 1.0f)
        return prev.pressure * 0.65f + target * 0.35f
    }

    /**
     * Erases points/segments intersecting the eraser path [eraserPath].
     * Splits strokes cleanly when erased in the middle; or removes whole stroke if [wholeStroke].
     */
    fun eraseFromStrokes(
        strokes: List<VectorStroke>,
        eraserPath: List<StrokePoint>,
        eraserRadius: Float,
        wholeStroke: Boolean = false,
    ): List<VectorStroke> {
        if (strokes.isEmpty() || eraserPath.isEmpty()) return strokes

        var minEx = Float.POSITIVE_INFINITY
        var minEy = Float.POSITIVE_INFINITY
        var maxEx = Float.NEGATIVE_INFINITY
        var maxEy = Float.NEGATIVE_INFINITY
        for (p in eraserPath) {
            minEx = min(minEx, p.x)
            minEy = min(minEy, p.y)
            maxEx = max(maxEx, p.x)
            maxEy = max(maxEy, p.y)
        }
        val eraserBounds = StrokeBounds(
            minEx - eraserRadius,
            minEy - eraserRadius,
            maxEx + eraserRadius,
            maxEy + eraserRadius,
        )

        val result = ArrayList<VectorStroke>(strokes.size)
        for (stroke in strokes) {
            val sb = computeBounds(stroke)
            if (sb == null || !sb.intersects(eraserBounds, padding = 4f)) {
                result.add(stroke)
                continue
            }

            val hitRadius = eraserRadius + stroke.baseWidth * 0.35f
            if (wholeStroke) {
                val hits = strokeTouchesEraser(stroke, eraserPath, hitRadius)
                if (!hits) result.add(stroke)
                continue
            }

            // Subdivide long segments so fast swipes cut cleanly right through 2-point lines too
            val step = max(4f, eraserRadius * 0.45f)
            val dense = densifyPoints(
                if (stroke.isClosed && stroke.points.size > 2) stroke.points + stroke.points.first() else stroke.points,
                maxStep = step,
            )

            val segments = mutableListOf<MutableList<StrokePoint>>()
            var currentRun: MutableList<StrokePoint>? = null
            var anyRemoved = false

            for (pt in dense) {
                val erased = pointTouchesEraserPath(pt, eraserPath, hitRadius)
                if (erased) {
                    anyRemoved = true
                    if (currentRun != null) {
                        if (currentRun.size >= 2) segments.add(currentRun)
                        currentRun = null
                    }
                } else {
                    if (currentRun == null) currentRun = mutableListOf()
                    currentRun.add(pt)
                }
            }
            if (currentRun != null && currentRun.size >= 2) {
                segments.add(currentRun)
            }

            if (!anyRemoved) {
                result.add(stroke)
            } else if (segments.isNotEmpty()) {
                if (segments.size == 1 && !stroke.isClosed) {
                    result.add(stroke.copy(points = segments.first()))
                } else {
                    segments.forEachIndexed { idx, pts ->
                        result.add(
                            stroke.copy(
                                id = if (idx == 0) stroke.id else newId(),
                                points = pts,
                                isClosed = false,
                                fillColorArgb = null,
                            )
                        )
                    }
                }
            }
        }
        return result
    }

    private fun pointTouchesEraserPath(
        pt: StrokePoint,
        eraserPath: List<StrokePoint>,
        radius: Float,
    ): Boolean {
        if (eraserPath.size == 1) {
            return distance(pt, eraserPath[0]) <= radius
        }
        for (i in 1 until eraserPath.size) {
            val a = eraserPath[i - 1]
            val b = eraserPath[i]
            if (distancePointToSegment(pt.x, pt.y, a.x, a.y, b.x, b.y) <= radius) {
                return true
            }
        }
        return false
    }

    private fun strokeTouchesEraser(
        stroke: VectorStroke,
        eraserPath: List<StrokePoint>,
        radius: Float,
    ): Boolean {
        val dense = densifyPoints(stroke.points, maxStep = max(6f, radius * 0.5f))
        return dense.any { pointTouchesEraserPath(it, eraserPath, radius) }
    }

    /**
     * Ray-casting point-in-polygon check for Lasso selection & shape containment.
     */
    fun pointInPolygon(x: Float, y: Float, polygon: List<StrokePoint>): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val xi = polygon[i].x
            val yi = polygon[i].y
            val xj = polygon[j].x
            val yj = polygon[j].y
            val intersect = ((yi > y) != (yj > y)) &&
                (x < (xj - xi) * (y - yi) / ((yj - yi).takeIf { abs(it) > 1e-6f } ?: 1e-6f) + xi)
            if (intersect) inside = !inside
            j = i
        }
        return inside
    }

    fun selectStrokesInLasso(
        strokes: List<VectorStroke>,
        lassoPolygon: List<StrokePoint>,
    ): Set<String> {
        if (lassoPolygon.size < 3) return emptySet()
        return strokes.mapNotNull { stroke ->
            if (stroke.points.isEmpty()) return@mapNotNull null
            val insideCount = stroke.points.count { pointInPolygon(it.x, it.y, lassoPolygon) }
            val centroidInside = pointInPolygon(
                computeCentroid(stroke).x,
                computeCentroid(stroke).y,
                lassoPolygon,
            )
            if (centroidInside || insideCount >= (stroke.points.size + 1) / 2) stroke.id else null
        }.toSet()
    }

    fun findStrokeAtPoint(
        strokes: List<VectorStroke>,
        x: Float,
        y: Float,
        hitRadius: Float = 28f,
    ): VectorStroke? {
        val target = StrokePoint(x, y)
        for (i in strokes.indices.reversed()) {
            val stroke = strokes[i]
            if (stroke.points.isEmpty()) continue
            if (stroke.points.size == 1 && distance(stroke.points[0], target) <= hitRadius + stroke.baseWidth) {
                return stroke
            }
            for (p in 1 until stroke.points.size) {
                val a = stroke.points[p - 1]
                val b = stroke.points[p]
                if (distancePointToSegment(x, y, a.x, a.y, b.x, b.y) <= hitRadius + stroke.baseWidth * 0.5f) {
                    return stroke
                }
            }
        }
        return null
    }

    fun transformStrokes(
        strokes: List<VectorStroke>,
        selectedIds: Set<String>,
        dx: Float,
        dy: Float,
        scale: Float = 1f,
        rotationRadians: Float = 0f,
    ): List<VectorStroke> {
        val selected = strokes.filter { it.id in selectedIds }
        val bounds = computeBounds(selected) ?: return strokes
        val cx = bounds.centerX
        val cy = bounds.centerY
        val cosR = cos(rotationRadians)
        val sinR = sin(rotationRadians)

        return strokes.map { stroke ->
            if (stroke.id !in selectedIds) return@map stroke
            stroke.copy(
                points = stroke.points.map { pt ->
                    val rx = (pt.x - cx) * scale
                    val ry = (pt.y - cy) * scale
                    val tx = rx * cosR - ry * sinR + cx + dx
                    val ty = rx * sinR + ry * cosR + cy + dy
                    pt.copy(x = tx, y = ty)
                },
                baseWidth = (stroke.baseWidth * scale).coerceIn(2f, 120f),
            )
        }
    }

    /**
     * Creates clean vector geometry for Shape tool or AI shapes.
     */
    fun createShapeStroke(
        kind: ShapeKind,
        start: StrokePoint,
        end: StrokePoint,
        brushType: BrushType,
        colorArgb: Long,
        width: Float,
        opacity: Float = 1f,
        fillColorArgb: Long? = null,
    ): VectorStroke {
        val points = when (kind) {
            ShapeKind.LINE -> listOf(
                start.copy(pressure = 1f),
                end.copy(pressure = 1f),
            )
            ShapeKind.RECTANGLE -> {
                val left = min(start.x, end.x)
                val right = max(start.x, end.x)
                val top = min(start.y, end.y)
                val bottom = max(start.y, end.y)
                listOf(
                    StrokePoint(left, top),
                    StrokePoint(right, top),
                    StrokePoint(right, bottom),
                    StrokePoint(left, bottom),
                    StrokePoint(left, top),
                )
            }
            ShapeKind.OVAL -> {
                val cx = (start.x + end.x) * 0.5f
                val cy = (start.y + end.y) * 0.5f
                val rx = max(6f, abs(end.x - start.x) * 0.5f)
                val ry = max(6f, abs(end.y - start.y) * 0.5f)
                createOvalPoints(cx, cy, rx, ry, segments = 32)
            }
            ShapeKind.TRIANGLE -> {
                val left = min(start.x, end.x)
                val right = max(start.x, end.x)
                val top = min(start.y, end.y)
                val bottom = max(start.y, end.y)
                val midX = (left + right) * 0.5f
                listOf(
                    StrokePoint(midX, top),
                    StrokePoint(right, bottom),
                    StrokePoint(left, bottom),
                    StrokePoint(midX, top),
                )
            }
        }
        return VectorStroke(
            points = points,
            brushType = brushType,
            colorArgb = colorArgb,
            baseWidth = width,
            opacity = opacity,
            isClosed = kind != ShapeKind.LINE,
            fillColorArgb = if (kind != ShapeKind.LINE) fillColorArgb else null,
        )
    }

    fun createOvalPoints(
        cx: Float,
        cy: Float,
        rx: Float,
        ry: Float,
        segments: Int = 28,
    ): List<StrokePoint> {
        val list = ArrayList<StrokePoint>(segments + 1)
        for (i in 0..segments) {
            val angle = (i.toFloat() / segments) * (2f * PI.toFloat())
            list.add(
                StrokePoint(
                    x = cx + cos(angle) * rx,
                    y = cy + sin(angle) * ry,
                    pressure = 1f,
                )
            )
        }
        return list
    }

    /**
     * Ramer-Douglas-Peucker simplification + gentle Chaikin corner cutting for wobbly finger lines.
     */
    fun smoothAndSimplify(points: List<StrokePoint>, epsilon: Float = 3.5f): List<StrokePoint> {
        if (points.size <= 3) return points
        val simplified = rdpSimplify(points, epsilon)
        if (simplified.size <= 2) return simplified
        // One Chaikin smoothing pass keeping endpoints intact
        val out = ArrayList<StrokePoint>(simplified.size * 2)
        out.add(simplified.first())
        for (i in 0 until simplified.size - 1) {
            val a = simplified[i]
            val b = simplified[i + 1]
            if (i > 0) {
                out.add(
                    StrokePoint(
                        x = a.x * 0.75f + b.x * 0.25f,
                        y = a.y * 0.75f + b.y * 0.25f,
                        pressure = a.pressure * 0.75f + b.pressure * 0.25f,
                    )
                )
            }
            if (i < simplified.size - 2) {
                out.add(
                    StrokePoint(
                        x = a.x * 0.25f + b.x * 0.75f,
                        y = a.y * 0.25f + b.y * 0.75f,
                        pressure = a.pressure * 0.25f + b.pressure * 0.75f,
                    )
                )
            }
        }
        out.add(simplified.last())
        return out
    }

    private fun rdpSimplify(points: List<StrokePoint>, epsilon: Float): List<StrokePoint> {
        if (points.size < 3) return points
        val first = points.first()
        val last = points.last()
        var maxDist = 0f
        var index = 0
        for (i in 1 until points.size - 1) {
            val d = distancePointToSegment(points[i].x, points[i].y, first.x, first.y, last.x, last.y)
            if (d > maxDist) {
                maxDist = d
                index = i
            }
        }
        return if (maxDist > epsilon) {
            val left = rdpSimplify(points.subList(0, index + 1), epsilon)
            val right = rdpSimplify(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    /**
     * Recognizes rough hand-drawn lines or ellipses/circles and snaps them cleanly.
     */
    fun trySnapRoughShape(stroke: VectorStroke): VectorStroke? {
        val pts = stroke.points
        if (pts.size < 4) return null

        val first = pts.first()
        val last = pts.last()
        val endDist = distance(first, last)
        val totalLen = pathLength(pts)
        if (totalLen < 40f) return null

        // 1. Straight line detection
        val directDist = endDist
        if (directDist > 40f && totalLen / directDist < 1.12f) {
            var maxDev = 0f
            for (p in pts) {
                val dev = distancePointToSegment(p.x, p.y, first.x, first.y, last.x, last.y)
                if (dev > maxDev) maxDev = dev
            }
            if (maxDev < directDist * 0.06f) {
                return stroke.copy(
                    points = listOf(first.copy(pressure = 1f), last.copy(pressure = 1f)),
                    isClosed = false,
                )
            }
        }

        // 2. Closed oval/circle detection
        val bounds = computeBounds(stroke) ?: return null
        val diag = hypot(bounds.width, bounds.height).coerceAtLeast(1f)
        if (endDist < diag * 0.28f && pts.size >= 8) {
            val cx = bounds.centerX
            val cy = bounds.centerY
            val rx = (bounds.width - stroke.baseWidth) * 0.5f
            val ry = (bounds.height - stroke.baseWidth) * 0.5f
            if (rx > 12f && ry > 12f) {
                // Measure fit error to normalized ellipse
                var avgErr = 0f
                for (p in pts) {
                    val nx = (p.x - cx) / rx
                    val ny = (p.y - cy) / ry
                    avgErr += abs(hypot(nx, ny) - 1f)
                }
                avgErr /= pts.size
                if (avgErr < 0.22f) {
                    val isCircle = abs(rx - ry) / max(rx, ry) < 0.18f
                    val finalRx = if (isCircle) (rx + ry) * 0.5f else rx
                    val finalRy = if (isCircle) (rx + ry) * 0.5f else ry
                    return stroke.copy(
                        points = createOvalPoints(cx, cy, finalRx, finalRy, segments = 32),
                        isClosed = true,
                    )
                }
            }
        }
        return null
    }
}
