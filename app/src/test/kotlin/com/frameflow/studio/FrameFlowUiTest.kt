package com.frameflow.studio

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.StudioTool
import com.frameflow.studio.model.toSummary
import com.frameflow.studio.ui.home.ProjectsHomeContent
import com.frameflow.studio.ui.home.ProjectsHomeUiState
import com.frameflow.studio.ui.studio.StudioScreenContent
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
class FrameFlowUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun projectsHomeDisplaysCardsAndCreatesAiProjectFromSheet() {
        val layers = AnimationProject.defaultLayers()
        val demo = LocalAnimationSynthesizer.synthesize(
            prompt = "Bouncing ball",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
            frameCount = 6,
        )
        val summary = AnimationProject(
            id = "demo_1",
            title = "Squash & Bounce",
            fps = 12,
            layers = layers,
            frames = demo.frames,
        ).toSummary()

        var capturedTitle = ""
        var capturedPrompt = ""
        var capturedFrames = 0

        composeTestRule.setContent {
            FrameFlowTheme {
                ProjectsHomeContent(
                    state = ProjectsHomeUiState(projects = listOf(summary)),
                    hasCloudKey = false,
                    animateThumbnails = false,
                    onCreateProject = { title, _, _, _, prompt, frameCount ->
                        capturedTitle = title
                        capturedPrompt = prompt
                        capturedFrames = frameCount
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("FrameFlow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Squash & Bounce").assertIsDisplayed()

        // Open AI Prompt creator sheet
        composeTestRule.onNodeWithTag("home_ai_create_btn").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithTag("create_title_input").performScrollTo().performTextReplacement("Orbital Hop")
        composeTestRule.onNodeWithText("Rocket launch").performClick()
        composeTestRule.onNodeWithTag("create_confirm_btn").performScrollTo().performClick()

        assertEquals("Orbital Hop", capturedTitle)
        assertEquals("Rocket launch", capturedPrompt)
        assertEquals(12, capturedFrames)
    }

    @Test
    fun studioScreenHandlesToolsTimelineAiSheetAndFocusMode() {
        val layers = AnimationProject.defaultLayers()
        val anim = LocalAnimationSynthesizer.synthesize(
            prompt = "Bouncing ball",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
            frameCount = 4,
        )
        val project = AnimationProject(
            id = "studio_ui_test",
            title = "Bounce Clip",
            fps = 12,
            layers = layers,
            frames = anim.frames,
        )

        var selectedTool: StudioTool? = null
        var addedFrame = false
        var focusToggled = false
        var aiPolishTriggered = false

        composeTestRule.setContent {
            FrameFlowTheme {
                var focusMode by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
                StudioScreenContent(
                    state = StudioUiState(
                        project = project,
                        isLoading = false,
                        currentFrameIndex = 0,
                        activeLayerId = layers[1].id,
                        isFocusMode = focusMode,
                        canUndo = true,
                    ),
                    hasCloudKey = false,
                    onSelectTool = { selectedTool = it },
                    onAddBlankFrame = { addedFrame = true },
                    onToggleFocusMode = {
                        focusMode = !focusMode
                        focusToggled = true
                    },
                    onRunAiPolish = { aiPolishTriggered = true },
                )
            }
        }

        composeTestRule.onNodeWithText("Bounce Clip").assertIsDisplayed()
        composeTestRule.onNodeWithText("Frame 1/${anim.frames.size}").assertIsDisplayed()

        // Switch tool to Shapes
        composeTestRule.onNodeWithTag("tool_shape").performClick()
        assertEquals(StudioTool.SHAPE, selectedTool)

        // Add frame button
        composeTestRule.onNodeWithTag("timeline_add_btn").performClick()
        assertTrue(addedFrame)

        // Open AI sheet -> Polish & Ghost -> trigger Polish
        composeTestRule.onNodeWithTag("studio_ai_btn").performClick()
        composeTestRule.onNodeWithTag("ai_assistant_sheet").assertIsDisplayed()
        composeTestRule.onNodeWithTag("ai_tab_assist").performClick()
        composeTestRule.onNodeWithTag("ai_run_polish_btn").performClick()
        assertTrue(aiPolishTriggered)

        // Focus mode hides deck and reveals exit pill, then restores cleanly
        composeTestRule.onNodeWithTag("studio_focus_btn").performClick()
        assertTrue(focusToggled)
        composeTestRule.onNodeWithTag("exit_focus_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("exit_focus_btn").performClick()
        composeTestRule.onNodeWithTag("timeline_add_btn").assertIsDisplayed()
    }
}
