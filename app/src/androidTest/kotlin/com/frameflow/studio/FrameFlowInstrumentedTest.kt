package com.frameflow.studio

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.toSummary
import com.frameflow.studio.ui.home.ProjectsHomeContent
import com.frameflow.studio.ui.home.ProjectsHomeUiState
import com.frameflow.studio.ui.theme.FrameFlowTheme
import org.junit.Rule
import org.junit.Test

class FrameFlowInstrumentedTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeGalleryDisplaysStarterProject() {
        val layers = AnimationProject.defaultLayers()
        val anim = LocalAnimationSynthesizer.synthesize(
            prompt = "Bouncing ball",
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            inkLayerId = layers[1].id,
            colorLayerId = layers[0].id,
            frameCount = 4,
        )
        val summary = AnimationProject(
            id = "test_bounce",
            title = "Bounce Demo",
            fps = 12,
            layers = layers,
            frames = anim.frames,
        ).toSummary()

        composeTestRule.setContent {
            FrameFlowTheme {
                ProjectsHomeContent(
                    state = ProjectsHomeUiState(projects = listOf(summary)),
                    hasCloudKey = false,
                    animateThumbnails = false,
                )
            }
        }

        composeTestRule.onNodeWithText("FrameFlow").assertIsDisplayed()
        composeTestRule.onNodeWithText("Bounce Demo").assertIsDisplayed()
    }
}
