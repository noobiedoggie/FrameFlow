package com.frameflow.studio.export

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.frameflow.studio.engine.FrameRenderer
import com.frameflow.studio.model.AnimationProject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

enum class ExportFormat(val label: String, val extension: String, val mimeType: String) {
    GIF("Animated GIF", "gif", "image/gif"),
    MP4("MP4 Video", "mp4", "video/mp4"),
    PNG("Current Frame PNG", "png", "image/png"),
}

data class ExportResult(
    val file: File,
    val contentUri: Uri,
    val mimeType: String,
    val format: ExportFormat,
    val savedToGallery: Boolean,
)

object ExportManager {

    suspend fun exportProject(
        context: Context,
        project: AnimationProject,
        format: ExportFormat,
        currentFrameIndex: Int,
        maxDimension: Int = 720,
        saveToGallery: Boolean = true,
    ): ExportResult = withContext(Dispatchers.IO) {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val safeName = project.title.replace(Regex("[^A-Za-z0-9_-]"), "_").ifBlank { "animation" }
        val timestamp = System.currentTimeMillis()
        val outFile = File(exportDir, "${safeName}_$timestamp.${format.extension}")

        val ratio = project.aspectRatio.ratio
        val (outW, outH) = if (ratio >= 1f) {
            maxDimension to (maxDimension / ratio).roundToInt().coerceAtLeast(128)
        } else {
            (maxDimension * ratio).roundToInt().coerceAtLeast(128) to maxDimension
        }

        when (format) {
            ExportFormat.PNG -> {
                val bmp = FrameRenderer.renderFrameToBitmap(
                    project = project,
                    frameIndex = currentFrameIndex.coerceIn(project.frames.indices),
                    outWidth = outW,
                    outHeight = outH,
                )
                try {
                    FileOutputStream(outFile).use { fos ->
                        bmp.compress(Bitmap.CompressFormat.PNG, 100, fos)
                    }
                } finally {
                    bmp.recycle()
                }
            }

            ExportFormat.GIF -> {
                val gifMax = minOf(maxDimension, 540)
                val (gw, gh) = if (ratio >= 1f) {
                    gifMax to (gifMax / ratio).roundToInt().coerceAtLeast(96)
                } else {
                    (gifMax * ratio).roundToInt().coerceAtLeast(96) to gifMax
                }
                val baseDelayCs = (100f / project.fps.coerceIn(1, 30)).roundToInt().coerceAtLeast(3)
                val rendered = project.frames.indices.map { idx ->
                    val bmp = FrameRenderer.renderFrameToBitmap(project, idx, gw, gh)
                    val hold = project.frames[idx].holdCount.coerceAtLeast(1)
                    bmp to (baseDelayCs * hold)
                }
                try {
                    FileOutputStream(outFile).use { fos ->
                        AnimatedGifEncoder.encodeBitmaps(rendered, fos)
                    }
                } finally {
                    rendered.forEach { it.first.recycle() }
                }
            }

            ExportFormat.MP4 -> {
                val rendered = project.frames.indices.map { idx ->
                    val bmp = FrameRenderer.renderFrameToBitmap(project, idx, outW, outH)
                    bmp to project.frames[idx].holdCount.coerceAtLeast(1)
                }
                try {
                    Mp4VideoExporter.exportBitmapsToMp4(rendered, project.fps, outFile)
                } finally {
                    rendered.forEach { it.first.recycle() }
                }
            }
        }

        val gallerySaved = if (saveToGallery) {
            runCatching { copyToMediaStore(context, outFile, format) }.getOrDefault(false)
        } else {
            false
        }

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            outFile,
        )

        ExportResult(
            file = outFile,
            contentUri = uri,
            mimeType = format.mimeType,
            format = format,
            savedToGallery = gallerySaved,
        )
    }

    private fun copyToMediaStore(context: Context, file: File, format: ExportFormat): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        val resolver = context.contentResolver
        val (collection, relativePath) = when (format) {
            ExportFormat.MP4 -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_MOVIES}/FrameFlow"
            ExportFormat.GIF, ExportFormat.PNG -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI to "${Environment.DIRECTORY_PICTURES}/FrameFlow"
        }
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mimeType)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val itemUri = resolver.insert(collection, values) ?: return false
        return try {
            val os = resolver.openOutputStream(itemUri)
                ?: throw IllegalStateException("Unable to open MediaStore output stream")
            os.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            }
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(itemUri, values, null, null)
            true
        } catch (_: Exception) {
            runCatching { resolver.delete(itemUri, null, null) }
            false
        }
    }

    fun launchShareSheet(context: Context, result: ExportResult) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = result.mimeType
            putExtra(Intent.EXTRA_STREAM, result.contentUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, "Share ${result.format.label}").apply {
            if (context !is android.app.Activity) {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
        runCatching { context.startActivity(chooser) }
    }
}
