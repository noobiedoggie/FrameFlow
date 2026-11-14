package com.frameflow.studio

import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.ShapeKind
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs

class StrokeGeometryTest {

    @Test
    fun partialEraserSplitsHorizontalStrokeInMiddle() {
        val line = VectorStroke(
            id = "s1",
            points = listOf(StrokePoint(100f, 500f), StrokePoint(900f, 500f)),
            baseWidth = 12f,
        )
        val eraserSwipe = listOf(StrokePoint(500f, 440f), StrokePoint(500f, 560f))

        val remaining = StrokeGeometry.eraseFromStrokes(
            strokes = listOf(line),
            eraserPath = eraserSwipe,
            eraserRadius = 35f,
            wholeStroke = false,
        )

        assertEquals("Erasing across middle of a 2-point line should split it into 2 strokes", 2, remaining.size)
        val leftMaxX = remaining[0].points.maxOf { it.x }
        val rightMinX = remaining[1].points.minOf { it.x }
        assertTrue(leftMaxX < 490f)
        assertTrue(rightMinX > 510f)
    }

    @Test
    fun wholeStrokeEraserRemovesTouchedStrokeCompletely() {
        val s1 = VectorStroke(id = "a", points = listOf(StrokePoint(100f, 100f), StrokePoint(400f, 100f)))
        val s2 = VectorStroke(id = "b", points = listOf(StrokePoint(100f, 700f), StrokePoint(400f, 700f)))

        val remaining = StrokeGeometry.eraseFromStrokes(
            strokes = listOf(s1, s2),
            eraserPath = listOf(StrokePoint(250f, 105f)),
            eraserRadius = 24f,
            wholeStroke = true,
        )

        assertEquals(listOf("b"), remaining.map { it.id })
    }

    @Test
    fun lassoSelectsAndTransformsOnlyEnclosedStrokes() {
        val inside = VectorStroke(id = "inside", points = listOf(StrokePoint(220f, 220f), StrokePoint(280f, 280f)))
        val outside = VectorStroke(id = "outside", points = listOf(StrokePoint(820f, 820f), StrokePoint(900f, 900f)))
        val lasso = listOf(
            StrokePoint(150f, 150f),
            StrokePoint(350f, 150f),
            StrokePoint(350f, 350f),
            StrokePoint(150f, 350f),
        )

        val selected = StrokeGeometry.selectStrokesInLasso(listOf(inside, outside), lasso)
        assertEquals(setOf("inside"), selected)

        val transformed = StrokeGeometry.transformStrokes(
            strokes = listOf(inside, outside),
            selectedIds = selected,
            dx = 100f,
            dy = -50f,
            scale = 1.5f,
            rotationRadians = (PI / 2.0).toFloat(),
        )
        val movedInside = transformed.first { it.id == "inside" }
        val untouchedOutside = transformed.first { it.id == "outside" }

        assertEquals(350f, StrokeGeometry.computeCentroid(movedInside).x, 2f)
        assertEquals(200f, StrokeGeometry.computeCentroid(movedInside).y, 2f)
        assertEquals(outside.points, untouchedOutside.points)
    }

    @Test
    fun roughShapeSnapRecognizesCircleAndStraightLine() {
        val roughCirclePts = (0..36).map { i ->
            val angle = (2.0 * PI * i / 36.0).toFloat()
            val jitter = if (i % 2 == 0) 6f else -5f
            StrokePoint(
                x = 500f + (150f + jitter) * kotlin.math.cos(angle),
                y = 500f + (150f - jitter) * kotlin.math.sin(angle),
            )
        }
        val snappedCircle = StrokeGeometry.trySnapRoughShape(
            VectorStroke(points = roughCirclePts, baseWidth = 10f),
        )
        assertNotNull(snappedCircle)
        assertTrue(snappedCircle!!.isClosed)

        val roughLinePts = (0..10).map { i ->
            StrokePoint(100f + i * 50f, 300f + (if (i % 2 == 0) 2f else -2f))
        }
        val snappedLine = StrokeGeometry.trySnapRoughShape(
            VectorStroke(points = roughLinePts, baseWidth = 10f),
        )
        assertNotNull(snappedLine)
        assertEquals(2, snappedLine!!.points.size)
        assertEquals(100f, snappedLine.points[0].x, 0.1f)
        assertEquals(600f, snappedLine.points[1].x, 0.1f)

        assertNull(StrokeGeometry.trySnapRoughShape(VectorStroke(points = listOf(StrokePoint(0f, 0f)))))
    }

    @Test
    fun createShapeStrokeBuildsClosedPolygonWithOptionalFill() {
        val rect = StrokeGeometry.createShapeStroke(
            kind = ShapeKind.RECTANGLE,
            start = StrokePoint(100f, 200f),
            end = StrokePoint(300f, 500f),
            brushType = BrushType.MARKER,
            colorArgb = 0xFF000000,
            width = 12f,
            fillColorArgb = 0xFF7C8BFF,
        )
        assertTrue(rect.isClosed)
        assertEquals(0xFF7C8BFFL, rect.fillColorArgb)
        assertEquals(5, rect.points.size)
        assertTrue(abs(rect.points.first().x - 100f) < 0.01f)
    }
}
