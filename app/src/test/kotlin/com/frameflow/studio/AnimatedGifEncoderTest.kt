package com.frameflow.studio

import com.frameflow.studio.export.AnimatedGifEncoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

class AnimatedGifEncoderTest {

    @Test
    fun encodesValidMultiFrameGif89aWithNetscapeLoopAndTrailer() {
        val w = 16
        val h = 16
        val f1Pixels = IntArray(w * h) { idx ->
            if (idx % 2 == 0) 0xFF000000.toInt() else 0xFFFF5C7A.toInt()
        }
        val f2Pixels = IntArray(w * h) { idx ->
            if (idx < w * 8) 0xFF34E8C7.toInt() else 0xFFFFFFFF.toInt()
        }

        val out = ByteArrayOutputStream()
        AnimatedGifEncoder.encodeRawFrames(
            frames = listOf(
                AnimatedGifEncoder.GifFrame(w, h, f1Pixels, delayCs = 8),
                AnimatedGifEncoder.GifFrame(w, h, f2Pixels, delayCs = 12),
            ),
            output = out,
        )

        val bytes = out.toByteArray()
        assertTrue(bytes.size > 800)

        // Header: GIF89a
        val header = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
        assertEquals("GIF89a", header)

        // Logical screen width & height (little endian)
        assertEquals(w, (bytes[6].toInt() and 0xFF) or ((bytes[7].toInt() and 0xFF) shl 8))
        assertEquals(h, (bytes[8].toInt() and 0xFF) or ((bytes[9].toInt() and 0xFF) shl 8))

        // Contains NETSCAPE2.0 loop extension
        val netscapeAscii = "NETSCAPE2.0".toByteArray(Charsets.US_ASCII)
        var foundNetscape = false
        for (i in 0..(bytes.size - netscapeAscii.size)) {
            if (bytes.copyOfRange(i, i + netscapeAscii.size).contentEquals(netscapeAscii)) {
                foundNetscape = true
                break
            }
        }
        assertTrue("Expected NETSCAPE2.0 looping block in GIF stream", foundNetscape)

        // Ends with GIF trailer 0x3B
        assertEquals(0x3B.toByte(), bytes.last())
    }

    @Test(expected = IllegalArgumentException::class)
    fun throwsOnEmptyFrameList() {
        AnimatedGifEncoder.encodeRawFrames(emptyList(), ByteArrayOutputStream())
    }

    @Test
    fun encodesLargeMultiFrameRichColorGifDecodableByImageIo() {
        val w = 72
        val h = 72
        val frames = (0 until 4).map { frameIdx ->
            val pixels = IntArray(w * h) { idx ->
                val x = idx % w
                val y = idx / w
                // > 4096 pixels per frame with dense color gradients to exercise palette quantization and LZW clearCode resets
                val r = ((x * 3 + frameIdx * 40) % 256)
                val g = ((y * 3 + frameIdx * 25) % 256)
                val b = (((x + y) * 2) % 256)
                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            AnimatedGifEncoder.GifFrame(w, h, pixels, delayCs = 6)
        }

        val out = ByteArrayOutputStream()
        AnimatedGifEncoder.encodeRawFrames(frames, out)
        val bytes = out.toByteArray()
        assertEquals(0x3B.toByte(), bytes.last())

        val readers = ImageIO.getImageReadersByFormatName("gif")
        assertTrue("Expected standard JVM GIF ImageReader", readers.hasNext())
        val reader = readers.next()
        try {
            ImageIO.createImageInputStream(ByteArrayInputStream(bytes)).use { iis ->
                reader.input = iis
                val numImages = reader.getNumImages(true)
                assertEquals(4, numImages)
                for (i in 0 until numImages) {
                    val decoded = reader.read(i)
                    assertEquals(w, decoded.width)
                    assertEquals(h, decoded.height)
                }
            }
        } finally {
            reader.dispose()
        }
    }
}
