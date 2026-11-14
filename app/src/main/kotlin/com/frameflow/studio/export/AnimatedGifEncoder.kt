package com.frameflow.studio.export

import android.graphics.Bitmap
import androidx.core.graphics.scale
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Pure-Kotlin GIF89a animated encoder with 256-color palette mapping + LZW compression.
 * Zero native dependencies and directly testable on JVM & Android.
 */
object AnimatedGifEncoder {

    data class GifFrame(
        val width: Int,
        val height: Int,
        val argbPixels: IntArray,
        val delayCs: Int,
    )

    fun encodeBitmaps(
        frames: List<Pair<Bitmap, Int>>, // Pair(bitmap, delayCentiseconds)
        output: OutputStream,
    ) {
        require(frames.isNotEmpty()) { "Cannot encode empty frame list." }
        val first = frames.first().first
        val w = first.width
        val h = first.height
        val gifFrames = frames.map { (bmp, delayCs) ->
            val scaled = if (bmp.width == w && bmp.height == h) {
                bmp
            } else {
                bmp.scale(w, h)
            }
            val pixels = IntArray(w * h)
            scaled.getPixels(pixels, 0, w, 0, 0, w, h)
            if (scaled !== bmp) scaled.recycle()
            GifFrame(w, h, pixels, delayCs.coerceAtLeast(2))
        }
        encodeRawFrames(gifFrames, output)
    }

    fun encodeRawFrames(
        frames: List<GifFrame>,
        output: OutputStream,
    ) {
        require(frames.isNotEmpty()) { "Cannot encode empty frame list." }
        val width = frames.first().width
        val height = frames.first().height
        require(width > 0 && height > 0) { "GIF dimensions must be positive." }

        val palette = buildAdaptivePalette(frames)
        val colorMap = HashMap<Int, Int>(4096)

        // Header: GIF89a
        output.write("GIF89a".toByteArray(Charsets.US_ASCII))

        // Logical Screen Descriptor
        writeShortLE(output, width)
        writeShortLE(output, height)
        // Global Color Table Flag (1), Color Resolution (7 -> 8bit), Sort (0), Size (7 -> 2^(7+1) = 256 colors)
        output.write(0xF7)
        output.write(0x00) // Background color index
        output.write(0x00) // Pixel aspect ratio

        // Global Color Table (256 * 3 bytes)
        for (i in 0 until 256) {
            val rgb = palette[i]
            output.write((rgb shr 16) and 0xFF)
            output.write((rgb shr 8) and 0xFF)
            output.write(rgb and 0xFF)
        }

        // Netscape 2.0 Application Extension for infinite looping
        output.write(0x21)
        output.write(0xFF)
        output.write(11)
        output.write("NETSCAPE2.0".toByteArray(Charsets.US_ASCII))
        output.write(3)
        output.write(1)
        writeShortLE(output, 0) // 0 = loop forever
        output.write(0)

        for (frame in frames) {
            require(frame.width == width && frame.height == height) {
                "All GIF frames must share identical dimensions."
            }
            // Graphic Control Extension
            output.write(0x21)
            output.write(0xF9)
            output.write(4)
            output.write(0x04) // Disposal method 1 (do not dispose)
            writeShortLE(output, frame.delayCs.coerceIn(2, 65535))
            output.write(0) // Transparent color index (unused)
            output.write(0)

            // Image Descriptor
            output.write(0x2C)
            writeShortLE(output, 0)
            writeShortLE(output, 0)
            writeShortLE(output, width)
            writeShortLE(output, height)
            output.write(0x00) // Use Global Color Table

            val indices = ByteArray(width * height)
            val px = frame.argbPixels
            for (i in px.indices) {
                val rgb = px[i] and 0x00FFFFFF
                indices[i] = mapRgbToPaletteIndex(rgb, palette, colorMap).toByte()
            }

            // Image Data (LZW minimum code size = 8 for 256 colors)
            output.write(8)
            writeLzwData(indices, minCodeSize = 8, output = output)
            output.write(0) // Block terminator
        }

        // GIF Trailer
        output.write(0x3B)
        output.flush()
    }

    /**
     * Builds a 256-color palette combining a 6x6x6 RGB cube (216 colors),
     * 16 grayscale shades, and up to 24 most frequent custom colors from the frames!
     */
    private fun buildAdaptivePalette(frames: List<GifFrame>): IntArray {
        val palette = IntArray(256)
        var idx = 0
        val levels = intArrayOf(0, 51, 102, 153, 204, 255)
        for (r in levels) {
            for (g in levels) {
                for (b in levels) {
                    palette[idx++] = (r shl 16) or (g shl 8) or b
                }
            }
        }
        // 16 grayscale stops (idx 216..231)
        for (k in 0 until 16) {
            val v = (k * 255f / 15f).roundToInt().coerceIn(0, 255)
            palette[idx++] = (v shl 16) or (v shl 8) or v
        }

        // Sample top 24 exact colors from frames for crisp UI/accent colors
        val freq = HashMap<Int, Int>(512)
        for (f in frames) {
            val step = (f.argbPixels.size / 1500).coerceAtLeast(1)
            var i = 0
            while (i < f.argbPixels.size) {
                val rgb = f.argbPixels[i] and 0x00FFFFFF
                freq[rgb] = (freq[rgb] ?: 0) + 1
                i += step
            }
        }
        val existingSet = palette.take(idx).toHashSet()
        val topCustom = freq.entries
            .filter { it.key !in existingSet }
            .sortedByDescending { it.value }
            .take(256 - idx)
        for (entry in topCustom) {
            palette[idx++] = entry.key
        }
        while (idx < 256) {
            palette[idx++] = 0xFFFFFF
        }
        return palette
    }

    private fun mapRgbToPaletteIndex(
        rgb: Int,
        palette: IntArray,
        cache: HashMap<Int, Int>,
    ): Int {
        cache[rgb]?.let { return it }
        val r = (rgb shr 16) and 0xFF
        val g = (rgb shr 8) and 0xFF
        val b = rgb and 0xFF
        var bestIdx = 0
        var bestDist = Int.MAX_VALUE
        for (i in palette.indices) {
            val prgb = palette[i]
            val dr = r - ((prgb shr 16) and 0xFF)
            val dg = g - ((prgb shr 8) and 0xFF)
            val db = b - (prgb and 0xFF)
            val d = dr * dr + dg * dg + db * db
            if (d == 0) {
                bestIdx = i
                break
            }
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        if (cache.size < 32768) {
            cache[rgb] = bestIdx
        }
        return bestIdx
    }

    private fun writeLzwData(
        indices: ByteArray,
        minCodeSize: Int,
        output: OutputStream,
    ) {
        val clearCode = 1 shl minCodeSize
        val eoiCode = clearCode + 1
        var codeSize = minCodeSize + 1
        var nextCode = eoiCode + 1
        val maxTableSize = 4096

        val subBlock = ByteArray(256)
        var subLen = 0
        var bitBuf = 0
        var bitCount = 0

        fun flushSubBlock() {
            if (subLen > 0) {
                output.write(subLen)
                output.write(subBlock, 0, subLen)
                subLen = 0
            }
        }

        fun emitCode(code: Int) {
            bitBuf = bitBuf or (code shl bitCount)
            bitCount += codeSize
            while (bitCount >= 8) {
                subBlock[subLen++] = (bitBuf and 0xFF).toByte()
                if (subLen == 255) flushSubBlock()
                bitBuf = bitBuf ushr 8
                bitCount -= 8
            }
        }

        val dict = HashMap<Long, Int>(4096)

        fun resetDict() {
            dict.clear()
            codeSize = minCodeSize + 1
            nextCode = eoiCode + 1
        }

        emitCode(clearCode)
        resetDict()

        if (indices.isEmpty()) {
            emitCode(eoiCode)
            if (bitCount > 0) {
                subBlock[subLen++] = (bitBuf and 0xFF).toByte()
            }
            flushSubBlock()
            return
        }

        var prefix = indices[0].toInt() and 0xFF
        for (i in 1 until indices.size) {
            val ch = indices[i].toInt() and 0xFF
            val key = (prefix.toLong() shl 8) or ch.toLong()
            val existing = dict[key]
            if (existing != null) {
                prefix = existing
            } else {
                emitCode(prefix)
                if (nextCode < maxTableSize) {
                    dict[key] = nextCode++
                    if (nextCode > (1 shl codeSize) && codeSize < 12) {
                        codeSize++
                    }
                } else {
                    emitCode(clearCode)
                    resetDict()
                }
                prefix = ch
            }
        }

        emitCode(prefix)
        emitCode(eoiCode)
        if (bitCount > 0) {
            subBlock[subLen++] = (bitBuf and 0xFF).toByte()
        }
        flushSubBlock()
    }

    private fun writeShortLE(output: OutputStream, value: Int) {
        output.write(value and 0xFF)
        output.write((value ushr 8) and 0xFF)
    }
}
