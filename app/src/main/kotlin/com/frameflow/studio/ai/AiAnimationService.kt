package com.frameflow.studio.ai

import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

class AiAnimationService(
    private val getApiKey: () -> String,
    private val getModel: () -> String = { GeminiClient.DEFAULT_MODEL },
) {

    fun hasCloudKey(): Boolean = getApiKey().isNotBlank()

    suspend fun generateFrames(
        prompt: String,
        frameCount: Int,
        aspectRatio: AspectRatioPreset,
        inkLayerId: String,
        colorLayerId: String,
        projectForSketchContext: AnimationProject? = null,
        currentFrameIndex: Int = 0,
    ): AiGeneratedAnimation {
        val refFrame = projectForSketchContext?.frames?.getOrNull(currentFrameIndex)?.takeIf { it.hasAnyContent() }
        val apiKey = getApiKey().trim()
        if (apiKey.isNotEmpty()) {
            val sketchBase64 = if (refFrame != null) {
                withContext(Dispatchers.IO) {
                    GeminiClient.encodeFramePngBase64(projectForSketchContext, currentFrameIndex)
                }
            } else {
                null
            }
            return try {
                GeminiClient.generateAnimation(
                    apiKey = apiKey,
                    prompt = prompt,
                    frameCount = frameCount,
                    aspectRatio = aspectRatio,
                    inkLayerId = inkLayerId,
                    colorLayerId = colorLayerId,
                    model = getModel(),
                    referenceFrameImageBase64 = sketchBase64,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Graceful fallback to on-device AI synthesizer while surfacing why cloud fell back
                val local = LocalAnimationSynthesizer.synthesize(
                    prompt = prompt,
                    aspectRatio = aspectRatio,
                    inkLayerId = inkLayerId,
                    colorLayerId = colorLayerId,
                    frameCount = frameCount,
                    referenceFrame = refFrame,
                )
                local.copy(
                    summary = "Cloud Gemini error (${e.message?.take(70) ?: "offline"}); built editable frames with On-Device AI instead."
                )
            }
        }

        return LocalAnimationSynthesizer.synthesize(
            prompt = prompt,
            aspectRatio = aspectRatio,
            inkLayerId = inkLayerId,
            colorLayerId = colorLayerId,
            frameCount = frameCount,
            referenceFrame = refFrame,
        )
    }

    suspend fun coachProject(
        project: AnimationProject,
        currentFrameIndex: Int,
        question: String,
    ): GeminiCoachTip {
        val apiKey = getApiKey().trim()
        if (apiKey.isNotEmpty()) {
            return try {
                GeminiClient.getCoachingAdvice(
                    apiKey = apiKey,
                    project = project,
                    currentFrameIndex = currentFrameIndex,
                    userQuestion = question,
                    model = getModel(),
                )
            } catch (e: CancellationException) {
                throw e
            } catch (err: Exception) {
                buildLocalCoachTip(project, question, fallbackReason = err.message)
            }
        }
        return buildLocalCoachTip(project, question, fallbackReason = null)
    }

    private fun buildLocalCoachTip(
        project: AnimationProject,
        question: String,
        fallbackReason: String?,
    ): GeminiCoachTip {
        val frames = project.frames.size
        val emptyCount = project.frames.count { !it.hasAnyContent() }
        val advice = when {
            frames == 1 -> "Start by duplicating Frame 1, moving your subject slightly, or drawing Frame 2 and tapping AI In-Between to generate smooth middle frames automatically."
            emptyCount > 0 -> "You have $emptyCount blank frame(s). Turn on Onion Skin + AI Ghost Trajectory to trace smoothly between poses, or use Hold 2x on keyframes for punchier cartoon timing."
            project.fps >= 18 -> "At ${project.fps} FPS you get silky motion! Consider holding key poses for 2x frames to give impacts and anticipation more weight."
            else -> "Great ${frames}-frame loop at ${project.fps} FPS! Try selecting two key poses and running AI In-Between (Bounce or Smooth) to double fluidity without redrawing."
        }
        val prefix = if (fallbackReason != null) "(Offline coach — Gemini unavailable: ${fallbackReason.take(50)}) " else ""
        return GeminiCoachTip(
            title = if (question.isNotBlank()) "AI Timing Note" else "Animation Coach",
            advice = prefix + advice,
            recommendedFps = if (frames <= 6) 10 else 12,
        )
    }
}
