package com.frameflow.studio

import com.frameflow.studio.engine.FloodFillEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FloodFillEngineTest {

    @Test
    fun floodFillFillsClosedBoxWithoutLeakingOutside() {
        val w = 24
        val h = 24
        val src = IntArray(w * h) { 0x00000000 }
        val borderColor = 0xFF1A1E29.toInt()
        // Draw hollow closed square from (4,4) to (19,19)
        for (x in 4..19) {
            src[4 * w + x] = borderColor
            src[19 * w + x] = borderColor
        }
        for (y in 4..19) {
            src[y * w + 4] = borderColor
            src[y * w + 19] = borderColor
        }

        val mask = IntArray(w * h) { 0 }
        val fillColor = 0xFF34E8C7.toInt()
        val result = FloodFillEngine.floodFillToMask(
            sourcePixels = src,
            outMaskPixels = mask,
            width = w,
            height = h,
            seedX = 10,
            seedY = 10,
            fillColor = fillColor,
        )

        // Interior is 5..18 x 5..18 = 14 * 14 = 196 pixels
        assertEquals(196, result.filledCount)
        assertEquals(5, result.minX)
        assertEquals(5, result.minY)
        assertEquals(18, result.maxX)
        assertEquals(18, result.maxY)
        assertEquals(0, mask[0])
        assertEquals(fillColor, mask[10 * w + 10])
    }

    @Test
    fun floodFillOutOfBoundsAndAlreadyFilledReturnZero() {
        val w = 10
        val h = 10
        val fillColor = 0xFFFF5C7A.toInt()
        val alreadyFilled = IntArray(w * h) { fillColor }
        val mask = IntArray(w * h)

        val outOfBounds = FloodFillEngine.floodFillToMask(alreadyFilled, mask, w, h, -1, 5, fillColor)
        assertEquals(0, outOfBounds.filledCount)

        val sameColor = FloodFillEngine.floodFillToMask(alreadyFilled, mask, w, h, 5, 5, fillColor)
        assertEquals(0, sameColor.filledCount)
    }

    @Test
    fun floodFillRespectsColorToleranceAcrossAntiAliasedInterior() {
        val w = 12
        val h = 12
        val src = IntArray(w * h) { 0xFFFFFFFF.toInt() }
        // Draw solid barrier line at y = 6
        for (x in 0 until w) {
            src[6 * w + x] = 0xFF000000.toInt()
        }
        val mask = IntArray(w * h)
        val res = FloodFillEngine.floodFillToMask(src, mask, w, h, 3, 2, 0xFF7C8BFF.toInt())

        // Rows 0..5 = 6 * 12 = 72 pixels
        assertEquals(72, res.filledCount)
        assertTrue(mask[8 * w + 3] == 0)
    }
}
