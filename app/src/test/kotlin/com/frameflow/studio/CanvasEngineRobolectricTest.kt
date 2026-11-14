package com.frameflow.studio

import android.content.Context
import android.graphics.Color
import androidx.core.content.edit
import androidx.test.core.app.ApplicationProvider
import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.data.FileProjectRepository
import com.frameflow.studio.engine.FillBitmapCache
import com.frameflow.studio.engine.FloodFillEngine
import com.frameflow.studio.engine.FrameRenderer
import com.frameflow.studio.export.Mp4VideoExporter
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.StudioTool
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.ui.studio.StudioViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withContext
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CanvasEngineRobolectricTest {

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
    fun crossLayerBucketFillRespectsInkLayerBoundsAndRenders() {
        val layers = AnimationProject.defaultLayers()
        val colorId = layers[0].id
        val inkId = layers[1].id

        // Closed thick ink square from (300,300) to (700,700) on Ink layer
        val inkBox = VectorStroke(
            points = listOf(
                StrokePoint(300f, 300f),
                StrokePoint(700f, 300f),
                StrokePoint(700f, 700f),
                StrokePoint(300f, 700f),
            ),
            colorArgb = 0xFF1A1E29,
            baseWidth = 22f,
            isClosed = true,
        )
        val inkContent = LayerContent(strokes = listOf(inkBox))
        val colorContent = LayerContent()

        val patch = FloodFillEngine.computeLayerFillPatch(
            layerContent = colorContent,
            canvasWidth = 1080f,
            canvasHeight = 1080f,
            canvasX = 500f,
            canvasY = 500f,
            fillColorArgb = 0xFFFF5C7AL,
            boundaryContents = listOf(colorContent, inkContent),
        )

        assertNotNull("Expected bounded fill inside Ink box", patch)
        val p = patch!!
        // Should stay strictly confined inside (300..700), not leak to canvas edges (0..1080)
        assertTrue("Fill leaked left (${p.left})", p.left > 250f)
        assertTrue("Fill leaked top (${p.top})", p.top > 250f)
        assertTrue("Fill leaked width (${p.width})", p.width < 460f)
        assertTrue("Fill leaked height (${p.height})", p.height < 460f)

        val proj = AnimationProject(
            id = "fill_render",
            title = "Fill Render",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            backgroundColorArgb = 0xFFFFFFFFL,
            layers = layers,
            frames = listOf(
                AnimationFrame(
                    layers = mapOf(
                        colorId to LayerContent(fills = listOf(p)),
                        inkId to inkContent,
                    )
                )
            ),
        )
        val bmp = FrameRenderer.renderFrameToBitmap(proj, 0, 200, 200)
        try {
            // Center pixel must match fill color, outside corner must stay white background
            val centerPx = bmp.getPixel(100, 100)
            val cornerPx = bmp.getPixel(10, 10)
            assertEquals(Color.WHITE, cornerPx)
            assertTrue(Color.red(centerPx) > 200 && Color.green(centerPx) < 130)
        } finally {
            bmp.recycle()
        }
    }

    @Test
    fun splitAndWholeEraserModifyAndRemoveFillPatches() {
        val layers = AnimationProject.defaultLayers()
        val inkBox = VectorStroke(
            points = listOf(
                StrokePoint(250f, 250f),
                StrokePoint(750f, 250f),
                StrokePoint(750f, 750f),
                StrokePoint(250f, 750f),
            ),
            colorArgb = 0xFF000000L,
            baseWidth = 20f,
            isClosed = true,
        )
        val fill = FloodFillEngine.computeLayerFillPatch(
            layerContent = LayerContent(strokes = listOf(inkBox)),
            canvasWidth = 1080f,
            canvasHeight = 1080f,
            canvasX = 500f,
            canvasY = 500f,
            fillColorArgb = 0xFF34E8C7L,
        )!!

        // Split erase across the top strip only -> must produce updated patch with new id (for cache invalidation)
        val afterSplit = FloodFillEngine.eraseFromFillPatches(
            fills = listOf(fill),
            eraserPath = listOf(StrokePoint(250f, 360f), StrokePoint(750f, 360f)),
            eraserRadius = 60f,
            wholeStroke = false,
        )
        assertEquals(1, afterSplit.size)
        val splitPatch = afterSplit.first()
        assertNotEquals("Split erase must assign fresh id for FillBitmapCache", fill.id, splitPatch.id)

        val bmp = FillBitmapCache.getAndroidBitmap(splitPatch)!!
        // Near y=360 should be erased (alpha == 0), near bottom y=640 should remain filled (alpha > 0)
        val erasedSampleY = (((360f - splitPatch.top) / splitPatch.height) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val keptSampleY = (((640f - splitPatch.top) / splitPatch.height) * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        assertEquals(0, Color.alpha(bmp.getPixel(bmp.width / 2, erasedSampleY)))
        assertTrue(Color.alpha(bmp.getPixel(bmp.width / 2, keptSampleY)) > 200)

        // Whole erase touching remaining bottom half removes the patch completely
        val afterWhole = FloodFillEngine.eraseFromFillPatches(
            fills = afterSplit,
            eraserPath = listOf(StrokePoint(500f, 640f)),
            eraserRadius = 25f,
            wholeStroke = true,
        )
        assertTrue(afterWhole.isEmpty())
    }

    @Test
    fun studioViewModelFillsColorLayerBoundedByInkAndSplitErasesFill() = runTest {
        val repo = FileProjectRepository(tempFolder.newFolder("bucket_vm"))
        val layers = AnimationProject.defaultLayers()
        val colorId = layers[0].id
        val inkId = layers[1].id
        val inkBox = VectorStroke(
            points = listOf(
                StrokePoint(300f, 300f),
                StrokePoint(700f, 300f),
                StrokePoint(700f, 700f),
                StrokePoint(300f, 700f),
            ),
            colorArgb = 0xFF000000L,
            baseWidth = 20f,
            isClosed = true,
        )
        val proj = AnimationProject(
            id = "bucket_vm_1",
            title = "Cross Layer Fill",
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            layers = layers,
            frames = listOf(
                AnimationFrame(layers = mapOf(inkId to LayerContent(strokes = listOf(inkBox))))
            ),
        )
        repo.saveProject(proj)

        val vm = StudioViewModel(
            projectId = "bucket_vm_1",
            repository = repo,
            aiService = AiAnimationService(getApiKey = { "" }),
        )
        val vmStore = androidx.lifecycle.ViewModelStore()
        val holder = androidx.lifecycle.ViewModelProvider(
            vmStore,
            object : androidx.lifecycle.ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = vm as T
            },
        )[StudioViewModel::class.java]
        try {
            withContext(Dispatchers.Default) {
                var tries = 0
                while (holder.uiState.value.isLoading && tries < 100) {
                    kotlinx.coroutines.delay(15)
                    tries++
                }
            }

            // Select Color layer (below Ink), pick Bucket, tap inside the Ink square
            holder.selectLayer(colorId)
            holder.selectTool(StudioTool.BUCKET)
            holder.setBrushColor(0xFF7C8BFFL)
            holder.onCanvasPointerDown(500f, 500f, 1f)
            holder.onCanvasPointerUp()
            withContext(Dispatchers.Default) {
                var tries = 0
                while (holder.uiState.value.currentFrame!!.contentFor(colorId).fills.isEmpty() && tries < 100) {
                    kotlinx.coroutines.delay(15)
                    tries++
                }
            }
            val fillsAfterBucket = holder.uiState.value.currentFrame!!.contentFor(colorId).fills
            assertEquals(1, fillsAfterBucket.size)
            assertTrue("Bucket fill leaked out of Ink boundary (width=${fillsAfterBucket.first().width})", fillsAfterBucket.first().width < 460f)

            // Split-erase across top of fill on Color layer
            holder.selectTool(StudioTool.ERASER)
            holder.setEraseWholeStroke(false)
            holder.setEraserRadius(50f)
            holder.onCanvasPointerDown(350f, 380f, 1f)
            holder.onCanvasPointerMove(650f, 380f, 1f)
            holder.onCanvasPointerUp()

            val fillsAfterErase = holder.uiState.value.currentFrame!!.contentFor(colorId).fills
            assertEquals(1, fillsAfterErase.size)
            assertNotEquals(fillsAfterBucket.first().id, fillsAfterErase.first().id)
        } finally {
            vmStore.clear()
        }

        // Filling outside canvas bounds should be ignored safely
        val nullFill = FloodFillEngine.computeLayerFillPatch(
            layerContent = LayerContent(),
            canvasWidth = 1080f,
            canvasHeight = 1080f,
            canvasX = -20f,
            canvasY = 500f,
            fillColorArgb = 0xFF7C8BFFL,
        )
        assertNull(nullFill)
    }

    @Test
    fun mp4ExporterAlignsDimensionsAndConvertsArgbToYuv420Accurately() {
        val (w, h) = Mp4VideoExporter.alignedVideoDimensions(405, 720)
        assertEquals(416, w)
        assertEquals(720, h)
        assertEquals(0, w % 16)
        assertEquals(0, h % 16)

        // 4x4 pure white vs black
        val whitePixels = IntArray(16) { Color.WHITE }
        val yuvWhite = Mp4VideoExporter.argbToYuv420(whitePixels, 4, 4)
        assertEquals(16, yuvWhite.y.size)
        assertEquals(4, yuvWhite.u.size)
        assertEquals(4, yuvWhite.v.size)
        assertEquals(235, yuvWhite.y[0].toInt() and 0xFF)
        assertEquals(128, yuvWhite.u[0].toInt() and 0xFF)
        assertEquals(128, yuvWhite.v[0].toInt() and 0xFF)

        val blackPixels = IntArray(16) { Color.BLACK }
        val yuvBlack = Mp4VideoExporter.argbToYuv420(blackPixels, 4, 4)
        assertEquals(16, yuvBlack.y[0].toInt() and 0xFF)
        assertEquals(128, yuvBlack.u[0].toInt() and 0xFF)
        assertEquals(128, yuvBlack.v[0].toInt() and 0xFF)
    }

    @Test
    fun aiSettingsStoreUpdatesStateFlowAndPersistsAcrossInstances() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store1 = AiSettingsStore(context)
        store1.updateSettings("  AIzaTestKey123  ", "gemini-4-argon")

        assertEquals("AIzaTestKey123", store1.settings.value.apiKey)
        assertEquals("gemini-4-argon", store1.settings.value.model)

        val store2 = AiSettingsStore(context)
        assertEquals("AIzaTestKey123", store2.settings.value.apiKey)
        assertEquals("gemini-4-argon", store2.settings.value.model)
    }

    @Test
    fun aiSettingsStoreNormalizesLegacyHydrogenModelAndListsValidModels() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("frameflow_ai_prefs", Context.MODE_PRIVATE)
            .edit(commit = true) {
                putString("gemini_model", "gemini-4-hydrogen")
            }

        val store = AiSettingsStore(context)
        assertEquals("gemini-3.8-flash", store.settings.value.model)
        assertEquals("gemini-3.8-flash", store.getGeminiModel())

        store.setGeminiModel("gemini-4-hydrogen")
        assertEquals("gemini-3.8-flash", store.getGeminiModel())

        store.setGeminiModel("  GEMINI-4-HYDROGEN  ")
        assertEquals("gemini-3.8-flash", store.getGeminiModel())

        store.setGeminiModel("GEMINI-4-ARGON")
        assertEquals("gemini-4-argon", store.getGeminiModel())

        val modelIds = AiSettingsStore.AVAILABLE_MODELS.map { it.first }
        assertEquals(
            listOf("gemini-3.8-flash", "gemini-4-argon", "gemini-3.6-flash", "gemini-3.5-flash-lite"),
            modelIds,
        )
    }
}
