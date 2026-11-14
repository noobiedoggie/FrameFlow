package com.frameflow.studio

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.unit.dp
import com.frameflow.studio.ai.GeminiCoachTip
import com.frameflow.studio.export.ExportFormat
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.ui.home.ProjectsHomeContent
import com.frameflow.studio.ui.home.ProjectsHomeUiState
import com.frameflow.studio.ui.studio.AiAssistantSheetContent
import com.frameflow.studio.ui.studio.ExportSheetContent
import com.frameflow.studio.ui.studio.StudioCanvas
import com.frameflow.studio.ui.studio.StudioUiState
import com.frameflow.studio.ui.theme.FrameFlowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h915dp-xxhdpi")
class FrameFlowCanvasUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun studioCanvasOverlayButtonsNeverLeakTapsToCanvas() {
        val layers = AnimationProject.defaultLayers()
        val inkId = layers[1].id
        val stroke = VectorStroke(
            id = "sel_1",
            points = listOf(StrokePoint(300f, 300f), StrokePoint(700f, 700f)),
        )
        val project = AnimationProject(
            id = "overlay_test",
            title = "Overlay Isolation",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            layers = layers,
            frames = listOf(AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(stroke))))),
        )

        var downCount = 0
        var scaleCalls = 0
        var rotateCalls = 0
        var stampCalls = 0

        composeTestRule.setContent {
            FrameFlowTheme {
                Box(modifier = Modifier.size(380.dp, 520.dp)) {
                    StudioCanvas(
                        project = project,
                        currentFrameIndex = 0,
                        activeLayerId = inkId,
                        onionSkin = OnionSkinSettings(enabled = false),
                        guides = GuideSettings(),
                        inProgressStroke = null,
                        lassoPolygon = emptyList(),
                        selectedStrokeIds = setOf("sel_1"),
                        aiGhostStrokes = listOf(stroke.copy(id = "ghost_1")),
                        isPlaying = false,
                        onPointerDown = { _, _, _ -> downCount++ },
                        onPointerMove = { _, _, _ -> },
                        onPointerUp = {},
                        onTwoFingerTapUndo = {},
                        onThreeFingerTapRedo = {},
                        onScaleSelected = { scaleCalls++ },
                        onRotateSelected = { rotateCalls++ },
                        onDuplicateSelected = {},
                        onDeleteSelected = {},
                        onClearSelection = {},
                        onCommitGhost = { stampCalls++ },
                    )
                }
            }
        }

        composeTestRule.onNodeWithTag("lasso_transform_bar").assertIsDisplayed()
        composeTestRule.onNodeWithTag("stamp_ghost_btn").assertIsDisplayed()

        composeTestRule.onNodeWithText("S+").performClick()
        composeTestRule.onNodeWithText("Rot").performClick()
        composeTestRule.onNodeWithTag("stamp_ghost_btn").performClick()

        assertEquals(1, scaleCalls)
        assertEquals(1, rotateCalls)
        assertEquals(1, stampCalls)
        assertEquals("Canvas must not receive pointerDown when tapping floating overlays", 0, downCount)
    }

    @Test
    fun studioCanvasHandlesTwoFingerUndoThreeFingerRedoPinchCancelAndTapPause() {
        val layers = AnimationProject.defaultLayers()
        val project = AnimationProject(
            id = "gesture_test",
            title = "Gestures",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            layers = layers,
            frames = listOf(AnimationFrame()),
        )

        var downCount = 0
        var upCount = 0
        var cancelCount = 0
        var undoCount = 0
        var redoCount = 0
        var tapPlayCount = 0
        var isPlaying by mutableStateOf(false)
        val capturedPoints = mutableListOf<Pair<Float, Float>>()

        composeTestRule.setContent {
            FrameFlowTheme {
                Box(modifier = Modifier.size(380.dp, 520.dp)) {
                    StudioCanvas(
                        project = project,
                        currentFrameIndex = 0,
                        activeLayerId = layers[1].id,
                        onionSkin = OnionSkinSettings(enabled = false),
                        guides = GuideSettings(),
                        inProgressStroke = null,
                        lassoPolygon = emptyList(),
                        selectedStrokeIds = emptySet(),
                        aiGhostStrokes = emptyList(),
                        isPlaying = isPlaying,
                        onPointerDown = { x, y, _ ->
                            downCount++
                            capturedPoints += x to y
                        },
                        onPointerMove = { _, _, _ -> },
                        onPointerUp = { upCount++ },
                        onCancelPointer = { cancelCount++ },
                        onTapWhilePlaying = { tapPlayCount++ },
                        onTwoFingerTapUndo = { undoCount++ },
                        onThreeFingerTapRedo = { redoCount++ },
                        onScaleSelected = {},
                        onRotateSelected = {},
                        onDuplicateSelected = {},
                        onDeleteSelected = {},
                        onClearSelection = {},
                        onCommitGhost = {},
                    )
                }
            }
        }

        // 1. Two-finger quick tap triggers Undo without drawing
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            down(0, Offset(center.x - 40f, center.y))
            down(1, Offset(center.x + 40f, center.y))
            advanceEventTime(60)
            up(0)
            up(1)
        }
        assertEquals(1, undoCount)
        assertEquals(0, downCount)
        assertEquals(0, upCount)

        // 2. Three-finger quick tap triggers Redo without drawing
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            down(0, Offset(center.x - 60f, center.y))
            down(1, Offset(center.x, center.y))
            down(2, Offset(center.x + 60f, center.y))
            advanceEventTime(60)
            up(0)
            up(1)
            up(2)
        }
        assertEquals(1, redoCount)
        assertEquals(0, downCount)

        // 3. Finger-1 slight wobble (>6px) followed quickly by Finger-2 pinch cancels accidental stroke & zooms
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            down(0, Offset(center.x - 20f, center.y))
            advanceEventTime(20)
            moveTo(0, Offset(center.x - 10f, center.y)) // 10px moves past 6px threshold -> drawingStarted=true
            advanceEventTime(20)
            down(1, Offset(center.x + 20f, center.y)) // second finger lands within 40ms -> must cancel stub!
            advanceEventTime(20)
            moveTo(0, Offset(center.x - 120f, center.y))
            moveTo(1, Offset(center.x + 120f, center.y))
            advanceEventTime(40)
            up(0)
            up(1)
        }
        assertEquals("Brief stub stroke before pinch must be canceled", 1, cancelCount)
        assertEquals("Canceled pinch stub must NOT commit pointerUp", 0, upCount)
        composeTestRule.onNodeWithTag("reset_view_chip").assertIsDisplayed()

        // 4. Tap canvas while zoomed -> maps cleanly near center without Reset leak, and Reset chip does not fire downCount
        val downBeforeReset = downCount
        composeTestRule.onNodeWithTag("reset_view_chip").performClick()
        assertEquals("Reset view tap must not draw on canvas", downBeforeReset, downCount)

        // 5. Tapping canvas during playback calls onTapWhilePlaying cleanly
        isPlaying = true
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            down(0, center)
            advanceEventTime(30)
            up(0)
        }
        assertEquals(1, tapPlayCount)
    }

    @Test
    fun studioCanvasMapsTouchUnderPinchAndRotationTransform() {
        val layers = AnimationProject.defaultLayers()
        val project = AnimationProject(
            id = "map_test",
            title = "Transform Mapping",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            layers = layers,
            frames = listOf(AnimationFrame()),
        )

        val tappedPoints = mutableListOf<Pair<Float, Float>>()

        composeTestRule.setContent {
            FrameFlowTheme {
                Box(modifier = Modifier.size(400.dp, 400.dp)) {
                    StudioCanvas(
                        project = project,
                        currentFrameIndex = 0,
                        activeLayerId = layers[1].id,
                        onionSkin = OnionSkinSettings(enabled = false),
                        guides = GuideSettings(),
                        inProgressStroke = null,
                        lassoPolygon = emptyList(),
                        selectedStrokeIds = emptySet(),
                        aiGhostStrokes = emptyList(),
                        isPlaying = false,
                        onPointerDown = { x, y, _ -> tappedPoints += x to y },
                        onPointerMove = { _, _, _ -> },
                        onPointerUp = {},
                        onTwoFingerTapUndo = {},
                        onThreeFingerTapRedo = {},
                        onScaleSelected = {},
                        onRotateSelected = {},
                        onDuplicateSelected = {},
                        onDeleteSelected = {},
                        onClearSelection = {},
                        onCommitGhost = {},
                    )
                }
            }
        }

        // Pinch outward centered at viewport center while rotating 90 degrees clockwise
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            val c = center
            down(0, Offset(c.x - 40f, c.y))
            down(1, Offset(c.x + 40f, c.y))
            advanceEventTime(25)
            // Expand and rotate 90 degrees around center
            moveTo(0, Offset(c.x, c.y - 80f))
            moveTo(1, Offset(c.x, c.y + 80f))
            advanceEventTime(50)
            up(0)
            up(1)
        }
        composeTestRule.onNodeWithTag("reset_view_chip").assertIsDisplayed()

        // Tap viewport center -> should still map cleanly to canvas center (~540, 540)
        composeTestRule.onNodeWithTag("studio_canvas_viewport").performTouchInput {
            down(0, center)
            advanceEventTime(30)
            up(0)
        }
        assertEquals(1, tappedPoints.size)
        val (cx, cy) = tappedPoints.first()
        assertEquals(540f, cx, 15f)
        assertEquals(540f, cy, 15f)
    }

    @Test
    fun exportSheetPluralizesAndAiCoachAppliesRecommendedFps() {
        val layers = AnimationProject.defaultLayers()
        val singleFrameProject = AnimationProject(
            id = "single",
            title = "Single",
            fps = 10,
            layers = layers,
            frames = listOf(AnimationFrame(holdCount = 1)),
        )
        val multiFrameProject = singleFrameProject.copy(
            frames = listOf(AnimationFrame(holdCount = 1), AnimationFrame(holdCount = 2)),
        )

        var currentProj by mutableStateOf(singleFrameProject)

        composeTestRule.setContent {
            FrameFlowTheme {
                ExportSheetContent(
                    project = currentProj,
                    selectedFormat = ExportFormat.GIF,
                    maxDimension = 720,
                    saveToGallery = true,
                    isExporting = false,
                    exportError = null,
                    lastResult = null,
                    onSelectFormat = {},
                    onSelectResolution = {},
                    onToggleSaveToGallery = {},
                    onExportClick = {},
                    onShareClick = {},
                )
            }
        }

        composeTestRule.onNodeWithText("1 drawing · 1 exposure · 10 FPS · 0.1s").assertIsDisplayed()
        currentProj = multiFrameProject
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("2 drawings · 3 exposures · 10 FPS · 0.3s").assertIsDisplayed()
    }

    @Test
    fun aiCoachTipCardAppliesRecommendedFps() {
        val layers = AnimationProject.defaultLayers()
        val proj = AnimationProject(
            id = "coach_fps",
            title = "Coach FPS",
            fps = 12,
            layers = layers,
            frames = listOf(AnimationFrame()),
        )
        var appliedFps = 0

        composeTestRule.setContent {
            FrameFlowTheme {
                AiAssistantSheetContent(
                    state = StudioUiState(
                        project = proj,
                        isLoading = false,
                        coachTip = GeminiCoachTip(
                            title = "Animation Coach",
                            advice = "Try 10 FPS for punchy timing.",
                            recommendedFps = 10,
                        ),
                    ),
                    hasCloudKey = false,
                    onRunTween = { _, _, _ -> },
                    onRunGenerate = { _, _, _, _ -> },
                    onRunPolish = {},
                    onToggleGhost = {},
                    onStampGhost = {},
                    onAskCoach = {},
                    onApplyFps = { appliedFps = it },
                    onOpenAiSettings = {},
                )
            }
        }

        composeTestRule.onNodeWithTag("ai_tab_coach").performClick()
        composeTestRule.onNodeWithTag("ai_apply_fps_btn").performScrollTo().performClick()
        assertEquals(10, appliedFps)
    }

    @Test
    fun createProjectSheetRemainsOpenWhileCreatingAndClosesOnCompletion() {
        var homeState by mutableStateOf(ProjectsHomeUiState())

        composeTestRule.setContent {
            FrameFlowTheme {
                ProjectsHomeContent(
                    state = homeState,
                    hasCloudKey = false,
                    animateThumbnails = false,
                    onCreateProject = { _, _, _, _, _, _ ->
                        homeState = homeState.copy(isCreating = true)
                    },
                )
            }
        }

        composeTestRule.onNodeWithTag("home_ai_create_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_confirm_btn").performScrollTo().performClick()
        composeTestRule.waitForIdle()

        // While isCreating == true, modal sheet must remain open showing disabled busy CTA
        composeTestRule.onNodeWithTag("create_confirm_btn").assertIsDisplayed().assertIsNotEnabled()

        // Once async creation finishes (isCreating == false), sheet closes automatically
        homeState = homeState.copy(isCreating = false)
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_confirm_btn").assertDoesNotExist()
    }
}
