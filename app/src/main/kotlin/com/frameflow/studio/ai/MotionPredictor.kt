package com.frameflow.studio.ai

import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.LayerMeta
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.model.newId
import kotlin.math.hypot

object MotionPredictor {

    /**
     * Analyzes recent frames before [targetFrameIndex] to predict a subtle ghost continuation
     * of the motion onto the current frame. Useful for phone drawing without losing arc continuity!
     */
    fun predictNextFrameStrokes(
        frames: List<AnimationFrame>,
        targetFrameIndex: Int,
        layerId: String,
    ): List<VectorStroke> {
        val prevIdx = targetFrameIndex - 1
        val prev2Idx = targetFrameIndex - 2
        if (prevIdx !in frames.indices) return emptyList()

        val prevStrokes = frames[prevIdx].contentFor(layerId).strokes
        if (prevStrokes.isEmpty()) return emptyList()

        // If we have 2 prior frames, compute true velocity vector (+ slight momentum)
        val prev2Strokes = if (prev2Idx in frames.indices) {
            frames[prev2Idx].contentFor(layerId).strokes
        } else {
            emptyList()
        }

        if (prev2Strokes.isEmpty()) {
            // Single previous frame: suggest a subtle 16px forward nudge so user has a starting ghost
            return prevStrokes.map { s ->
                s.copy(
                    id = newId(),
                    points = s.points.map { it.copy(x = it.x + 18f) },
                )
            }
        }

        val c2 = StrokeGeometry.computeBounds(prev2Strokes)
        val c1 = StrokeGeometry.computeBounds(prevStrokes)
        if (c2 == null || c1 == null) return emptyList()

        val dx = (c1.centerX - c2.centerX).coerceIn(-360f, 360f)
        val dy = (c1.centerY - c2.centerY).coerceIn(-360f, 360f)

        // If almost static across frames, add subtle breathing motion or keep position
        val moveLen = hypot(dx, dy)
        val stepDx = if (moveLen < 2f) 14f else dx
        val stepDy = if (moveLen < 2f) 0f else dy

        return prevStrokes.mapIndexed { idx, s ->
            val matchPrev2 = prev2Strokes.getOrNull(idx)
            if (matchPrev2 != null && prevStrokes.size == prev2Strokes.size) {
                val p2c = StrokeGeometry.computeCentroid(matchPrev2)
                val p1c = StrokeGeometry.computeCentroid(s)
                val sdx = (p1c.x - p2c.x).coerceIn(-360f, 360f)
                val sdy = (p1c.y - p2c.y).coerceIn(-360f, 360f)
                s.copy(
                    id = newId(),
                    points = s.points.map { it.copy(x = it.x + sdx, y = it.y + sdy) },
                )
            } else {
                s.copy(
                    id = newId(),
                    points = s.points.map { it.copy(x = it.x + stepDx, y = it.y + stepDy) },
                )
            }
        }
    }

    /**
     * AI Sketch Polish: smooths shaky finger strokes and optionally snaps rough lines/ovals.
     */
    fun polishLayer(
        content: LayerContent,
        snapShapes: Boolean = true,
    ): LayerContent {
        val polishedStrokes = content.strokes.map { stroke ->
            if (snapShapes) {
                StrokeGeometry.trySnapRoughShape(stroke)?.let { return@map it }
            }
            val smoothed = StrokeGeometry.smoothAndSimplify(stroke.points, epsilon = 4.0f)
            stroke.copy(points = smoothed)
        }
        return content.copy(strokes = polishedStrokes)
    }

    fun polishFrame(
        frame: AnimationFrame,
        layers: List<LayerMeta>,
        snapShapes: Boolean = true,
    ): AnimationFrame {
        val updated = LinkedHashMap<String, LayerContent>()
        for (layer in layers) {
            if (layer.locked) {
                frame.layers[layer.id]?.let { updated[layer.id] = it }
                continue
            }
            val content = frame.contentFor(layer.id)
            if (content.strokes.isNotEmpty() || content.fills.isNotEmpty()) {
                updated[layer.id] = polishLayer(content, snapShapes)
            }
        }
        return frame.copy(layers = updated)
    }
}
