package com.frameflow.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.frameflow.studio.ai.GeminiClient
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.ui.home.AiSettingsSheet
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
@Config(sdk = [35], qualifiers = "w320dp-h640dp-xxhdpi")
class NarrowPhoneUiTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun studioControlsAndSheetsWorkOnNarrow320dpPhoneViewport() {
        val layers = AnimationProject.defaultLayers()
        val anim = LocalAnimationSynthesizer.synthesize(
            prompt = "Bouncing ball",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
            frameCount = 4,
        )
        val project = AnimationProject(
            id = "narrow_studio",
            title = "Very Long Flipbook Title That Must Ellipsize Cleanly",
            fps = 12,
            layers = layers,
            frames = anim.frames,
        )

        var selectedTool: StudioTool? = null
        var addedFrame = false

        composeTestRule.setContent {
            FrameFlowTheme {
                StudioScreenContent(
                    state = StudioUiState(
                        project = project,
                        isLoading = false,
                        currentFrameIndex = 0,
                        activeLayerId = layers[1].id,
                    ),
                    hasCloudKey = false,
                    onSelectTool = { selectedTool = it },
                    onAddBlankFrame = { addedFrame = true },
                )
            }
        }

        // Top actions and bottom timeline header + add button remain accessible at 320dp width
        composeTestRule.onNodeWithTag("studio_ai_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("studio_focus_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("timeline_add_btn").assertIsDisplayed().performClick()
        assertTrue(addedFrame)

        composeTestRule.onNodeWithTag("tool_pen").assertIsDisplayed().performClick()
        assertEquals(StudioTool.PEN, selectedTool)
    }

    @Test
    fun projectsHomeButtonsWorkOnNarrow320dpPhoneViewport() {
        val layers = AnimationProject.defaultLayers()
        val proj = AnimationProject(
            id = "narrow_home",
            title = "Pocket Sketchbook",
            fps = 12,
            layers = layers,
        )

        composeTestRule.setContent {
            FrameFlowTheme {
                ProjectsHomeContent(
                    state = ProjectsHomeUiState(projects = listOf(proj.toSummary())),
                    hasCloudKey = false,
                    animateThumbnails = false,
                )
            }
        }

        composeTestRule.onNodeWithText("FrameFlow").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_ai_create_btn").assertIsDisplayed()
        composeTestRule.onNodeWithTag("home_new_canvas_btn").assertIsDisplayed()
        composeTestRule.onNodeWithText("AI Prompt").assertIsDisplayed()
        composeTestRule.onNodeWithText("New Flipbook").assertIsDisplayed()
    }

    @Test
    fun aiSettingsSheetNormalizesLegacyHydrogenModelOn320dpViewport() {
        var savedKey = ""
        var savedModel = ""

        composeTestRule.setContent {
            FrameFlowTheme {
                AiSettingsSheet(
                    initialApiKey = "AIzaLegacyTest",
                    initialModel = "gemini-4-hydrogen",
                    onDismiss = {},
                    onSave = { key, model ->
                        savedKey = key
                        savedModel = model
                    },
                )
            }
        }

        AiSettingsStore.AVAILABLE_MODELS.forEach { (modelId, label) ->
            composeTestRule.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            composeTestRule.onNodeWithText(modelId).performScrollTo().assertIsDisplayed()
        }
        composeTestRule.onNodeWithText("Save Settings").performScrollTo().performClick()

        assertEquals("AIzaLegacyTest", savedKey)
        assertEquals(GeminiClient.DEFAULT_MODEL, savedModel)
    }

    @Test
    fun aiSettingsSheetSelectsModelOn320dpViewport() {
        var savedKey = ""
        var savedModel = ""

        composeTestRule.setContent {
            FrameFlowTheme {
                AiSettingsSheet(
                    initialApiKey = "AIzaArgonTest",
                    initialModel = "gemini-3.8-flash",
                    onDismiss = {},
                    onSave = { key, model ->
                        savedKey = key
                        savedModel = model
                    },
                )
            }
        }

        composeTestRule.onNodeWithText("gemini-4-argon").performScrollTo().performClick()
        composeTestRule.onNodeWithText("Save Settings").performScrollTo().performClick()

        assertEquals("AIzaArgonTest", savedKey)
        assertEquals("gemini-4-argon", savedModel)
    }
}
