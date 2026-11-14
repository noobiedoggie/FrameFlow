package com.frameflow.studio.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatioPreset(
    val label: String,
    val shortLabel: String,
    val canvasWidth: Float,
    val canvasHeight: Float,
) {
    PORTRAIT_9_16("9:16 Phone", "9:16", 1080f, 1920f),
    SQUARE_1_1("1:1 Square", "1:1", 1080f, 1080f),
    PORTRAIT_4_5("4:5 Portrait", "4:5", 1080f, 1350f),
    LANDSCAPE_16_9("16:9 Cinema", "16:9", 1920f, 1080f);

    val ratio: Float
        get() = canvasWidth / canvasHeight
}

@Serializable
enum class BrushType(val displayName: String) {
    PEN("Pen"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    HIGHLIGHTER("Highlighter"),
}

enum class StudioTool(val displayName: String) {
    PEN("Pen"),
    PENCIL("Pencil"),
    MARKER("Marker"),
    HIGHLIGHTER("Glow"),
    ERASER("Eraser"),
    BUCKET("Fill"),
    SHAPE("Shapes"),
    LASSO("Lasso");

    fun toBrushType(): BrushType? = when (this) {
        PEN -> BrushType.PEN
        PENCIL -> BrushType.PENCIL
        MARKER -> BrushType.MARKER
        HIGHLIGHTER -> BrushType.HIGHLIGHTER
        else -> null
    }
}

@Serializable
enum class ShapeKind(val displayName: String) {
    LINE("Line"),
    RECTANGLE("Rect"),
    OVAL("Circle"),
    TRIANGLE("Triangle"),
}

@Serializable
enum class TweenEasing(val displayName: String) {
    EASE_IN_OUT("Smooth"),
    LINEAR("Linear"),
    BOUNCE("Bounce"),
    ANTICIPATE("Snap"),
}

@Serializable
data class StrokePoint(
    val x: Float,
    val y: Float,
    val pressure: Float = 1f,
)

@Serializable
data class VectorStroke(
    val id: String = newId(),
    val points: List<StrokePoint>,
    val brushType: BrushType = BrushType.PEN,
    val colorArgb: Long = 0xFF151821,
    val baseWidth: Float = 14f,
    val opacity: Float = 1f,
    val isClosed: Boolean = false,
    val fillColorArgb: Long? = null,
)

@Serializable
data class FillPatch(
    val id: String = newId(),
    val colorArgb: Long,
    val maskPngBase64: String,
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

@Serializable
data class LayerContent(
    val strokes: List<VectorStroke> = emptyList(),
    val fills: List<FillPatch> = emptyList(),
)

@Serializable
data class LayerMeta(
    val id: String = newId(),
    val name: String,
    val visible: Boolean = true,
    val opacity: Float = 1f,
    val locked: Boolean = false,
)

@Serializable
data class AnimationFrame(
    val id: String = newId(),
    val holdCount: Int = 1,
    val layers: Map<String, LayerContent> = emptyMap(),
) {
    fun contentFor(layerId: String): LayerContent = layers[layerId] ?: LayerContent()

    fun withLayerContent(layerId: String, content: LayerContent): AnimationFrame =
        copy(layers = layers + (layerId to content))

    fun hasAnyContent(): Boolean =
        layers.values.any { it.strokes.isNotEmpty() || it.fills.isNotEmpty() }
}

@Serializable
data class OnionSkinSettings(
    val enabled: Boolean = true,
    val prevFrames: Int = 1,
    val nextFrames: Int = 1,
    val opacity: Float = 0.28f,
    val pastColorArgb: Long = 0xFFFF5C7A,
    val futureColorArgb: Long = 0xFF2CEAA3,
)

@Serializable
data class GuideSettings(
    val showGrid: Boolean = false,
    val mirrorHorizontal: Boolean = false,
    val autoSnapShapes: Boolean = false,
)

@Serializable
data class AnimationProject(
    val id: String = newId(),
    val title: String,
    val fps: Int = 12,
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.PORTRAIT_9_16,
    val backgroundColorArgb: Long = 0xFFFAF9F6,
    val layers: List<LayerMeta> = defaultLayers(),
    val frames: List<AnimationFrame> = listOf(AnimationFrame()),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    val totalExposureFrames: Int
        get() = frames.sumOf { it.holdCount.coerceAtLeast(1) }.coerceAtLeast(1)

    val durationSeconds: Float
        get() = totalExposureFrames / fps.coerceAtLeast(1).toFloat()

    fun expandedTimelineIndices(): List<Int> {
        val result = ArrayList<Int>(totalExposureFrames)
        frames.forEachIndexed { index, frame ->
            val holds = frame.holdCount.coerceAtLeast(1)
            repeat(holds) { result.add(index) }
        }
        return result
    }

    companion object {
        fun defaultLayers(): List<LayerMeta> = listOf(
            LayerMeta(id = "layer_color", name = "Color", visible = true, opacity = 1f),
            LayerMeta(id = "layer_ink", name = "Ink", visible = true, opacity = 1f),
        )
    }
}

data class ProjectSummary(
    val id: String,
    val title: String,
    val fps: Int,
    val frameCount: Int,
    val totalExposureFrames: Int,
    val aspectRatio: AspectRatioPreset,
    val backgroundColorArgb: Long,
    val previewFrames: List<AnimationFrame>,
    val layers: List<LayerMeta>,
    val updatedAt: Long,
)

fun AnimationProject.toSummary(): ProjectSummary = ProjectSummary(
    id = id,
    title = title,
    fps = fps,
    frameCount = frames.size,
    totalExposureFrames = totalExposureFrames,
    aspectRatio = aspectRatio,
    backgroundColorArgb = backgroundColorArgb,
    previewFrames = frames.take(24),
    layers = layers,
    updatedAt = updatedAt,
)

fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(12)
