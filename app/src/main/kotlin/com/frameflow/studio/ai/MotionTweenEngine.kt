package com.frameflow.studio.ai

import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.LayerMeta
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.model.newId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

object MotionTweenEngine {

    /**
     * Generates [count] smooth intermediate frames between [startFrame] and [endFrame].
     * Matches strokes per layer using centroid & length proximity, aligns point orientation,
     * resamples to uniform arc-length points, and interpolates geometry, width, opacity, and colors.
     */
    fun generateInBetweens(
        startFrame: AnimationFrame,
        endFrame: AnimationFrame,
        layers: List<LayerMeta>,
        count: Int,
        easing: TweenEasing = TweenEasing.EASE_IN_OUT,
    ): List<AnimationFrame> {
        val safeCount = count.coerceIn(1, 16)
        val result = ArrayList<AnimationFrame>(safeCount)

        for (step in 1..safeCount) {
            val linearT = step.toFloat() / (safeCount + 1)
            val t = applyEasing(linearT, easing)
            val newLayers = LinkedHashMap<String, LayerContent>()

            for (layer in layers) {
                val aStrokes = startFrame.contentFor(layer.id).strokes
                val bStrokes = endFrame.contentFor(layer.id).strokes
                val interpolatedStrokes = interpolateStrokeLists(aStrokes, bStrokes, t)
                val fills = if (t < 0.5f) startFrame.contentFor(layer.id).fills else endFrame.contentFor(layer.id).fills
                if (interpolatedStrokes.isNotEmpty() || fills.isNotEmpty()) {
                    newLayers[layer.id] = LayerContent(strokes = interpolatedStrokes, fills = fills)
                }
            }

            result.add(
                AnimationFrame(
                    id = newId(),
                    holdCount = 1,
                    layers = newLayers,
                )
            )
        }
        return result
    }

    fun applyEasing(t: Float, easing: TweenEasing): Float {
        val x = t.coerceIn(0f, 1f)
        return when (easing) {
            TweenEasing.LINEAR -> x
            TweenEasing.EASE_IN_OUT ->
                if (x < 0.5f) 4f * x * x * x else 1f - (-2f * x + 2f).pow(3) / 2f
            TweenEasing.BOUNCE -> bounceOut(x)
            TweenEasing.ANTICIPATE -> {
                val c1 = 1.70158f
                val c2 = c1 * 1.525f
                if (x < 0.5f) {
                    ((2f * x).pow(2) * ((c2 + 1f) * 2f * x - c2)) / 2f
                } else {
                    ((2f * x - 2f).pow(2) * ((c2 + 1f) * (x * 2f - 2f) + c2) + 2f) / 2f
                }.coerceIn(-0.08f, 1.08f)
            }
        }
    }

    private fun bounceOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        return when {
            x < 1f / d1 -> n1 * x * x
            x < 2f / d1 -> {
                val t = x - 1.5f / d1
                n1 * t * t + 0.75f
            }
            x < 2.5f / d1 -> {
                val t = x - 2.25f / d1
                n1 * t * t + 0.9375f
            }
            else -> {
                val t = x - 2.625f / d1
                n1 * t * t + 0.984375f
            }
        }
    }

    private fun interpolateStrokeLists(
        aStrokes: List<VectorStroke>,
        bStrokes: List<VectorStroke>,
        t: Float,
    ): List<VectorStroke> {
        if (aStrokes.isEmpty() && bStrokes.isEmpty()) return emptyList()

        // Pair strokes greedily by lowest cost (centroid distance + length ratio)
        val usedB = BooleanArray(bStrokes.size)
        val matched = mutableListOf<Pair<VectorStroke, VectorStroke>>()
        val unmatchedA = mutableListOf<VectorStroke>()

        val aCentroids = aStrokes.map { StrokeGeometry.computeCentroid(it) }
        val bCentroids = bStrokes.map { StrokeGeometry.computeCentroid(it) }
        val aLens = aStrokes.map { StrokeGeometry.pathLength(it.points, it.isClosed) }
        val bLens = bStrokes.map { StrokeGeometry.pathLength(it.points, it.isClosed) }

        // If stroke counts match and order looks natural, also bias slightly toward same index
        for (i in aStrokes.indices) {
            val ac = aCentroids[i]
            val al = aLens[i]
            var bestJ = -1
            var bestScore = Float.POSITIVE_INFINITY
            for (j in bStrokes.indices) {
                if (usedB[j]) continue
                val bc = bCentroids[j]
                val bl = bLens[j]
                val distCost = hypot(ac.x - bc.x, ac.y - bc.y)
                val lenCost = abs(al - bl) * 0.35f
                val orderBias = abs(i - j) * 28f
                val score = distCost + lenCost + orderBias
                if (score < bestScore) {
                    bestScore = score
                    bestJ = j
                }
            }
            if (bestJ >= 0) {
                usedB[bestJ] = true
                matched.add(aStrokes[i] to bStrokes[bestJ])
            } else {
                unmatchedA.add(aStrokes[i])
            }
        }

        val out = ArrayList<VectorStroke>(matched.size + unmatchedA.size + bStrokes.size)

        for ((sa, sb) in matched) {
            out.add(interpolatePair(sa, sb, t))
        }

        // Fade out strokes only in A
        val clampedT = t.coerceIn(0f, 1f)
        for (sa in unmatchedA) {
            val fadeAlpha = (sa.opacity * (1f - clampedT)).coerceIn(0f, 1f)
            if (fadeAlpha > 0.03f) {
                val c = StrokeGeometry.computeCentroid(sa)
                val scale = 1f - clampedT * 0.35f
                out.add(
                    sa.copy(
                        id = newId(),
                        opacity = fadeAlpha,
                        points = sa.points.map { p ->
                            p.copy(
                                x = c.x + (p.x - c.x) * scale,
                                y = c.y + (p.y - c.y) * scale,
                            )
                        },
                    )
                )
            }
        }

        // Fade in strokes only in B
        for (j in bStrokes.indices) {
            if (!usedB[j]) {
                val sb = bStrokes[j]
                val fadeAlpha = (sb.opacity * clampedT).coerceIn(0f, 1f)
                if (fadeAlpha > 0.03f) {
                    val c = StrokeGeometry.computeCentroid(sb)
                    val scale = 0.65f + clampedT * 0.35f
                    out.add(
                        sb.copy(
                            id = newId(),
                            opacity = fadeAlpha,
                            points = sb.points.map { p ->
                                p.copy(
                                    x = c.x + (p.x - c.x) * scale,
                                    y = c.y + (p.y - c.y) * scale,
                                )
                            },
                        )
                    )
                }
            }
        }
        return out
    }

    private fun interpolatePair(
        a: VectorStroke,
        b: VectorStroke,
        t: Float,
    ): VectorStroke {
        val sampleCount = 36
        val resA = StrokeGeometry.resamplePoints(a.points, sampleCount, a.isClosed)
        var resB = StrokeGeometry.resamplePoints(b.points, sampleCount, b.isClosed)

        // Compare centroid-centered point vectors so horizontal/vertical translation
        // never masks reverse-drawn stroke orientation!
        if (resA.size == resB.size && resA.size >= 2) {
            val ca = StrokeGeometry.computeCentroid(a.copy(points = resA))
            val cb = StrokeGeometry.computeCentroid(b.copy(points = resB))
            var forwardErr = 0f
            var reverseErr = 0f
            for (i in resA.indices) {
                val ax = resA[i].x - ca.x
                val ay = resA[i].y - ca.y
                val fbx = resB[i].x - cb.x
                val fby = resB[i].y - cb.y
                val rIdx = resB.size - 1 - i
                val rbx = resB[rIdx].x - cb.x
                val rby = resB[rIdx].y - cb.y
                forwardErr += hypot(ax - fbx, ay - fby)
                reverseErr += hypot(ax - rbx, ay - rby)
            }
            if (reverseErr < forwardErr * 0.92f) {
                resB = resB.reversed()
            }
        }

        val pts = resA.zip(resB).map { (pa, pb) ->
            StrokePoint(
                x = pa.x + (pb.x - pa.x) * t,
                y = pa.y + (pb.y - pa.y) * t,
                pressure = (pa.pressure + (pb.pressure - pa.pressure) * t).coerceIn(0.25f, 1.2f),
            )
        }

        val clampedT = t.coerceIn(0f, 1f)
        val fillColor = when {
            a.fillColorArgb != null && b.fillColorArgb != null ->
                lerpArgb(a.fillColorArgb, b.fillColorArgb, clampedT)
            a.fillColorArgb != null && clampedT < 0.5f -> a.fillColorArgb
            b.fillColorArgb != null && clampedT >= 0.5f -> b.fillColorArgb
            else -> null
        }

        return VectorStroke(
            id = newId(),
            points = pts,
            brushType = if (clampedT < 0.5f) a.brushType else b.brushType,
            colorArgb = lerpArgb(a.colorArgb, b.colorArgb, clampedT),
            baseWidth = max(1f, a.baseWidth + (b.baseWidth - a.baseWidth) * clampedT),
            opacity = (a.opacity + (b.opacity - a.opacity) * clampedT).coerceIn(0f, 1f),
            isClosed = a.isClosed && b.isClosed,
            fillColorArgb = fillColor,
        )
    }

    fun lerpArgb(c1: Long, c2: Long, t: Float): Long {
        val f = t.coerceIn(0f, 1f)
        val a1 = ((c1 ushr 24) and 0xFF).toInt()
        val r1 = ((c1 ushr 16) and 0xFF).toInt()
        val g1 = ((c1 ushr 8) and 0xFF).toInt()
        val b1 = (c1 and 0xFF).toInt()

        val a2 = ((c2 ushr 24) and 0xFF).toInt()
        val r2 = ((c2 ushr 16) and 0xFF).toInt()
        val g2 = ((c2 ushr 8) and 0xFF).toInt()
        val b2 = (c2 and 0xFF).toInt()

        val a = (a1 + (a2 - a1) * f).roundToInt().coerceIn(0, 255)
        val r = (r1 + (r2 - r1) * f).roundToInt().coerceIn(0, 255)
        val g = (g1 + (g2 - g1) * f).roundToInt().coerceIn(0, 255)
        val b = (b1 + (b2 - b1) * f).roundToInt().coerceIn(0, 255)

        return ((a.toLong() shl 24) or (r.toLong() shl 16) or (g.toLong() shl 8) or b.toLong())
    }
}
