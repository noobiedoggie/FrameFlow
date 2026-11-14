package com.frameflow.studio.ai

import android.graphics.Bitmap
import android.util.Base64
import com.frameflow.studio.engine.FrameRenderer
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class GeminiStrokeSpec(
    val colorHex: String = "#1A1E29",
    val fillHex: String? = null,
    val width: Float = 10f,
    val brush: String = "PEN",
    val closed: Boolean = false,
    val points: List<List<Float>> = emptyList(),
)

@Serializable
data class GeminiFrameSpec(
    val hold: Int = 1,
    val strokes: List<GeminiStrokeSpec> = emptyList(),
)

@Serializable
data class GeminiAnimationSpec(
    val title: String = "AI Animation",
    val fps: Int = 12,
    val summary: String = "Generated with Gemini",
    val frames: List<GeminiFrameSpec> = emptyList(),
)

@Serializable
data class GeminiCoachTip(
    val title: String = "Timing & Flow",
    val advice: String = "",
    val recommendedFps: Int = 12,
)

/**
 * REST client for Google Gemini Interactions API (v1beta/interactions) using `gemini-3.8-flash`.
 */
object GeminiClient {

    internal const val INTERACTIONS_ENDPOINT = "https://generativelanguage.googleapis.com/v1beta/interactions"
    const val DEFAULT_MODEL = "gemini-3.8-flash"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun buildGenerateAnimationRequestBody(
        prompt: String,
        frameCount: Int,
        aspectRatio: AspectRatioPreset,
        model: String = DEFAULT_MODEL,
        referenceImageBase64: String? = null,
    ): String {
        val systemInstruction = """
            You are FrameFlow AI, a master 2D frame-by-frame animator.
            Generate clean, expressive, recognizable 2D vector keyframe animations as JSON.
            Coordinates MUST be normalized in [0.0, 1.0] where (0,0) is top-left and (1,1) is bottom-right.
            Use multiple clear strokes per frame (body, limbs, details, motion arcs, closed filled shapes where helpful).
            Maintain smooth frame-to-frame motion continuity across all $frameCount frames on a ${aspectRatio.shortLabel} canvas.
        """.trimIndent()

        val userText = buildString {
            append("Create a $frameCount-frame animation for: \"$prompt\". ")
            if (referenceImageBase64 != null) {
                append("Continue/animate starting from the attached user sketch on the canvas. ")
            }
            append("Return clean JSON matching the requested schema.")
        }

        val inputJson = if (referenceImageBase64 != null) {
            buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", userText)
                })
                add(buildJsonObject {
                    put("type", "image")
                    put("data", referenceImageBase64)
                    put("mime_type", "image/png")
                })
            }
        } else {
            JsonPrimitive(userText)
        }

        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("fps") { put("type", "integer") }
                putJsonObject("summary") { put("type", "string") }
                putJsonObject("frames") {
                    put("type", "array")
                    putJsonObject("items") {
                        put("type", "object")
                        putJsonObject("properties") {
                            putJsonObject("hold") { put("type", "integer") }
                            putJsonObject("strokes") {
                                put("type", "array")
                                putJsonObject("items") {
                                    put("type", "object")
                                    putJsonObject("properties") {
                                        putJsonObject("colorHex") { put("type", "string") }
                                        putJsonObject("fillHex") { put("type", "string") }
                                        putJsonObject("width") { put("type", "number") }
                                        putJsonObject("brush") {
                                            put("type", "string")
                                            put("enum", buildJsonArray {
                                                add(JsonPrimitive("PEN"))
                                                add(JsonPrimitive("PENCIL"))
                                                add(JsonPrimitive("MARKER"))
                                                add(JsonPrimitive("HIGHLIGHTER"))
                                            })
                                        }
                                        putJsonObject("closed") { put("type", "boolean") }
                                        putJsonObject("points") {
                                            put("type", "array")
                                            putJsonObject("items") {
                                                put("type", "array")
                                                putJsonObject("items") { put("type", "number") }
                                            }
                                        }
                                    }
                                    put("required", buildJsonArray {
                                        add(JsonPrimitive("colorHex"))
                                        add(JsonPrimitive("width"))
                                        add(JsonPrimitive("points"))
                                    })
                                }
                            }
                        }
                        put("required", buildJsonArray {
                            add(JsonPrimitive("strokes"))
                        })
                    }
                }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("fps"))
                add(JsonPrimitive("summary"))
                add(JsonPrimitive("frames"))
            })
        }

        val payload = buildJsonObject {
            put("model", model)
            put("system_instruction", systemInstruction)
            put("input", inputJson)
            put("store", false)
            putJsonObject("response_format") {
                put("type", "text")
                put("mime_type", "application/json")
                put("schema", schema)
            }
        }
        return payload.toString()
    }

    fun extractOutputTextFromInteractionResponse(responseJson: String): String {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val steps = root["steps"] as? JsonArray
        val outputs = root["outputs"] as? JsonArray
        val errorDetail = extractInteractionError(root)
        if (steps == null && outputs == null) {
            throw IllegalStateException(
                errorDetail?.let { "Gemini interaction failed: $it" }
                    ?: "Missing 'steps' in Gemini Interactions response."
            )
        }

        val textParts = mutableListOf<String>()
        if (steps != null) {
            for (step in steps) {
                val stepObj = step as? JsonObject ?: continue
                if (stepObj["type"]?.jsonPrimitive?.contentOrNull == "model_output") {
                    val content = stepObj["content"] as? JsonArray ?: continue
                    for (item in content) {
                        val obj = item as? JsonObject ?: continue
                        if (obj["type"]?.jsonPrimitive?.contentOrNull == "text") {
                            obj["text"]?.jsonPrimitive?.contentOrNull
                                ?.takeIf { it.isNotBlank() }
                                ?.let { textParts.add(it) }
                        }
                    }
                }
            }
        }
        if (textParts.isEmpty() && outputs != null) {
            for (item in outputs) {
                val obj = item as? JsonObject ?: continue
                val itemType = obj["type"]?.jsonPrimitive?.contentOrNull
                if (itemType == null || itemType == "text") {
                    obj["text"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                        ?.let { textParts.add(it) }
                }
            }
        }
        if (textParts.isEmpty()) {
            throw IllegalStateException(
                errorDetail?.let { "No text output returned ($it)" }
                    ?: "No text output returned in Gemini model_output steps."
            )
        }
        return textParts.joinToString("\n").trim()
    }

    private fun extractInteractionError(root: JsonObject): String? {
        val errorMessages = mutableListOf<String>()
        val errorsArray = root["errors"] as? JsonArray
        if (errorsArray != null) {
            for (entry in errorsArray) {
                when (entry) {
                    is JsonObject -> {
                        val message = entry["message"]?.jsonPrimitive?.contentOrNull
                        val code = entry["code"]?.jsonPrimitive?.contentOrNull
                        val formatted = listOfNotNull(code?.takeIf { it.isNotBlank() }, message?.takeIf { it.isNotBlank() })
                            .joinToString(": ")
                        if (formatted.isNotBlank()) errorMessages.add(formatted)
                    }
                    else -> {
                        entry.jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() }?.let { errorMessages.add(it) }
                    }
                }
            }
        }
        val singleError = root["error"]
        if (singleError is JsonObject) {
            singleError["message"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let { errorMessages.add(it) }
        }
        if (errorMessages.isNotEmpty()) {
            return errorMessages.joinToString("; ")
        }
        val status = root["status"]?.jsonPrimitive?.contentOrNull?.takeIf {
            it.equals("failed", ignoreCase = true) || it.equals("cancelled", ignoreCase = true)
        }
        return status?.let { "status=$it" }
    }

    private fun extractJsonObject(raw: String): String {
        var s = raw.trim()
        if (s.startsWith("```")) {
            s = s.removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .trim()
        }
        if (s.endsWith("```")) {
            s = s.removeSuffix("```").trim()
        }
        val firstBrace = s.indexOf('{')
        val lastBrace = s.lastIndexOf('}')
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            return s.substring(firstBrace, lastBrace + 1).trim()
        }
        return s
    }

    fun parseAnimationSpec(
        rawJson: String,
        aspectRatio: AspectRatioPreset,
        inkLayerId: String,
        colorLayerId: String,
    ): AiGeneratedAnimation {
        val cleaned = extractJsonObject(rawJson)
        val spec = json.decodeFromString<GeminiAnimationSpec>(cleaned)
        val cw = aspectRatio.canvasWidth
        val ch = aspectRatio.canvasHeight

        val frames = spec.frames.map { frameSpec ->
            val inkStrokes = mutableListOf<VectorStroke>()
            val fillStrokes = mutableListOf<VectorStroke>()
            for (s in frameSpec.strokes) {
                val pts = s.points.mapNotNull { pair ->
                    if (pair.size >= 2) {
                        StrokePoint(
                            x = (pair[0].coerceIn(0f, 1f)) * cw,
                            y = (pair[1].coerceIn(0f, 1f)) * ch,
                            pressure = if (pair.size >= 3) pair[2].coerceIn(0.3f, 1f) else 0.85f,
                        )
                    } else null
                }
                if (pts.isEmpty()) continue
                val stroke = VectorStroke(
                    points = pts,
                    brushType = runCatching { BrushType.valueOf(s.brush.uppercase()) }.getOrDefault(BrushType.PEN),
                    colorArgb = parseHexColor(s.colorHex, 0xFF1A1E29),
                    baseWidth = s.width.coerceIn(3f, 48f),
                    isClosed = s.closed,
                    fillColorArgb = s.fillHex?.takeIf { it.isNotBlank() }?.let { parseHexColor(it, 0xFF7C8BFF) },
                )
                if (stroke.fillColorArgb != null && stroke.brushType == BrushType.MARKER) {
                    fillStrokes.add(stroke)
                } else {
                    inkStrokes.add(stroke)
                }
            }
            val map = LinkedHashMap<String, LayerContent>()
            if (fillStrokes.isNotEmpty()) map[colorLayerId] = LayerContent(strokes = fillStrokes)
            if (inkStrokes.isNotEmpty()) map[inkLayerId] = LayerContent(strokes = inkStrokes)
            AnimationFrame(
                holdCount = frameSpec.hold.coerceIn(1, 8),
                layers = map,
            )
        }

        require(frames.isNotEmpty()) { "Gemini returned zero frames." }
        return AiGeneratedAnimation(
            title = spec.title.ifBlank { "Gemini Animation" },
            suggestedFps = spec.fps.coerceIn(4, 24),
            frames = frames,
            summary = spec.summary.ifBlank { "Generated ${frames.size} frames with Gemini." },
            fromCloudGemini = true,
        )
    }

    fun parseHexColor(hex: String, fallback: Long): Long {
        val clean = hex.trim()
            .removePrefix("#")
            .removePrefix("0x")
            .removePrefix("0X")
        return try {
            when (clean.length) {
                3 -> {
                    val r = clean[0].toString().repeat(2)
                    val g = clean[1].toString().repeat(2)
                    val b = clean[2].toString().repeat(2)
                    (0xFF000000L or "$r$g$b".toLong(16))
                }
                4 -> {
                    val a = clean[0].toString().repeat(2)
                    val r = clean[1].toString().repeat(2)
                    val g = clean[2].toString().repeat(2)
                    val b = clean[3].toString().repeat(2)
                    "$a$r$g$b".toLong(16)
                }
                6 -> (0xFF000000L or clean.toLong(16))
                8 -> clean.toLong(16)
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    suspend fun generateAnimation(
        apiKey: String,
        prompt: String,
        frameCount: Int,
        aspectRatio: AspectRatioPreset,
        inkLayerId: String,
        colorLayerId: String,
        model: String = DEFAULT_MODEL,
        referenceFrameImageBase64: String? = null,
    ): AiGeneratedAnimation = withContext(Dispatchers.IO) {
        val requestBody = buildGenerateAnimationRequestBody(
            prompt = prompt,
            frameCount = frameCount,
            aspectRatio = aspectRatio,
            model = model,
            referenceImageBase64 = referenceFrameImageBase64,
        )
        val rawResponse = postInteraction(apiKey, requestBody)
        val text = extractOutputTextFromInteractionResponse(rawResponse)
        parseAnimationSpec(text, aspectRatio, inkLayerId, colorLayerId)
    }

    suspend fun getCoachingAdvice(
        apiKey: String,
        project: AnimationProject,
        currentFrameIndex: Int,
        userQuestion: String,
        model: String = DEFAULT_MODEL,
    ): GeminiCoachTip = withContext(Dispatchers.IO) {
        val pngBase64 = encodeFramePngBase64(project, currentFrameIndex)
        val payload = buildCoachingRequestBody(
            project = project,
            currentFrameIndex = currentFrameIndex,
            userQuestion = userQuestion,
            model = model,
            pngBase64 = pngBase64,
        )
        val rawResponse = postInteraction(apiKey, payload)
        parseCoachTipResponse(rawResponse)
    }

    internal fun buildCoachingRequestBody(
        project: AnimationProject,
        currentFrameIndex: Int,
        userQuestion: String,
        model: String = DEFAULT_MODEL,
        pngBase64: String = encodeFramePngBase64(project, currentFrameIndex),
    ): String {
        val clampedFrameIndex = currentFrameIndex.coerceIn(0, (project.frames.size - 1).coerceAtLeast(0))
        val schema = buildJsonObject {
            put("type", "object")
            putJsonObject("properties") {
                putJsonObject("title") { put("type", "string") }
                putJsonObject("advice") { put("type", "string") }
                putJsonObject("recommendedFps") { put("type", "integer") }
            }
            put("required", buildJsonArray {
                add(JsonPrimitive("title"))
                add(JsonPrimitive("advice"))
                add(JsonPrimitive("recommendedFps"))
            })
        }
        val promptText = """
            Project: "${project.title}", ${project.frames.size} frames, ${project.fps} FPS, ${project.aspectRatio.shortLabel}.
            Current frame: #${clampedFrameIndex + 1}.
            User note/question: "${userQuestion.ifBlank { "Give concise animation timing & drawing tips to make this look smoother and more alive." }}".
            Give practical, phone-friendly animation coaching in 2-3 sentences.
        """.trimIndent()

        return buildJsonObject {
            put("model", model.ifBlank { DEFAULT_MODEL })
            put("system_instruction", "You are an encouraging 2D animation director coaching an animator using a mobile flipbook app.")
            put("store", false)
            put("input", buildJsonArray {
                add(buildJsonObject {
                    put("type", "text")
                    put("text", promptText)
                })
                add(buildJsonObject {
                    put("type", "image")
                    put("data", pngBase64)
                    put("mime_type", "image/png")
                })
            })
            putJsonObject("response_format") {
                put("type", "text")
                put("mime_type", "application/json")
                put("schema", schema)
            }
        }.toString()
    }

    internal fun parseCoachTipResponse(rawResponse: String): GeminiCoachTip {
        val text = extractOutputTextFromInteractionResponse(rawResponse)
        val parsed = json.decodeFromString<GeminiCoachTip>(extractJsonObject(text))
        val cleanAdvice = parsed.advice.trim()
        require(cleanAdvice.isNotEmpty()) { "Gemini coaching response returned empty advice." }
        return parsed.copy(
            title = parsed.title.trim().ifEmpty { "Animation Coach" },
            advice = cleanAdvice,
            recommendedFps = parsed.recommendedFps.coerceIn(4, 24),
        )
    }

    fun encodeFramePngBase64(project: AnimationProject, frameIndex: Int): String {
        val bmp = FrameRenderer.renderFrameToBitmap(project, frameIndex, 384, (384 / project.aspectRatio.ratio).toInt().coerceAtLeast(128))
        val bytes = ByteArrayOutputStream().use { out ->
            bmp.compress(Bitmap.CompressFormat.PNG, 90, out)
            out.toByteArray()
        }
        bmp.recycle()
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private fun postInteraction(apiKey: String, jsonBody: String): String {
        val conn = (URL(INTERACTIONS_ENDPOINT).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 20_000
            readTimeout = 45_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            setRequestProperty("Api-Revision", "2026-05-20")
            setRequestProperty("x-goog-api-key", apiKey.trim())
        }
        try {
            conn.outputStream.use { os ->
                os.write(jsonBody.toByteArray(Charsets.UTF_8))
            }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                throw IllegalStateException("Gemini API HTTP $code: ${body.take(280)}")
            }
            return body
        } finally {
            conn.disconnect()
        }
    }
}
