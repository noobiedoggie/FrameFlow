package com.frameflow.studio

import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.data.FileProjectRepository
import com.frameflow.studio.engine.FloodFillEngine
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.FillPatch
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StudioTool
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.ui.studio.FloodFillExecutor
import com.frameflow.studio.ui.studio.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

@OptIn(ExperimentalCoroutinesApi::class)
class ProjectRepositoryAndViewModelTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repositorySeedsStarterProjectsAndRoundTripsCrud() = runTest {
        val repo = FileProjectRepository(tempFolder.newFolder("projects"))
        repo.refresh()

        val initialSummaries = repo.summaries.first()
        assertEquals(2, initialSummaries.size)
        assertTrue(initialSummaries.any { it.title == "Squash & Bounce" })

        val custom = AnimationProject(
            id = "custom_1",
            title = "My Test Flip",
            fps = 16,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            frames = listOf(AnimationFrame(), AnimationFrame()),
        )
        repo.saveProject(custom)

        val loaded = repo.loadProject("custom_1")
        assertNotNull(loaded)
        assertEquals("My Test Flip", loaded!!.title)
        assertEquals(16, loaded.fps)
        assertEquals(2, loaded.frames.size)

        val copy = repo.duplicateProject("custom_1")
        assertNotNull(copy)
        assertTrue(copy!!.title.endsWith("Copy"))
        assertEquals(4, repo.summaries.first().size)

        repo.deleteProject("custom_1")
        assertNull(repo.loadProject("custom_1"))
    }

    @Test
    fun studioViewModelHandlesDrawingUndoRedoFramesAiAndLayers() = runTest {
        val repo = FileProjectRepository(tempFolder.newFolder("vm_projects"))
        val blankProject = AnimationProject(
            id = "vm_proj",
            title = "Studio Test",
            fps = 12,
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        )
        repo.saveProject(blankProject)

        val fakeFillExecutor = FloodFillExecutor { _, _, _, _, _, color ->
            FillPatch(colorArgb = color, maskPngBase64 = "AA==", left = 0f, top = 0f, width = 20f, height = 20f)
        }
        val vmStore = androidx.lifecycle.ViewModelStore()
        val rawVm = StudioViewModel(
            projectId = "vm_proj",
            repository = repo,
            aiService = AiAnimationService(getApiKey = { "" }),
            floodFillExecutor = fakeFillExecutor,
        )
        val vm = androidx.lifecycle.ViewModelProvider(
            vmStore,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = rawVm as T
            },
        )[StudioViewModel::class.java]
        try {
            // Wait for Dispatchers.IO init load to complete
            withContext(Dispatchers.Default) {
                var attempts = 0
                while (vm.uiState.value.isLoading && attempts < 100) {
                    kotlinx.coroutines.delay(20)
                    attempts++
                }
            }
            assertFalse(vm.uiState.value.isLoading)
            assertEquals(1, vm.uiState.value.project!!.frames.size)

            // Draw a stroke on Frame 1
            vm.onCanvasPointerDown(100f, 100f, 0.8f)
            vm.onCanvasPointerMove(300f, 300f, 0.9f)
            vm.onCanvasPointerMove(500f, 100f, 0.7f)
            vm.onCanvasPointerUp()

            val activeLayer = vm.uiState.value.activeLayerId
            assertEquals(1, vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size)
            assertTrue(vm.uiState.value.canUndo)

            // Undo & Redo
            vm.undo()
            assertEquals(0, vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size)
            assertTrue(vm.uiState.value.canRedo)

            vm.redo()
            assertEquals(1, vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size)

            // Flood fill via Bucket tool
            vm.selectTool(StudioTool.BUCKET)
            vm.onCanvasPointerDown(200f, 200f, null)
            vm.onCanvasPointerUp()
            assertEquals(1, vm.uiState.value.currentFrame!!.contentFor(activeLayer).fills.size)

            // Duplicate frame & cycle hold count
            vm.duplicateCurrentFrame()
            assertEquals(2, vm.uiState.value.project!!.frames.size)
            assertEquals(1, vm.uiState.value.currentFrameIndex)
            vm.cycleFrameHold(1)
            assertEquals(2, vm.uiState.value.currentFrame!!.holdCount)

            // AI In-Between between frame 0 and frame 1
            vm.selectFrame(0)
            vm.runAiInBetween(count = 2, easing = TweenEasing.BOUNCE, towardNext = true)
            assertEquals(4, vm.uiState.value.project!!.frames.size)

            // AI Polish on current frame
            vm.polishCurrentFrame(snapShapes = true)
            assertEquals(1, vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size)

            // Ghost trajectory predict + stamp onto blank frame
            vm.selectFrame(3)
            vm.addBlankFrameAfterCurrent()
            vm.toggleAiGhost()
            assertTrue(vm.uiState.value.showAiGhost)
            assertTrue(vm.uiState.value.aiGhostStrokes.isNotEmpty())
            vm.commitAiGhost()
            assertTrue(vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.isNotEmpty())

            // Layer locking blocks drawing cleanly
            vm.toggleLayerLock(activeLayer)
            val countBeforeLocked = vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size
            vm.selectTool(StudioTool.PEN)
            vm.onCanvasPointerDown(50f, 50f, 1f)
            vm.onCanvasPointerMove(120f, 120f, 1f)
            vm.onCanvasPointerUp()
            assertEquals(countBeforeLocked, vm.uiState.value.currentFrame!!.contentFor(activeLayer).strokes.size)

            // AI Generate replaces/builds editable frames
            vm.toggleLayerLock(activeLayer)
            vm.runAiGenerate(
                prompt = "Rocket launch",
                frameCount = 7,
                replaceBlankProject = true,
                useCurrentFrameAsSketch = false,
            )
            // Because frames are not blank, replaceBlankProject only replaces when project is single blank frame;
            // inserting after current frame adds +7 frames -> 5 + 7 = 12 frames!
            assertEquals(12, vm.uiState.value.project!!.frames.size)
        } finally {
            vmStore.clear()
        }
    }

    @Test
    fun studioViewModelUndoAfterAddLayerSelectsValidLayerAndPersistScopeSurvives() = runTest {
        val repo = FileProjectRepository(tempFolder.newFolder("persist_projects"))
        val project = AnimationProject(
            id = "p_layer_undo",
            title = "Layer Undo Test",
            fps = 12,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
        )
        repo.saveProject(project)

        val externalJob = kotlinx.coroutines.SupervisorJob()
        val externalScope = kotlinx.coroutines.CoroutineScope(externalJob + Dispatchers.IO)
        val vmStore = androidx.lifecycle.ViewModelStore()
        val rawVm = StudioViewModel(
            projectId = "p_layer_undo",
            repository = repo,
            aiService = AiAnimationService(getApiKey = { "" }),
            persistScope = externalScope,
        )
        val vm = androidx.lifecycle.ViewModelProvider(
            vmStore,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = rawVm as T
            },
        )[StudioViewModel::class.java]
        try {
            withContext(Dispatchers.Default) {
                var tries = 0
                while (vm.uiState.value.isLoading && tries < 100) {
                    kotlinx.coroutines.delay(15)
                    tries++
                }
            }
            assertFalse(vm.uiState.value.isLoading)

            // Add a new layer (auto-selected), draw on it, then undo back before addLayer
            vm.addLayer("FX")
            val addedLayerId = vm.uiState.value.activeLayerId
            assertTrue(vm.uiState.value.project!!.layers.any { it.id == addedLayerId })

            vm.onCanvasPointerDown(200f, 200f, 1f)
            vm.onCanvasPointerMove(400f, 400f, 1f)
            vm.onCanvasPointerUp()

            // Undo stroke, then undo addLayer -> activeLayerId must fall back to an existing layer!
            vm.undo()
            vm.undo()
            val stateAfterUndo = vm.uiState.value
            assertTrue(
                "activeLayerId '${stateAfterUndo.activeLayerId}' missing after undoing addLayer",
                stateAfterUndo.project!!.layers.any { it.id == stateAfterUndo.activeLayerId },
            )

            // Update FPS, trigger saveNow on externalScope, clear ViewModelStore (simulating Back pop),
            // and verify save STILL completes cleanly!
            vm.updateFps(18)
            vm.saveNow()
            vmStore.clear()
            withContext(Dispatchers.IO) {
                var attempts = 0
                while ((repo.loadProject("p_layer_undo")?.fps ?: 12) != 18 && attempts < 100) {
                    kotlinx.coroutines.delay(20)
                    attempts++
                }
            }
            val reloaded = repo.loadProject("p_layer_undo")
            assertNotNull(reloaded)
            assertEquals(18, reloaded!!.fps)
        } finally {
            vmStore.clear()
            externalJob.cancel()
        }
    }
}
