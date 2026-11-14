package com.frameflow.studio.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateRotation
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.frameflow.studio.engine.FrameRenderer.drawProjectFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.ui.theme.CoralAccent
import com.frameflow.studio.ui.theme.IndigoAccent
import com.frameflow.studio.ui.theme.MintAiAccent
import com.frameflow.studio.ui.theme.MintAiContainer
import com.frameflow.studio.ui.theme.ObsidianBg
import com.frameflow.studio.ui.theme.StudioOutline
import com.frameflow.studio.ui.theme.StudioSurfaceElevated
import com.frameflow.studio.ui.theme.StudioTextPrimary
import com.frameflow.studio.ui.theme.StudioTextSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun StudioCanvas(
    project: AnimationProject,
    currentFrameIndex: Int,
    activeLayerId: String,
    onionSkin: OnionSkinSettings,
    guides: GuideSettings,
    inProgressStroke: VectorStroke?,
    lassoPolygon: List<StrokePoint>,
    selectedStrokeIds: Set<String>,
    aiGhostStrokes: List<VectorStroke>,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    onPointerDown: (Float, Float, Float?) -> Unit,
    onPointerMove: (Float, Float, Float?) -> Unit,
    onPointerUp: () -> Unit,
    onCancelPointer: () -> Unit = {},
    onTapWhilePlaying: () -> Unit = {},
    onTwoFingerTapUndo: () -> Unit,
    onThreeFingerTapRedo: () -> Unit,
    onScaleSelected: (Float) -> Unit,
    onRotateSelected: (Float) -> Unit,
    onDuplicateSelected: () -> Unit,
    onDeleteSelected: () -> Unit,
    onClearSelection: () -> Unit,
    onCommitGhost: () -> Unit,
) {
    var zoom by remember(project.id) { mutableFloatStateOf(1f) }
    var panX by remember(project.id) { mutableFloatStateOf(0f) }
    var panY by remember(project.id) { mutableFloatStateOf(0f) }
    var rotationDeg by remember(project.id) { mutableFloatStateOf(0f) }

    val latestProject by rememberUpdatedState(project)
    val latestOnDown by rememberUpdatedState(onPointerDown)
    val latestOnMove by rememberUpdatedState(onPointerMove)
    val latestOnUp by rememberUpdatedState(onPointerUp)
    val latestOnCancel by rememberUpdatedState(onCancelPointer)
    val latestOnTapWhilePlaying by rememberUpdatedState(onTapWhilePlaying)
    val latestUndo by rememberUpdatedState(onTwoFingerTapUndo)
    val latestRedo by rememberUpdatedState(onThreeFingerTapRedo)
    val latestIsPlaying by rememberUpdatedState(isPlaying)

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .testTag("studio_canvas_viewport"),
        contentAlignment = Alignment.Center,
    ) {
        val viewportW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val viewportH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val padPx = with(density) { 12.dp.toPx() }
        val availW = (viewportW - padPx * 2f).coerceAtLeast(64f)
        val availH = (viewportH - padPx * 2f).coerceAtLeast(64f)

        val ratio = project.aspectRatio.ratio
        val (paperW, paperH) = if (availW / availH > ratio) {
            (availH * ratio) to availH
        } else {
            availW to (availW / ratio)
        }
        val paperLeft = (viewportW - paperW) * 0.5f
        val paperTop = (viewportH - paperH) * 0.5f

        val paperWState by rememberUpdatedState(paperW)
        val paperHState by rememberUpdatedState(paperH)
        val paperLeftState by rememberUpdatedState(paperLeft)
        val paperTopState by rememberUpdatedState(paperTop)
        val viewportWState by rememberUpdatedState(viewportW)
        val viewportHState by rememberUpdatedState(viewportH)

        fun mapScreenToCanvas(sx: Float, sy: Float): Pair<Float, Float> {
            val proj = latestProject
            val cx = viewportWState * 0.5f
            val cy = viewportHState * 0.5f
            val vx = sx - (cx + panX)
            val vy = sy - (cy + panY)
            val rad = (-rotationDeg * PI / 180.0).toFloat()
            val c = cos(rad)
            val s = sin(rad)
            val rx = vx * c - vy * s
            val ry = vx * s + vy * c
            val ux = cx + rx / zoom
            val uy = cy + ry / zoom
            val canvasX = ((ux - paperLeftState) / paperWState) * proj.aspectRatio.canvasWidth
            val canvasY = ((uy - paperTopState) / paperHState) * proj.aspectRatio.canvasHeight
            return canvasX to canvasY
        }

        // Gesture capture layer covering full viewport (overlays sit outside so taps never leak to canvas)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(project.id) {
                    awaitEachGesture {
                        val firstDown = awaitFirstDown(requireUnconsumed = true)
                        val gestureStartMs = System.currentTimeMillis()
                        var maxPointers = 1
                        var drawingStarted = false
                        var enteredMultiTouch = false
                        var singleFingerDistance = 0f
                        var totalMultiTouchDelta = 0f

                        val downX = firstDown.position.x
                        val downY = firstDown.position.y
                        var lastSingleX = downX
                        var lastSingleY = downY
                        val downPressure = firstDown.pressure
                        val (startCx, startCy) = mapScreenToCanvas(downX, downY)

                        while (true) {
                            val event = awaitPointerEvent()
                            val changes = event.changes
                            val pressedCount = changes.count { it.pressed }
                            if (pressedCount > maxPointers) {
                                maxPointers = pressedCount
                            }

                            if (pressedCount >= 2) {
                                if (!enteredMultiTouch && drawingStarted) {
                                    // Cancel brief accidental stroke before pinch/2-finger undo;
                                    // commit only if user had already drawn a clear intentional stroke.
                                    val elapsed = System.currentTimeMillis() - gestureStartMs
                                    if (singleFingerDistance < 36f || elapsed < 220L) {
                                        latestOnCancel()
                                    } else {
                                        latestOnUp()
                                    }
                                    drawingStarted = false
                                }
                                enteredMultiTouch = true
                                val panChange = event.calculatePan()
                                val zoomChange = event.calculateZoom()
                                val rotChange = event.calculateRotation()

                                totalMultiTouchDelta += hypot(panChange.x, panChange.y) +
                                    abs(zoomChange - 1f) * 140f +
                                    abs(rotChange) * 2.5f

                                // Apply pinch zoom/pan/rotation around centroid accurately
                                val oldZoom = zoom
                                val newZoom = (oldZoom * zoomChange).coerceIn(0.45f, 5.5f)
                                val actualScaleRatio = newZoom / oldZoom

                                val oldRot = rotationDeg
                                val candidateRot = (oldRot + rotChange) % 360f
                                val nextRot = if (abs(candidateRot) < 2.5f || abs(abs(candidateRot) - 360f) < 2.5f) {
                                    0f
                                } else {
                                    candidateRot
                                }
                                val actualRotDeltaRad = ((nextRot - oldRot) * PI / 180.0).toFloat()

                                val prevCentroid = event.calculateCentroid(useCurrent = false).let { c ->
                                    if (c == Offset.Unspecified) event.calculateCentroid(useCurrent = true) else c
                                }
                                val cx = viewportWState * 0.5f
                                val cy = viewportHState * 0.5f
                                val anchorX = if (prevCentroid == Offset.Unspecified) cx else prevCentroid.x
                                val anchorY = if (prevCentroid == Offset.Unspecified) cy else prevCentroid.y
                                val relX = anchorX - (cx + panX)
                                val relY = anchorY - (cy + panY)
                                val cosD = cos(actualRotDeltaRad)
                                val sinD = sin(actualRotDeltaRad)
                                val transformedRelX = actualScaleRatio * (relX * cosD - relY * sinD)
                                val transformedRelY = actualScaleRatio * (relX * sinD + relY * cosD)

                                val nextPanX = panX + panChange.x + (relX - transformedRelX)
                                val nextPanY = panY + panChange.y + (relY - transformedRelY)

                                val maxPanX = viewportWState * 0.85f * newZoom
                                val maxPanY = viewportHState * 0.85f * newZoom
                                panX = nextPanX.coerceIn(-maxPanX, maxPanX)
                                panY = nextPanY.coerceIn(-maxPanY, maxPanY)
                                zoom = newZoom
                                rotationDeg = nextRot

                                changes.forEach { it.consume() }
                            } else if (pressedCount == 1 && !enteredMultiTouch && !latestIsPlaying) {
                                val active = changes.firstOrNull { it.pressed }
                                if (active != null) {
                                    val pos = active.position
                                    singleFingerDistance += hypot(pos.x - lastSingleX, pos.y - lastSingleY)
                                    lastSingleX = pos.x
                                    lastSingleY = pos.y
                                    if (!drawingStarted) {
                                        val moved = hypot(pos.x - downX, pos.y - downY)
                                        if (moved > 6f) {
                                            latestOnDown(startCx, startCy, downPressure)
                                            drawingStarted = true
                                            val (mx, my) = mapScreenToCanvas(pos.x, pos.y)
                                            latestOnMove(mx, my, active.pressure)
                                        }
                                    } else if (active.positionChanged()) {
                                        val (mx, my) = mapScreenToCanvas(pos.x, pos.y)
                                        latestOnMove(mx, my, active.pressure)
                                    }
                                    active.consume()
                                }
                            }

                            if (changes.all { !it.pressed }) {
                                if (drawingStarted) {
                                    latestOnUp()
                                } else if (!enteredMultiTouch && maxPointers == 1) {
                                    if (latestIsPlaying) {
                                        if (singleFingerDistance < 18f) {
                                            latestOnTapWhilePlaying()
                                        }
                                    } else {
                                        // Single-finger tap (e.g., dot or Paint Bucket fill)
                                        latestOnDown(startCx, startCy, downPressure)
                                        latestOnUp()
                                    }
                                } else if (enteredMultiTouch && totalMultiTouchDelta < 28f) {
                                    val elapsed = System.currentTimeMillis() - gestureStartMs
                                    if (elapsed < 420L) {
                                        if (maxPointers == 2) {
                                            latestUndo()
                                        } else if (maxPointers >= 3) {
                                            latestRedo()
                                        }
                                    }
                                }
                                break
                            }
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            // Transformed viewport layer matching the linear algebra in mapScreenToCanvas()
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = panX
                        translationY = panY
                        scaleX = zoom
                        scaleY = zoom
                        rotationZ = rotationDeg
                    },
                contentAlignment = Alignment.Center,
            ) {
                val paperDpW = with(density) { paperW.toDp() }
                val paperDpH = with(density) { paperH.toDp() }

                Box(
                    modifier = Modifier
                        .size(paperDpW, paperDpH)
                        .shadow(16.dp, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                        .border(
                            width = 1.dp,
                            color = if (isPlaying) IndigoAccent.copy(alpha = 0.7f) else StudioOutline,
                            shape = RoundedCornerShape(8.dp),
                        ),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawProjectFrame(
                            project = project,
                            frameIndex = currentFrameIndex,
                            onionSkin = onionSkin,
                            guides = guides,
                            inProgressStroke = inProgressStroke,
                            activeLayerId = activeLayerId,
                            aiGhostStrokes = aiGhostStrokes,
                            selectedStrokeIds = selectedStrokeIds,
                            isPlaying = isPlaying,
                        )

                        // Active lasso polygon preview overlay
                        if (lassoPolygon.size >= 2 && !isPlaying) {
                            val sx = size.width / project.aspectRatio.canvasWidth
                            val sy = size.height / project.aspectRatio.canvasHeight
                            val path = Path().apply {
                                moveTo(lassoPolygon.first().x * sx, lassoPolygon.first().y * sy)
                                for (i in 1 until lassoPolygon.size) {
                                    lineTo(lassoPolygon[i].x * sx, lassoPolygon[i].y * sy)
                                }
                            }
                            drawPath(
                                path = path,
                                color = IndigoAccent,
                                style = Stroke(
                                    width = 2.5f,
                                    cap = StrokeCap.Round,
                                    join = StrokeJoin.Round,
                                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f)),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // Floating overlays live OUTSIDE the gesture Box so taps on buttons never leak to the canvas
        if (aiGhostStrokes.isNotEmpty() && !isPlaying) {
            Surface(
                onClick = onCommitGhost,
                shape = RoundedCornerShape(999.dp),
                color = MintAiContainer.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, MintAiAccent),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(
                        top = if (selectedStrokeIds.isNotEmpty()) 56.dp else 10.dp,
                        end = 10.dp,
                    )
                    .testTag("stamp_ghost_btn"),
            ) {
                Text(
                    text = "Stamp Ghost ✦",
                    style = MaterialTheme.typography.labelMedium,
                    color = MintAiAccent,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }

        // Floating Transform Bar for Lasso Selection
        if (selectedStrokeIds.isNotEmpty() && !isPlaying) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = StudioSurfaceElevated.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.65f)),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp)
                    .testTag("lasso_transform_bar"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TransformPill("S-", onClick = { onScaleSelected(0.9f) })
                    TransformPill("S+", onClick = { onScaleSelected(1.1f) })
                    TransformPill("Rot", onClick = { onRotateSelected(15f) })
                    TransformPill("Dup", onClick = onDuplicateSelected)
                    TransformPill("Del", accent = CoralAccent, onClick = onDeleteSelected)
                    TransformPill("Done", accent = IndigoAccent, onClick = onClearSelection)
                }
            }
        }

        // Reset View chip when canvas is zoomed, panned, or rotated
        val isTransformed = abs(zoom - 1f) > 0.03f ||
            abs(panX) > 4f ||
            abs(panY) > 4f ||
            abs(rotationDeg) > 1f
        if (isTransformed) {
            Surface(
                onClick = {
                    zoom = 1f
                    panX = 0f
                    panY = 0f
                    rotationDeg = 0f
                },
                shape = RoundedCornerShape(999.dp),
                color = StudioSurfaceElevated.copy(alpha = 0.92f),
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .testTag("reset_view_chip"),
            ) {
                Text(
                    text = "${(zoom * 100f).roundToInt()}% · Reset",
                    style = MaterialTheme.typography.labelMedium,
                    color = StudioTextPrimary,
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun TransformPill(
    label: String,
    accent: Color = StudioTextPrimary,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = ObsidianBg.copy(alpha = 0.7f),
        border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.7f)),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = accent,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}
