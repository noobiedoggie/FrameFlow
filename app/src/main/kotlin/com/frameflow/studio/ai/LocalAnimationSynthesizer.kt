package com.frameflow.studio.ai

import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.model.newId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class AiGeneratedAnimation(
    val title: String,
    val suggestedFps: Int,
    val frames: List<AnimationFrame>,
    val summary: String,
    val fromCloudGemini: Boolean = false,
)

/**
 * Built-in, zero-latency offline animation synthesizer that turns natural language prompts
 * into clean, editable multi-frame vector animations so AI works immediately out of the box
 * even without a Gemini API key or internet connection.
 */
object LocalAnimationSynthesizer {

    fun synthesize(
        prompt: String,
        aspectRatio: AspectRatioPreset,
        inkLayerId: String,
        colorLayerId: String,
        frameCount: Int = 12,
        referenceFrame: AnimationFrame? = null,
    ): AiGeneratedAnimation {
        val p = prompt.lowercase().trim()
        val cw = aspectRatio.canvasWidth
        val ch = aspectRatio.canvasHeight
        val count = frameCount.coerceIn(6, 24)
        val palette = detectColors(p)

        if (referenceFrame != null && referenceFrame.hasAnyContent()) {
            return animateUserSketch(
                prompt = prompt,
                p = p,
                referenceFrame = referenceFrame,
                cw = cw,
                ch = ch,
                count = count,
            )
        }

        return when {
            p.containsAny("rocket", "spaceship", "launch", "space", "blast") ->
                buildRocketLaunch(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("fire", "flame", "campfire", "candle", "torch", "burn") ->
                buildCampfire(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("heart", "love", "beat", "pulse") ->
                buildBeatingHeart(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("wave", "hello", "hi", "stick", "walk", "run", "jump", "dance", "person", "character") ->
                buildStickCharacter(p, cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("bird", "butterfly", "wing", "fly", "flap") ->
                buildFlyingBird(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("orbit", "planet", "solar", "atom", "moon", "galaxy", "sun") ->
                buildOrbitScene(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("rain", "water", "drop", "ripple", "splash") ->
                buildRainRipples(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("cat", "kitty", "pet", "face", "blink", "wink", "smile") ->
                buildBlinkingCat(cw, ch, inkLayerId, colorLayerId, count, palette)

            p.containsAny("ball", "bounce", "squash", "stretch", "hop") ->
                buildBouncingBall(cw, ch, inkLayerId, colorLayerId, count, palette)

            else ->
                buildAdaptiveMotion(prompt, cw, ch, inkLayerId, colorLayerId, count, palette)
        }
    }

    private fun String.containsAny(vararg keywords: String): Boolean =
        keywords.any { this.contains(it) }

    private data class PromptPalette(
        val primary: Long,
        val secondary: Long,
        val ink: Long = 0xFF1A1E29,
    )

    private fun detectColors(p: String): PromptPalette {
        val primary = when {
            p.contains("red") || p.contains("crimson") -> 0xFFFF4D6D
            p.contains("pink") || p.contains("rose") -> 0xFFFF66B3
            p.contains("blue") || p.contains("ocean") || p.contains("water") -> 0xFF3B82F6
            p.contains("cyan") || p.contains("neon") || p.contains("ice") -> 0xFF00E5FF
            p.contains("green") || p.contains("emerald") || p.contains("frog") -> 0xFF2CEAA3
            p.contains("purple") || p.contains("violet") -> 0xFF9D4EDD
            p.contains("yellow") || p.contains("gold") || p.contains("sun") -> 0xFFFFC300
            p.contains("orange") || p.contains("fire") || p.contains("flame") -> 0xFFFF7A2F
            else -> 0xFF7C8BFF
        }
        val secondary = when {
            primary == 0xFFFF7A2F -> 0xFFFFD166
            primary == 0xFFFF4D6D -> 0xFFFFB703
            else -> 0xFF34E8C7
        }
        return PromptPalette(primary = primary, secondary = secondary)
    }

    private fun buildBouncingBall(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val floorY = ch * 0.78f
        val topY = ch * 0.24f
        val cx = cw * 0.5f
        val baseR = min(cw, ch) * 0.11f

        val frames = (0 until count).map { i ->
            val phase = i.toFloat() / count
            // Symmetric parabolic bounce: 0 = top, 0.5 = impact, 1 = top
            val norm = abs(phase * 2f - 1f) // 1 at ends (top), 0 at 0.5 (impact)
            val heightFrac = 1f - (1f - norm) * (1f - norm) // inverted parabola

            val impactProximity = (1f - (1f - heightFrac) * 4f).coerceIn(0f, 1f)
            val squash = if (heightFrac < 0.16f) {
                val s = 1f - (heightFrac / 0.16f)
                s * 0.34f
            } else {
                0f
            }
            val stretch = if (heightFrac in 0.16f..0.55f) 0.14f else 0f

            val rx = baseR * (1f + squash - stretch * 0.6f)
            val ry = baseR * (1f - squash + stretch)
            val cy = topY + (floorY - ry - topY) * (1f - heightFrac)

            val shadowW = baseR * (0.55f + (1f - heightFrac) * 0.75f)
            val shadowPts = StrokeGeometry.createOvalPoints(cx, floorY + 14f, shadowW, 14f, 24)
            val shadowStroke = VectorStroke(
                points = shadowPts,
                brushType = BrushType.MARKER,
                colorArgb = 0x33000000,
                baseWidth = 4f,
                opacity = 0.28f + (1f - heightFrac) * 0.35f,
                isClosed = true,
                fillColorArgb = 0x44000000,
            )

            val ballStroke = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, cy, rx, ry, 30),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 10f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )

            val highlight = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx - rx * 0.32f, cy - ry * 0.32f, rx * 0.18f, ry * 0.16f, 16),
                brushType = BrushType.MARKER,
                colorArgb = 0xCCFFFFFF,
                baseWidth = 4f,
                isClosed = true,
                fillColorArgb = 0xAAFFFFFF,
            )

            val floorLine = VectorStroke(
                points = listOf(StrokePoint(cw * 0.16f, floorY + 14f), StrokePoint(cw * 0.84f, floorY + 14f)),
                brushType = BrushType.PENCIL,
                colorArgb = 0x552A3042,
                baseWidth = 6f,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(floorLine, shadowStroke)),
                    inkId to LayerContent(strokes = listOf(ballStroke, highlight)),
                )
            )
        }

        return AiGeneratedAnimation(
            title = "Squash & Bounce",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count looping frames with parabolic gravity, ground shadow scaling, and impact squash-and-stretch.",
        )
    }

    private fun buildStickCharacter(
        prompt: String,
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val isWalk = prompt.containsAny("walk", "run", "march")
        val isJump = prompt.containsAny("jump", "hop")
        val unit = min(cw, ch) * 0.11f
        val groundY = ch * 0.78f

        val frames = (0 until count).map { i ->
            val phase = (i.toFloat() / count) * (2f * PI.toFloat())
            val bounceY = when {
                isJump -> -abs(sin(phase * 0.5f)) * unit * 1.9f
                isWalk -> -abs(sin(phase)) * unit * 0.25f
                else -> -sin(phase) * unit * 0.12f
            }
            val cx = if (isWalk) {
                cw * 0.25f + (i.toFloat() / (count - 1).coerceAtLeast(1)) * (cw * 0.5f)
            } else {
                cw * 0.5f
            }

            val hipY = groundY - unit * 1.85f + bounceY
            val shoulderY = hipY - unit * 1.45f
            val headCy = shoulderY - unit * 0.72f
            val headR = unit * 0.52f

            val head = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, headCy, headR, headR, 24),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 11f,
                isClosed = true,
                fillColorArgb = palette.secondary,
            )

            val eyeL = VectorStroke(
                points = listOf(StrokePoint(cx - headR * 0.3f, headCy - headR * 0.1f)),
                brushType = BrushType.MARKER,
                colorArgb = palette.ink,
                baseWidth = 10f,
            )
            val eyeR = VectorStroke(
                points = listOf(StrokePoint(cx + headR * 0.3f, headCy - headR * 0.1f)),
                brushType = BrushType.MARKER,
                colorArgb = palette.ink,
                baseWidth = 10f,
            )

            val torso = VectorStroke(
                points = listOf(
                    StrokePoint(cx, shoulderY),
                    StrokePoint(cx, hipY),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 12f,
            )

            val stride = if (isWalk) sin(phase) * unit * 0.85f else unit * 0.52f
            val liftL = if (isWalk) max(0f, cos(phase)) * unit * 0.35f else 0f
            val liftR = if (isWalk) max(0f, -cos(phase)) * unit * 0.35f else 0f

            val legL = VectorStroke(
                points = listOf(
                    StrokePoint(cx, hipY),
                    StrokePoint(cx - stride * 0.55f, hipY + unit * 0.9f - liftL * 0.5f),
                    StrokePoint(cx - stride, groundY - liftL + (if (isJump) bounceY * 0.7f else 0f)),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 12f,
            )
            val legR = VectorStroke(
                points = listOf(
                    StrokePoint(cx, hipY),
                    StrokePoint(cx + stride * 0.55f, hipY + unit * 0.9f - liftR * 0.5f),
                    StrokePoint(cx + stride, groundY - liftR + (if (isJump) bounceY * 0.7f else 0f)),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 12f,
            )

            val armSwing = if (isWalk) sin(phase) * unit * 0.65f else 0f
            val leftArm = VectorStroke(
                points = listOf(
                    StrokePoint(cx, shoulderY + unit * 0.12f),
                    StrokePoint(cx - unit * 0.65f + armSwing * 0.5f, shoulderY + unit * 0.65f),
                    StrokePoint(cx - unit * 0.75f + armSwing, shoulderY + unit * 1.15f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 11f,
            )

            val waveAngle = if (isWalk) 0f else sin(phase * 2f) * 0.55f
            val elbowX = cx + unit * 0.72f
            val elbowY = if (isWalk) shoulderY + unit * 0.65f else shoulderY - unit * 0.18f
            val handX = if (isWalk) cx + unit * 0.75f - armSwing else elbowX + sin(waveAngle) * unit * 0.55f + unit * 0.25f
            val handY = if (isWalk) shoulderY + unit * 1.15f else elbowY - cos(waveAngle) * unit * 0.75f

            val rightArm = VectorStroke(
                points = listOf(
                    StrokePoint(cx, shoulderY + unit * 0.12f),
                    StrokePoint(elbowX, elbowY),
                    StrokePoint(handX, handY),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 11f,
            )

            val ground = VectorStroke(
                points = listOf(StrokePoint(cw * 0.14f, groundY + 10f), StrokePoint(cw * 0.86f, groundY + 10f)),
                brushType = BrushType.PENCIL,
                colorArgb = 0x552A3042,
                baseWidth = 5f,
            )

            val accentHalo = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, groundY + 10f, unit * 0.9f, 12f, 20),
                brushType = BrushType.MARKER,
                colorArgb = 0x22000000,
                baseWidth = 3f,
                isClosed = true,
                fillColorArgb = 0x33000000,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(ground, accentHalo)),
                    inkId to LayerContent(strokes = listOf(torso, legL, legR, leftArm, rightArm, head, eyeL, eyeR)),
                )
            )
        }

        val title = when {
            isWalk -> "Walk Cycle"
            isJump -> "Happy Jump"
            else -> "Friendly Wave"
        }
        return AiGeneratedAnimation(
            title = title,
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count character frames with articulated joints and smooth cyclic motion.",
        )
    }

    private fun buildRocketLaunch(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val unit = min(cw, ch) * 0.10f
        val cx = cw * 0.5f

        val frames = (0 until count).map { i ->
            val progress = i.toFloat() / (count - 1).coerceAtLeast(1)
            val eased = progress * progress // accelerating liftoff
            val wobble = sin(i * 2.4f) * (1f - progress * 0.6f) * 8f
            val bodyCy = ch * 0.68f - eased * (ch * 0.52f)
            val rx = cx + wobble

            val nose = VectorStroke(
                points = listOf(
                    StrokePoint(rx, bodyCy - unit * 1.8f),
                    StrokePoint(rx + unit * 0.65f, bodyCy - unit * 0.7f),
                    StrokePoint(rx - unit * 0.65f, bodyCy - unit * 0.7f),
                    StrokePoint(rx, bodyCy - unit * 1.8f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 10f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )

            val fuselage = VectorStroke(
                points = listOf(
                    StrokePoint(rx - unit * 0.65f, bodyCy - unit * 0.7f),
                    StrokePoint(rx + unit * 0.65f, bodyCy - unit * 0.7f),
                    StrokePoint(rx + unit * 0.65f, bodyCy + unit * 0.9f),
                    StrokePoint(rx - unit * 0.65f, bodyCy + unit * 0.9f),
                    StrokePoint(rx - unit * 0.65f, bodyCy - unit * 0.7f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 10f,
                isClosed = true,
                fillColorArgb = 0xFFFFFFFF,
            )

            val window = VectorStroke(
                points = StrokeGeometry.createOvalPoints(rx, bodyCy - unit * 0.05f, unit * 0.28f, unit * 0.28f, 20),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = palette.secondary,
            )

            val finL = VectorStroke(
                points = listOf(
                    StrokePoint(rx - unit * 0.65f, bodyCy + unit * 0.2f),
                    StrokePoint(rx - unit * 1.15f, bodyCy + unit * 1.05f),
                    StrokePoint(rx - unit * 0.65f, bodyCy + unit * 0.9f),
                    StrokePoint(rx - unit * 0.65f, bodyCy + unit * 0.2f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 9f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )
            val finR = VectorStroke(
                points = listOf(
                    StrokePoint(rx + unit * 0.65f, bodyCy + unit * 0.2f),
                    StrokePoint(rx + unit * 1.15f, bodyCy + unit * 1.05f),
                    StrokePoint(rx + unit * 0.65f, bodyCy + unit * 0.9f),
                    StrokePoint(rx + unit * 0.65f, bodyCy + unit * 0.2f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 9f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )

            val flameLen = unit * (1.0f + (i % 3) * 0.35f)
            val flame = VectorStroke(
                points = listOf(
                    StrokePoint(rx - unit * 0.38f, bodyCy + unit * 0.92f),
                    StrokePoint(rx, bodyCy + unit * 0.92f + flameLen),
                    StrokePoint(rx + unit * 0.38f, bodyCy + unit * 0.92f),
                    StrokePoint(rx - unit * 0.38f, bodyCy + unit * 0.92f),
                ),
                brushType = BrushType.MARKER,
                colorArgb = 0xFFFF6B35,
                baseWidth = 6f,
                isClosed = true,
                fillColorArgb = 0xFFFFBE0B,
            )

            val puff1R = unit * (0.35f + progress * 0.6f)
            val smoke1 = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx - unit * 0.5f, ch * 0.80f, puff1R, puff1R * 0.7f, 20),
                brushType = BrushType.MARKER,
                colorArgb = 0x5594A3B8,
                baseWidth = 4f,
                opacity = (1f - progress * 0.5f).coerceIn(0.2f, 0.8f),
                isClosed = true,
                fillColorArgb = 0x44CBD5E1,
            )
            val smoke2 = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx + unit * 0.55f, ch * 0.81f, puff1R * 0.85f, puff1R * 0.6f, 20),
                brushType = BrushType.MARKER,
                colorArgb = 0x5594A3B8,
                baseWidth = 4f,
                opacity = (1f - progress * 0.5f).coerceIn(0.2f, 0.8f),
                isClosed = true,
                fillColorArgb = 0x44CBD5E1,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(smoke1, smoke2, flame)),
                    inkId to LayerContent(strokes = listOf(finL, finR, fuselage, nose, window)),
                )
            )
        }

        return AiGeneratedAnimation(
            title = "Rocket Launch",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count liftoff frames with accelerating trajectory, flickering thrust plume, and expanding smoke puffs.",
        )
    }

    private fun buildCampfire(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val cx = cw * 0.5f
        val baseY = ch * 0.70f
        val unit = min(cw, ch) * 0.14f

        val log1 = VectorStroke(
            points = listOf(
                StrokePoint(cx - unit * 1.1f, baseY + unit * 0.35f),
                StrokePoint(cx + unit * 1.1f, baseY - unit * 0.05f),
            ),
            brushType = BrushType.MARKER,
            colorArgb = 0xFF6B4423,
            baseWidth = 28f,
        )
        val log2 = VectorStroke(
            points = listOf(
                StrokePoint(cx - unit * 1.1f, baseY - unit * 0.05f),
                StrokePoint(cx + unit * 1.1f, baseY + unit * 0.35f),
            ),
            brushType = BrushType.MARKER,
            colorArgb = 0xFF533318,
            baseWidth = 28f,
        )

        val frames = (0 until count).map { i ->
            val t = (i.toFloat() / count) * (2f * PI.toFloat())
            val sway = sin(t) * unit * 0.25f
            val sway2 = cos(t * 2f) * unit * 0.16f
            val h = unit * (1.85f + sin(t * 2f) * 0.22f)

            val outerFlame = VectorStroke(
                points = listOf(
                    StrokePoint(cx - unit * 0.72f, baseY),
                    StrokePoint(cx - unit * 0.92f, baseY - h * 0.45f),
                    StrokePoint(cx + sway, baseY - h),
                    StrokePoint(cx + unit * 0.92f, baseY - h * 0.45f),
                    StrokePoint(cx + unit * 0.72f, baseY),
                ),
                brushType = BrushType.PEN,
                colorArgb = 0xFFE85D04,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = 0xFFFF6B35,
            )

            val innerFlame = VectorStroke(
                points = listOf(
                    StrokePoint(cx - unit * 0.38f, baseY),
                    StrokePoint(cx - unit * 0.48f, baseY - h * 0.32f),
                    StrokePoint(cx + sway2, baseY - h * 0.66f),
                    StrokePoint(cx + unit * 0.48f, baseY - h * 0.32f),
                    StrokePoint(cx + unit * 0.38f, baseY),
                ),
                brushType = BrushType.PEN,
                colorArgb = 0xFFFFBA08,
                baseWidth = 5f,
                isClosed = true,
                fillColorArgb = 0xFFFFD166,
            )

            val sparkY = baseY - h * 1.1f - ((i % 4) / 4f) * unit * 0.9f
            val sparkX = cx + sin(t + 1.2f) * unit * 0.45f
            val spark = VectorStroke(
                points = listOf(StrokePoint(sparkX, sparkY)),
                brushType = BrushType.MARKER,
                colorArgb = 0xFFFFE066,
                baseWidth = 14f,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(log1, log2)),
                    inkId to LayerContent(strokes = listOf(outerFlame, innerFlame, spark)),
                )
            )
        }

        return AiGeneratedAnimation(
            title = "Flickering Fire",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count looping campfire frames with layered outer/inner flames and rising ember spark.",
        )
    }

    private fun buildBeatingHeart(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val cx = cw * 0.5f
        val cy = ch * 0.5f
        val baseSize = min(cw, ch) * 0.015f
        val heartColor = if (palette.primary == 0xFF7C8BFF) 0xFFFF3B6B else palette.primary

        val frames = (0 until count).map { i ->
            val p = i.toFloat() / count
            // Lub-dub double heartbeat curve
            val beat = when {
                p < 0.15f -> 1f + sin((p / 0.15f) * PI.toFloat()) * 0.22f
                p in 0.22f..0.38f -> 1f + sin(((p - 0.22f) / 0.16f) * PI.toFloat()) * 0.14f
                else -> 1f
            }
            val pts = ArrayList<StrokePoint>(41)
            for (k in 0..40) {
                val theta = (k.toFloat() / 40) * (2f * PI.toFloat())
                val hx = 16f * sin(theta).pow(3)
                val hy = -(13f * cos(theta) - 5f * cos(2f * theta) - 2f * cos(3f * theta) - cos(4f * theta))
                pts.add(StrokePoint(cx + hx * baseSize * beat, cy + hy * baseSize * beat))
            }

            val heart = VectorStroke(
                points = pts,
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 10f,
                isClosed = true,
                fillColorArgb = heartColor,
            )

            val pulseRadius = min(cw, ch) * (0.20f + p * 0.22f)
            val pulseRing = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, cy, pulseRadius, pulseRadius, 32),
                brushType = BrushType.HIGHLIGHTER,
                colorArgb = heartColor,
                baseWidth = 10f,
                opacity = (1f - p).coerceIn(0.05f, 0.55f),
                isClosed = true,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(pulseRing)),
                    inkId to LayerContent(strokes = listOf(heart)),
                )
            )
        }

        return AiGeneratedAnimation(
            title = "Heart Pulse",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count looping frames with realistic double lub-dub beat and expanding pulse ring.",
        )
    }

    private fun buildFlyingBird(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val unit = min(cw, ch) * 0.13f
        val frames = (0 until count).map { i ->
            val phase = (i.toFloat() / count) * (2f * PI.toFloat())
            val cx = cw * 0.5f
            val cy = ch * 0.48f + sin(phase) * unit * 0.45f
            val wingLift = sin(phase) * unit * 0.9f

            val bird = VectorStroke(
                points = listOf(
                    StrokePoint(cx - unit * 1.4f, cy - wingLift),
                    StrokePoint(cx - unit * 0.55f, cy - wingLift * 0.55f),
                    StrokePoint(cx, cy),
                    StrokePoint(cx + unit * 0.55f, cy - wingLift * 0.55f),
                    StrokePoint(cx + unit * 1.4f, cy - wingLift),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 14f,
            )
            val body = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, cy + unit * 0.1f, unit * 0.34f, unit * 0.22f, 20),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )

            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(body)),
                    inkId to LayerContent(strokes = listOf(bird)),
                )
            )
        }
        return AiGeneratedAnimation(
            title = "Wing Flap",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count looping wing-flap frames with counter-bobbing flight arc.",
        )
    }

    private fun buildOrbitScene(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val cx = cw * 0.5f
        val cy = ch * 0.5f
        val orbitRx = min(cw, ch) * 0.32f
        val orbitRy = min(cw, ch) * 0.16f
        val sunR = min(cw, ch) * 0.10f

        val track = VectorStroke(
            points = StrokeGeometry.createOvalPoints(cx, cy, orbitRx, orbitRy, 36),
            brushType = BrushType.PENCIL,
            colorArgb = 0x557C8BFF,
            baseWidth = 5f,
            isClosed = true,
        )
        val sun = VectorStroke(
            points = StrokeGeometry.createOvalPoints(cx, cy, sunR, sunR, 28),
            brushType = BrushType.PEN,
            colorArgb = palette.ink,
            baseWidth = 9f,
            isClosed = true,
            fillColorArgb = 0xFFFFBE0B,
        )

        val frames = (0 until count).map { i ->
            val angle = (i.toFloat() / count) * (2f * PI.toFloat())
            val px = cx + cos(angle) * orbitRx
            val py = cy + sin(angle) * orbitRy
            val pr = sunR * (0.42f + 0.12f * sin(angle))
            val planet = VectorStroke(
                points = StrokeGeometry.createOvalPoints(px, py, pr, pr, 22),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )
            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(track)),
                    inkId to LayerContent(strokes = listOf(sun, planet)),
                )
            )
        }
        return AiGeneratedAnimation(
            title = "Orbit Loop",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count orbital loop frames with subtle perspective depth scaling.",
        )
    }

    private fun buildRainRipples(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val dropColor = if (palette.primary == 0xFF7C8BFF) 0xFF38BDF8 else palette.primary
        val poolY = ch * 0.72f
        val cx = cw * 0.5f

        val frames = (0 until count).map { i ->
            val p = i.toFloat() / count
            val dropProgress = (p * 1.4f).coerceIn(0f, 1f)
            val dropY = ch * 0.16f + (poolY - ch * 0.16f) * (dropProgress * dropProgress)
            val strokes = mutableListOf<VectorStroke>()

            if (p < 0.7f) {
                strokes.add(
                    VectorStroke(
                        points = listOf(
                            StrokePoint(cx, dropY - 46f),
                            StrokePoint(cx, dropY),
                        ),
                        brushType = BrushType.PEN,
                        colorArgb = dropColor,
                        baseWidth = 14f,
                    )
                )
            }
            val ripplePhase = ((p + 0.3f) % 1f)
            val rx = min(cw, ch) * (0.04f + ripplePhase * 0.28f)
            val ry = rx * 0.34f
            strokes.add(
                VectorStroke(
                    points = StrokeGeometry.createOvalPoints(cx, poolY, rx, ry, 28),
                    brushType = BrushType.PEN,
                    colorArgb = dropColor,
                    baseWidth = 8f,
                    opacity = (1f - ripplePhase).coerceIn(0.15f, 0.95f),
                    isClosed = true,
                )
            )
            AnimationFrame(
                layers = mapOf(
                    inkId to LayerContent(strokes = strokes),
                )
            )
        }
        return AiGeneratedAnimation(
            title = "Rain Ripple",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count accelerating raindrop & concentric water ripple frames.",
        )
    }

    private fun buildBlinkingCat(
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val cx = cw * 0.5f
        val cy = ch * 0.52f
        val r = min(cw, ch) * 0.22f

        val head = VectorStroke(
            points = StrokeGeometry.createOvalPoints(cx, cy, r, r * 0.88f, 30),
            brushType = BrushType.PEN,
            colorArgb = palette.ink,
            baseWidth = 11f,
            isClosed = true,
            fillColorArgb = palette.secondary,
        )
        val earL = VectorStroke(
            points = listOf(
                StrokePoint(cx - r * 0.75f, cy - r * 0.45f),
                StrokePoint(cx - r * 0.85f, cy - r * 1.2f),
                StrokePoint(cx - r * 0.25f, cy - r * 0.8f),
                StrokePoint(cx - r * 0.75f, cy - r * 0.45f),
            ),
            brushType = BrushType.PEN,
            colorArgb = palette.ink,
            baseWidth = 10f,
            isClosed = true,
            fillColorArgb = palette.primary,
        )
        val earR = VectorStroke(
            points = listOf(
                StrokePoint(cx + r * 0.75f, cy - r * 0.45f),
                StrokePoint(cx + r * 0.85f, cy - r * 1.2f),
                StrokePoint(cx + r * 0.25f, cy - r * 0.8f),
                StrokePoint(cx + r * 0.75f, cy - r * 0.45f),
            ),
            brushType = BrushType.PEN,
            colorArgb = palette.ink,
            baseWidth = 10f,
            isClosed = true,
            fillColorArgb = palette.primary,
        )
        val mouth = VectorStroke(
            points = listOf(
                StrokePoint(cx - r * 0.16f, cy + r * 0.22f),
                StrokePoint(cx, cy + r * 0.12f),
                StrokePoint(cx + r * 0.16f, cy + r * 0.22f),
            ),
            brushType = BrushType.PEN,
            colorArgb = palette.ink,
            baseWidth = 8f,
        )

        val frames = (0 until count).map { i ->
            val p = i.toFloat() / count
            val blinkOpen = when {
                p in 0.42f..0.56f -> 0.15f
                p in 0.35f..0.42f || p in 0.56f..0.63f -> 0.55f
                else -> 1f
            }
            val eyeH = max(3f, r * 0.16f * blinkOpen)
            val eyeL = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx - r * 0.36f, cy - r * 0.1f, r * 0.11f, eyeH, 16),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = palette.ink,
            )
            val eyeR = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx + r * 0.36f, cy - r * 0.1f, r * 0.11f, eyeH, 16),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 8f,
                isClosed = true,
                fillColorArgb = palette.ink,
            )
            val tailSway = sin((i.toFloat() / count) * 2f * PI.toFloat()) * r * 0.35f
            val tail = VectorStroke(
                points = listOf(
                    StrokePoint(cx + r * 0.85f, cy + r * 0.65f),
                    StrokePoint(cx + r * 1.35f + tailSway * 0.5f, cy + r * 0.35f),
                    StrokePoint(cx + r * 1.45f + tailSway, cy - r * 0.15f),
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 14f,
            )
            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(earL, earR, head)),
                    inkId to LayerContent(strokes = listOf(tail, eyeL, eyeR, mouth)),
                )
            )
        }

        return AiGeneratedAnimation(
            title = "Blinking Cat",
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count expressive character frames with natural blink timing and tail sway.",
        )
    }

    private fun buildAdaptiveMotion(
        prompt: String,
        cw: Float,
        ch: Float,
        inkId: String,
        colorId: String,
        count: Int,
        palette: PromptPalette,
    ): AiGeneratedAnimation {
        val cx = cw * 0.5f
        val cy = ch * 0.5f
        val radius = min(cw, ch) * 0.18f

        val frames = (0 until count).map { i ->
            val phase = (i.toFloat() / count) * (2f * PI.toFloat())
            val floatY = cy + sin(phase) * radius * 0.45f
            val scale = 1f + cos(phase * 2f) * 0.12f

            val core = VectorStroke(
                points = StrokeGeometry.createOvalPoints(cx, floatY, radius * scale, radius / scale, 28),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 10f,
                isClosed = true,
                fillColorArgb = palette.primary,
            )
            val satellite = VectorStroke(
                points = StrokeGeometry.createOvalPoints(
                    cx + cos(phase) * radius * 1.55f,
                    floatY + sin(phase) * radius * 0.75f,
                    radius * 0.24f,
                    radius * 0.24f,
                    18,
                ),
                brushType = BrushType.PEN,
                colorArgb = palette.ink,
                baseWidth = 7f,
                isClosed = true,
                fillColorArgb = palette.secondary,
            )
            AnimationFrame(
                layers = mapOf(
                    colorId to LayerContent(strokes = listOf(core)),
                    inkId to LayerContent(strokes = listOf(satellite)),
                )
            )
        }
        val cleanTitle = prompt.trim().take(22).ifBlank { "AI Motion Sketch" }
        return AiGeneratedAnimation(
            title = cleanTitle,
            suggestedFps = 12,
            frames = frames,
            summary = "Generated $count editable starter keyframes on-device. Add a Gemini API key in AI Settings for unlimited custom scene synthesis.",
        )
    }

    private fun animateUserSketch(
        prompt: String,
        p: String,
        referenceFrame: AnimationFrame,
        cw: Float,
        ch: Float,
        count: Int,
    ): AiGeneratedAnimation {
        val allStrokes = referenceFrame.layers.values.flatMap { it.strokes }
        val allFills = referenceFrame.layers.values.flatMap { it.fills }
        val strokeBounds = StrokeGeometry.computeBounds(allStrokes)
        val pivotX = strokeBounds?.centerX
            ?: allFills.takeIf { it.isNotEmpty() }?.map { it.left + it.width * 0.5f }?.average()?.toFloat()
            ?: (cw * 0.5f)
        val pivotY = strokeBounds?.centerY
            ?: allFills.takeIf { it.isNotEmpty() }?.map { it.top + it.height * 0.5f }?.average()?.toFloat()
            ?: (ch * 0.5f)

        val motionMode = when {
            p.containsAny("spin", "rotate", "orbit", "twist", "turn") -> "spin"
            p.containsAny("bounce", "jump", "hop", "squash") -> "bounce"
            p.containsAny("pulse", "beat", "breathe", "glow", "grow") -> "pulse"
            p.containsAny("shake", "wiggle", "vibrat", "tremble", "jitter") -> "shake"
            p.containsAny("walk", "run", "slide", "move", "fly", "glide", "dash") -> "glide"
            p.containsAny("fall", "drop", "sink") -> "fall"
            else -> "float"
        }

        val frames = (0 until count).map { i ->
            val t = i.toFloat() / count.toFloat()
            val phase = t * (2f * PI.toFloat())
            var dx = 0f
            var dy = 0f
            var sx = 1f
            var sy = 1f
            var rotRad = 0f

            when (motionMode) {
                "spin" -> {
                    rotRad = phase
                }
                "bounce" -> {
                    val bounceArc = abs(sin(t * PI.toFloat()))
                    dy = (1f - bounceArc) * ch * 0.08f - bounceArc * ch * 0.14f
                    val squash = if (bounceArc < 0.18f) (1f - bounceArc / 0.18f) * 0.14f else 0f
                    sx = 1f + squash
                    sy = 1f - squash * 0.85f
                }
                "pulse" -> {
                    val scale = 1f + sin(phase) * 0.14f
                    sx = scale
                    sy = scale
                }
                "shake" -> {
                    dx = sin(phase * 3f) * cw * 0.025f
                    dy = cos(phase * 4f) * ch * 0.015f
                    rotRad = sin(phase * 3f) * 0.10f
                }
                "glide" -> {
                    dx = sin(phase) * cw * 0.16f
                    dy = -abs(sin(phase * 2f)) * ch * 0.04f
                    rotRad = cos(phase) * 0.06f
                }
                "fall" -> {
                    val eased = (i.toFloat() / max(1, count - 1)).let { it * it }
                    dy = (eased - 0.35f) * ch * 0.24f
                    if (eased > 0.85f) {
                        sx = 1.12f
                        sy = 0.90f
                    }
                }
                else -> {
                    dy = sin(phase) * ch * 0.045f
                    rotRad = cos(phase) * 0.06f
                    sx = 1f + sin(phase * 2f) * 0.03f
                    sy = 1f - sin(phase * 2f) * 0.03f
                }
            }

            val cosR = cos(rotRad)
            val sinR = sin(rotRad)
            val mappedLayers = referenceFrame.layers.mapValues { (_, content) ->
                val transformedStrokes = content.strokes.map { stroke ->
                    stroke.copy(
                        id = newId(),
                        points = stroke.points.map { pt ->
                            val rx = (pt.x - pivotX) * sx
                            val ry = (pt.y - pivotY) * sy
                            val tx = rx * cosR - ry * sinR + pivotX + dx
                            val ty = rx * sinR + ry * cosR + pivotY + dy
                            pt.copy(x = tx, y = ty)
                        },
                    )
                }
                val transformedFills = content.fills.map { patch ->
                    patch.copy(
                        id = newId(),
                        left = patch.left + dx,
                        top = patch.top + dy,
                    )
                }
                LayerContent(strokes = transformedStrokes, fills = transformedFills)
            }
            AnimationFrame(layers = mappedLayers)
        }

        val cleanTitle = prompt.trim().take(22).ifBlank { "Animated Sketch" }
        return AiGeneratedAnimation(
            title = cleanTitle,
            suggestedFps = 12,
            frames = frames,
            summary = "Animated your sketch ($motionMode) across $count editable frames on-device!",
        )
    }

    private fun Float.pow(exp: Int): Float {
        var r = 1f
        repeat(exp) { r *= this }
        return r
    }
}
