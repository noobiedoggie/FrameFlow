package com.frameflow.studio

import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.ai.GeminiClient
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.ai.MotionPredictor
import com.frameflow.studio.ai.MotionTweenEngine
import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.model.VectorStroke
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionTweenAndAiTest {

    @Test
    fun motionTweenHandlesReverseDrawnStrokesWithoutTanglingAndFadesUnmatched() {
        val layers = AnimationProject.defaultLayers()
        val inkId = layers[1].id

        val startStroke = VectorStroke(
            id = "s_start",
            points = listOf(StrokePoint(100f, 100f), StrokePoint(100f, 300f)),
            colorArgb = 0xFF000000,
            opacity = 1f,
        )
        val unmatchedExtra = VectorStroke(
            id = "extra",
            points = listOf(StrokePoint(700f, 700f), StrokePoint(800f, 700f)),
            opacity = 1f,
        )
        // Drawn bottom-to-top (reverse order!) at x = 500
        val endStroke = VectorStroke(
            id = "s_end",
            points = listOf(StrokePoint(500f, 300f), StrokePoint(500f, 100f)),
            colorArgb = 0xFFFFFFFF,
            opacity = 1f,
        )

        val f1 = AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(startStroke, unmatchedExtra))))
        val f2 = AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(endStroke))))

        val tweens = MotionTweenEngine.generateInBetweens(
            startFrame = f1,
            endFrame = f2,
            layers = layers,
            count = 1,
            easing = TweenEasing.LINEAR,
        )

        assertEquals(1, tweens.size)
        val midStrokes = tweens.first().contentFor(inkId).strokes
        val morphed = midStrokes.first { it.points.first().x < 400f }
        // First point should still be top (~100f), not tangled across vertical midpoint
        assertEquals(300f, morphed.points.first().x, 4f)
        assertEquals(100f, morphed.points.first().y, 4f)
        assertEquals(300f, morphed.points.last().y, 4f)

        // Unmatched stroke fades toward 0.5 opacity at t = 0.5
        val fadedExtra = midStrokes.first { it.points.first().x > 600f }
        assertTrue(fadedExtra.opacity < 0.6f)
    }

    @Test
    fun motionPredictorExtrapolatesVelocityFromTwoPreviousFrames() {
        val layers = AnimationProject.defaultLayers()
        val inkId = layers[1].id

        val f0 = AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(
            VectorStroke(points = listOf(StrokePoint(100f, 200f), StrokePoint(140f, 200f)))
        ))))
        val f1 = AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(
            VectorStroke(points = listOf(StrokePoint(250f, 260f), StrokePoint(290f, 260f)))
        ))))
        val f2 = AnimationFrame()

        val predicted = MotionPredictor.predictNextFrameStrokes(
            frames = listOf(f0, f1, f2),
            targetFrameIndex = 2,
            layerId = inkId,
        )
        assertEquals(1, predicted.size)
        val c = StrokeGeometry.computeCentroid(predicted.first())
        // Previous centroids were 120->270 (+150) and 200->260 (+60), so predicted should be ~(420, 320)
        assertEquals(420f, c.x, 4f)
        assertEquals(320f, c.y, 4f)
    }

    @Test
    fun localSynthesizerProducesValidMultiFrameAnimationsForAllPromptMotifs() {
        val prompts = listOf(
            "Bouncing ball",
            "Rocket launch",
            "Campfire flame",
            "Beating heart",
            "Stick character wave",
            "Flying butterfly",
            "Solar orbit",
            "Rain water ripple",
            "Blinking cat",
            "Abstract cosmic burst",
        )
        val layers = AnimationProject.defaultLayers()
        for (prompt in prompts) {
            val anim = LocalAnimationSynthesizer.synthesize(
                prompt = prompt,
                aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
                inkLayerId = layers[1].id,
                colorLayerId = layers[0].id,
                frameCount = 8,
            )
            assertEquals("Prompt '$prompt' frame count mismatch", 8, anim.frames.size)
            assertTrue("Prompt '$prompt' has empty frames", anim.frames.all { it.hasAnyContent() })
            assertFalse(anim.fromCloudGemini)
        }
    }

    @Test
    fun geminiClientBuildsInteractionsPayloadAndParsesStepsResponse() {
        assertEquals("gemini-3.8-flash", GeminiClient.DEFAULT_MODEL)
        assertEquals("https://generativelanguage.googleapis.com/v1beta/interactions", GeminiClient.INTERACTIONS_ENDPOINT)

        val requestJson = GeminiClient.buildGenerateAnimationRequestBody(
            prompt = "Neon ball bouncing",
            frameCount = 6,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            model = GeminiClient.DEFAULT_MODEL,
            referenceImageBase64 = "AAAA",
        )
        assertTrue(requestJson.contains("\"model\":\"gemini-3.8-flash\""))
        assertTrue(requestJson.contains("\"mime_type\":\"image/png\""))
        assertTrue(requestJson.contains("\"response_format\""))

        val mockResponse = """
            {
              "id": "int_123",
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"title\":\"Neon Hop\",\"fps\":14,\"summary\":\"Clean hop\",\"frames\":[{\"hold\":1,\"strokes\":[{\"colorHex\":\"#34E8C7\",\"fillHex\":\"#7C8BFF\",\"width\":10,\"brush\":\"MARKER\",\"closed\":true,\"points\":[[0.4,0.4],[0.6,0.4],[0.5,0.6]]}]}]}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val extracted = GeminiClient.extractOutputTextFromInteractionResponse(mockResponse)
        val layers = AnimationProject.defaultLayers()
        val parsed = GeminiClient.parseAnimationSpec(
            rawJson = extracted,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
        )

        assertEquals("Neon Hop", parsed.title)
        assertEquals(14, parsed.suggestedFps)
        assertTrue(parsed.fromCloudGemini)
        assertEquals(1, parsed.frames.size)
        val fillStrokes = parsed.frames.first().contentFor(layers[0].id).strokes
        assertEquals(1, fillStrokes.size)
        assertEquals(0xFF7C8BFFL, fillStrokes.first().fillColorArgb)
        assertEquals(0.4f * 1080f, fillStrokes.first().points.first().x, 0.5f)
    }

    @Test
    fun geminiClientExtractsOutputsFallbackAndRejectsEmptyResponses() {
        val legacyOutputsResponse = """
            {
              "id": "int_legacy",
              "outputs": [
                {
                  "type": "thought",
                  "signature": "sig"
                },
                {
                  "type": "text",
                  "text": "{\"title\":\"Legacy Hop\",\"fps\":12,\"summary\":\"\",\"frames\":[{\"hold\":1,\"strokes\":[{\"colorHex\":\"#1A1E29\",\"width\":8,\"points\":[[0.1,0.1],[0.9,0.9]]}]}]}"
                }
              ]
            }
        """.trimIndent()

        val extracted = GeminiClient.extractOutputTextFromInteractionResponse(legacyOutputsResponse)
        val layers = AnimationProject.defaultLayers()
        val parsed = GeminiClient.parseAnimationSpec(
            rawJson = extracted,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
        )
        assertEquals("Legacy Hop", parsed.title)
        assertFalse(parsed.summary.contains("Hydrogen", ignoreCase = true))
        assertTrue(parsed.summary.contains("Gemini"))

        var threwMissing = false
        try {
            GeminiClient.extractOutputTextFromInteractionResponse("""{"id":"int_empty"}""")
        } catch (_: IllegalStateException) {
            threwMissing = true
        }
        assertTrue(threwMissing)

        var threwNoText = false
        try {
            GeminiClient.extractOutputTextFromInteractionResponse("""{"steps":[{"type":"thought","summary":[]}]}""")
        } catch (_: IllegalStateException) {
            threwNoText = true
        }
        assertTrue(threwNoText)
    }

    @Test
    fun geminiClientParsesLeadingWhitespaceFencedJsonAndShortHexColors() {
        val wrapped = """
            Here is your animation:
              ```json
              {
                "title": "Pulse Gem",
                "fps": 12,
                "summary": "Clean pulse",
                "frames": [
                  {
                    "hold": 1,
                    "strokes": [
                      {
                        "colorHex": "#F0A",
                        "fillHex": "0x8034E8C7",
                        "width": 8,
                        "brush": "PEN",
                        "closed": true,
                        "points": [[0.2, 0.2], [0.8, 0.2], [0.5, 0.8]]
                      }
                    ]
                  }
                ]
              }
              ```
        """
        val layers = AnimationProject.defaultLayers()
        val parsed = GeminiClient.parseAnimationSpec(
            rawJson = wrapped,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
        )
        assertEquals("Pulse Gem", parsed.title)
        val stroke = parsed.frames.first().contentFor(layers[1].id).strokes.first()
        assertEquals(0xFFFF00AAL, stroke.colorArgb)
        assertEquals(0x8034E8C7L, stroke.fillColorArgb)
    }

    @Test
    fun localSynthesizerAnimatesUserSketchWhenReferenceFrameProvided() {
        val layers = AnimationProject.defaultLayers()
        val inkId = layers[1].id
        val userStroke = VectorStroke(
            id = "user_gem",
            points = listOf(
                StrokePoint(400f, 400f),
                StrokePoint(600f, 400f),
                StrokePoint(500f, 600f),
            ),
            isClosed = true,
        )
        val refFrame = AnimationFrame(
            layers = mapOf(inkId to LayerContent(strokes = listOf(userStroke))),
        )
        val result = LocalAnimationSynthesizer.synthesize(
            prompt = "Spin and orbit",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            inkLayerId = inkId,
            colorLayerId = layers[0].id,
            frameCount = 6,
            referenceFrame = refFrame,
        )

        assertEquals(6, result.frames.size)
        assertTrue(result.summary.contains("Animated your sketch", ignoreCase = true))
        val f0 = result.frames[0].contentFor(inkId).strokes.first()
        val f2 = result.frames[2].contentFor(inkId).strokes.first()
        assertEquals(3, f0.points.size)
        assertEquals(3, f2.points.size)
        // Rotation around centroid moves points between frame 0 and frame 2
        assertNotEquals(f0.points[0].x, f2.points[0].x, 2f)
    }

    @Test
    fun aiServiceRethrowsCancellationExceptionsWithoutFallback() = runTest {
        val service = AiAnimationService(
            getApiKey = { "non_empty_key" },
            getModel = { throw CancellationException("caller canceled") },
        )
        var generateCanceled = false
        try {
            service.generateFrames(
                prompt = "Bouncing ball",
                frameCount = 6,
                aspectRatio = AspectRatioPreset.SQUARE_1_1,
                inkLayerId = "layer_ink",
                colorLayerId = "layer_color",
            )
        } catch (_: CancellationException) {
            generateCanceled = true
        }
        assertTrue("generateFrames must rethrow CancellationException", generateCanceled)

        var coachCanceled = false
        try {
            service.coachProject(
                project = AnimationProject(id = "p1", title = "T", frames = listOf(AnimationFrame())),
                currentFrameIndex = 0,
                question = "How is spacing?",
            )
        } catch (_: CancellationException) {
            coachCanceled = true
        }
        assertTrue("coachProject must rethrow CancellationException", coachCanceled)
    }

    @Test
    fun geminiClientSkipsBlankStepTextAndSurfacesInteractionErrors() {
        val blankStepWithOutputs = """
            {
              "steps": [
                {
                  "type": "model_output",
                  "content": [{ "type": "text", "text": "   " }]
                }
              ],
              "outputs": [
                { "type": "text", "text": "{\"summary\":\"ok\",\"frames\":[]}" }
              ]
            }
        """.trimIndent()
        assertEquals(
            """{"summary":"ok","frames":[]}""",
            GeminiClient.extractOutputTextFromInteractionResponse(blankStepWithOutputs),
        )

        val failedBlankSteps = """
            {
              "status": "failed",
              "errors": [{ "code": "invalid_argument", "message": "Unknown model id" }],
              "steps": [
                {
                  "type": "model_output",
                  "content": [{ "type": "text", "text": " \n " }]
                }
              ]
            }
        """.trimIndent()
        val blankErr = runCatching {
            GeminiClient.extractOutputTextFromInteractionResponse(failedBlankSteps)
        }.exceptionOrNull()
        assertTrue(blankErr is IllegalStateException)
        assertTrue(blankErr?.message?.contains("Unknown model id") == true)

        val failedNoSteps = """
            {
              "status": "failed",
              "errors": [{ "message": "API key expired" }]
            }
        """.trimIndent()
        val missingErr = runCatching {
            GeminiClient.extractOutputTextFromInteractionResponse(failedNoSteps)
        }.exceptionOrNull()
        assertTrue(missingErr is IllegalStateException)
        assertTrue(missingErr?.message?.contains("API key expired") == true)
    }

    @Test
    fun geminiClientBuildsAndParsesCoachingInteraction() {
        val project = AnimationProject(
            id = "coach_test",
            title = "Jump Arc",
            fps = 12,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            frames = listOf(AnimationFrame()),
        )
        val payload = GeminiClient.buildCoachingRequestBody(
            project = project,
            currentFrameIndex = 0,
            userQuestion = "Check easing on peak",
            model = GeminiClient.DEFAULT_MODEL,
            pngBase64 = "ZmFrZV9wbmc=",
        )
        val root = Json.parseToJsonElement(payload).jsonObject
        assertEquals("gemini-3.8-flash", root["model"]?.jsonPrimitive?.contentOrNull)
        assertEquals("false", root["store"]?.jsonPrimitive?.contentOrNull)
        assertTrue(root["system_instruction"]?.jsonPrimitive?.contentOrNull?.isNotBlank() == true)
        val input = root["input"]!!.jsonArray
        assertEquals(2, input.size)
        assertEquals("text", input[0].jsonObject["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("image", input[1].jsonObject["type"]?.jsonPrimitive?.contentOrNull)
        assertEquals("ZmFrZV9wbmc=", input[1].jsonObject["data"]?.jsonPrimitive?.contentOrNull)
        assertEquals(
            "application/json",
            root["response_format"]?.jsonObject?.get("mime_type")?.jsonPrimitive?.contentOrNull,
        )

        val validResponse = """
            {
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"title\":\"  \",\"advice\":\"Add two ease-in drawings at the apex.\",\"recommendedFps\":99}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val tip = GeminiClient.parseCoachTipResponse(validResponse)
        assertEquals("Animation Coach", tip.title)
        assertEquals("Add two ease-in drawings at the apex.", tip.advice)
        assertEquals(24, tip.recommendedFps)

        val emptyAdviceResponse = """
            {
              "steps": [
                {
                  "type": "model_output",
                  "content": [
                    {
                      "type": "text",
                      "text": "{\"title\":\"Tip\",\"advice\":\"   \",\"recommendedFps\":12}"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()
        val emptyAdviceError = runCatching {
            GeminiClient.parseCoachTipResponse(emptyAdviceResponse)
        }.exceptionOrNull()
        assertTrue(emptyAdviceError is IllegalArgumentException)
    }
}
