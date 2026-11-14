package com.frameflow.studio.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.media.Image
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import androidx.core.graphics.createBitmap
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Hardware/platform H.264 MP4 exporter using [MediaCodec] YUV420Flexible buffers + [MediaMuxer]
 * so exact frame presentation timestamps (`ptsUs` from [fps] and per-frame `holdCount`) are preserved.
 */
object Mp4VideoExporter {

    internal data class Yuv420Frame(
        val y: ByteArray,
        val u: ByteArray,
        val v: ByteArray,
    )

    internal fun alignedVideoDimensions(width: Int, height: Int): Pair<Int, Int> {
        val safeW = width.coerceAtLeast(16)
        val safeH = height.coerceAtLeast(16)
        val alignedW = ((safeW + 15) / 16) * 16
        val alignedH = ((safeH + 15) / 16) * 16
        return alignedW to alignedH
    }

    internal fun argbToYuv420(argb: IntArray, width: Int, height: Int): Yuv420Frame {
        require(width % 2 == 0 && height % 2 == 0) { "YUV420 requires even dimensions ($width x $height)" }
        require(argb.size >= width * height) { "ARGB buffer too small" }

        val yPlane = ByteArray(width * height)
        val uvWidth = width / 2
        val uvHeight = height / 2
        val uPlane = ByteArray(uvWidth * uvHeight)
        val vPlane = ByteArray(uvWidth * uvHeight)

        for (y in 0 until height) {
            val rowOffset = y * width
            for (x in 0 until width) {
                val px = argb[rowOffset + x]
                val r = (px ushr 16) and 0xFF
                val g = (px ushr 8) and 0xFF
                val b = px and 0xFF
                val yVal = (((66 * r + 129 * g + 25 * b + 128) shr 8) + 16).coerceIn(0, 255)
                yPlane[rowOffset + x] = yVal.toByte()
            }
        }

        for (cy in 0 until uvHeight) {
            val y0 = cy * 2
            val y1 = y0 + 1
            for (cx in 0 until uvWidth) {
                val x0 = cx * 2
                val x1 = x0 + 1
                val p00 = argb[y0 * width + x0]
                val p01 = argb[y0 * width + x1]
                val p10 = argb[y1 * width + x0]
                val p11 = argb[y1 * width + x1]
                val r = (((p00 ushr 16) and 0xFF) + ((p01 ushr 16) and 0xFF) +
                    ((p10 ushr 16) and 0xFF) + ((p11 ushr 16) and 0xFF)) ushr 2
                val g = (((p00 ushr 8) and 0xFF) + ((p01 ushr 8) and 0xFF) +
                    ((p10 ushr 8) and 0xFF) + ((p11 ushr 8) and 0xFF)) ushr 2
                val b = ((p00 and 0xFF) + (p01 and 0xFF) + (p10 and 0xFF) + (p11 and 0xFF)) ushr 2
                val uVal = (((-38 * r - 74 * g + 112 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val vVal = (((112 * r - 94 * g - 18 * b + 128) shr 8) + 128).coerceIn(0, 255)
                val uvIdx = cy * uvWidth + cx
                uPlane[uvIdx] = uVal.toByte()
                vPlane[uvIdx] = vVal.toByte()
            }
        }

        return Yuv420Frame(y = yPlane, u = uPlane, v = vPlane)
    }

    fun exportBitmapsToMp4(
        frames: List<Pair<Bitmap, Int>>, // Pair(bitmap, holdCount)
        fps: Int,
        outputFile: File,
    ) {
        require(frames.isNotEmpty()) { "No frames to export." }
        val first = frames.first().first
        val (videoWidth, videoHeight) = alignedVideoDimensions(first.width, first.height)

        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, videoWidth, videoHeight).apply {
            setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible,
            )
            setInteger(MediaFormat.KEY_BIT_RATE, 4_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps.coerceIn(1, 60))
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
        }

        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var trackIndex = -1

        val argbScratch = IntArray(videoWidth * videoHeight)
        val frameCanvasBitmap = createBitmap(videoWidth, videoHeight)
        val fitCanvas = Canvas(frameCanvasBitmap)
        val fitPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        try {
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            encoder.start()

            if (outputFile.exists()) outputFile.delete()
            muxer = MediaMuxer(outputFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val bufferInfo = MediaCodec.BufferInfo()
            val frameDurationUs = 1_000_000L / fps.coerceIn(1, 60)
            var ptsUs = 0L
            val yuvByteSize = videoWidth * videoHeight * 3 / 2

            fun drainEncoder(endOfStream: Boolean) {
                var emptyTries = 0
                while (true) {
                    val outIdx = encoder.dequeueOutputBuffer(bufferInfo, 10_000L)
                    when {
                        outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> {
                            if (!endOfStream) break
                            if (++emptyTries > 250) break
                        }
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            if (!muxerStarted) {
                                trackIndex = muxer.addTrack(encoder.outputFormat)
                                muxer.start()
                                muxerStarted = true
                            }
                        }
                        outIdx >= 0 -> {
                            emptyTries = 0
                            val encoded = encoder.getOutputBuffer(outIdx)
                            if (encoded != null &&
                                (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 &&
                                bufferInfo.size > 0 &&
                                muxerStarted
                            ) {
                                encoded.position(bufferInfo.offset)
                                encoded.limit(bufferInfo.offset + bufferInfo.size)
                                muxer.writeSampleData(trackIndex, encoded, bufferInfo)
                            }
                            encoder.releaseOutputBuffer(outIdx, false)
                            if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
                        }
                    }
                }
            }

            fun dequeueInputOrDrain(): Int {
                repeat(250) {
                    val idx = encoder.dequeueInputBuffer(10_000L)
                    if (idx >= 0) return idx
                    drainEncoder(endOfStream = false)
                }
                throw IllegalStateException("MediaCodec input buffer timed out")
            }

            for ((bmp, hold) in frames) {
                fitCanvas.drawColor(android.graphics.Color.BLACK)
                val scale = min(videoWidth / bmp.width.toFloat(), videoHeight / bmp.height.toFloat())
                val drawW = (bmp.width * scale).roundToInt().coerceAtLeast(1)
                val drawH = (bmp.height * scale).roundToInt().coerceAtLeast(1)
                val left = (videoWidth - drawW) / 2
                val top = (videoHeight - drawH) / 2
                fitCanvas.drawBitmap(bmp, null, Rect(left, top, left + drawW, top + drawH), fitPaint)
                frameCanvasBitmap.getPixels(argbScratch, 0, videoWidth, 0, 0, videoWidth, videoHeight)

                val yuv = argbToYuv420(argbScratch, videoWidth, videoHeight)
                val repeats = hold.coerceAtLeast(1)
                repeat(repeats) {
                    val inIdx = dequeueInputOrDrain()
                    val image = runCatching { encoder.getInputImage(inIdx) }.getOrNull()
                    if (image != null) {
                        writeYuvToImage(image, yuv, videoWidth, videoHeight)
                        encoder.queueInputBuffer(inIdx, 0, yuvByteSize, ptsUs, 0)
                    } else {
                        val buf = encoder.getInputBuffer(inIdx)
                            ?: throw IllegalStateException("Encoder returned null input buffer and image")
                        val written = writePackedI420(buf, yuv)
                        encoder.queueInputBuffer(inIdx, 0, written, ptsUs, 0)
                    }
                    ptsUs += frameDurationUs
                    drainEncoder(endOfStream = false)
                }
            }

            val eosIdx = dequeueInputOrDrain()
            encoder.queueInputBuffer(eosIdx, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
            drainEncoder(endOfStream = true)
        } finally {
            frameCanvasBitmap.recycle()
            runCatching { encoder.stop() }
            runCatching { encoder.release() }
            if (muxer != null) {
                if (muxerStarted) runCatching { muxer.stop() }
                runCatching { muxer.release() }
            }
        }
    }

    private fun writeYuvToImage(image: Image, yuv: Yuv420Frame, width: Int, height: Int) {
        val planes = image.planes
        writePlane(planes[0].buffer, yuv.y, width, height, planes[0].rowStride, planes[0].pixelStride)
        val uvW = width / 2
        val uvH = height / 2
        writePlane(planes[1].buffer, yuv.u, uvW, uvH, planes[1].rowStride, planes[1].pixelStride)
        writePlane(planes[2].buffer, yuv.v, uvW, uvH, planes[2].rowStride, planes[2].pixelStride)
    }

    private fun writePlane(
        buffer: ByteBuffer,
        src: ByteArray,
        planeWidth: Int,
        planeHeight: Int,
        rowStride: Int,
        pixelStride: Int,
    ) {
        buffer.rewind()
        if (pixelStride == 1 && rowStride == planeWidth) {
            buffer.put(src, 0, planeWidth * planeHeight)
            return
        }
        for (row in 0 until planeHeight) {
            val dstRowStart = row * rowStride
            val srcRowStart = row * planeWidth
            if (pixelStride == 1) {
                buffer.position(dstRowStart)
                buffer.put(src, srcRowStart, planeWidth)
            } else {
                for (col in 0 until planeWidth) {
                    buffer.put(dstRowStart + col * pixelStride, src[srcRowStart + col])
                }
            }
        }
    }

    private fun writePackedI420(buffer: ByteBuffer, yuv: Yuv420Frame): Int {
        buffer.clear()
        buffer.put(yuv.y)
        buffer.put(yuv.u)
        buffer.put(yuv.v)
        return yuv.y.size + yuv.u.size + yuv.v.size
    }
}
