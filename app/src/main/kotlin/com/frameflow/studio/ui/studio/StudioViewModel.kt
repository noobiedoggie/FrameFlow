package com.frameflow.studio.ui.studio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.ai.GeminiCoachTip
import com.frameflow.studio.ai.MotionPredictor
import com.frameflow.studio.ai.MotionTweenEngine
import com.frameflow.studio.engine.FloodFillEngine
import com.frameflow.studio.engine.StrokeGeometry
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.BrushType
import com.frameflow.studio.model.FillPatch
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.LayerContent
import com.frameflow.studio.model.LayerMeta
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.ShapeKind
import com.frameflow.studio.model.StrokePoint
import com.frameflow.studio.model.StudioTool
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.model.VectorStroke
import com.frameflow.studio.model.newId
import com.frameflow.studio.data.ProjectRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.roundToInt

fun interface FloodFillExecutor {
    suspend fun fill(
        layerContent: LayerContent,
        canvasWidth: Float,
        canvasHeight: Float,
        x: Float,
        y: Float,
        colorArgb: Long,
    ): FillPatch?
}

object DefaultFloodFillExecutor : FloodFillExecutor {
    override suspend fun fill(
        layerContent: LayerContent,
        canvasWidth: Float,
        canvasHeight: Float,
        x: Float,
        y: Float,
        colorArgb: Long,
    ): FillPatch? = withContext(Dispatchers.Default) {
        FloodFillEngine.computeLayerFillPatch(
            layerContent = layerContent,
            canvasWidth = canvasWidth,
            canvasHeight = canvasHeight,
            canvasX = x,
            canvasY = y,
            fillColorArgb = colorArgb,
        )
    }
}

data class StudioUiState(
    val project: AnimationProject? = null,
    val isLoading: Boolean = true,
    val currentFrameIndex: Int = 0,
    val activeLayerId: String = "layer_ink",
    val activeTool: StudioTool = StudioTool.PEN,
    val activeShapeKind: ShapeKind = ShapeKind.OVAL,
    val shapeFilled: Boolean = false,
    val brushColorArgb: Long = 0xFF1A1E29,
    val brushWidth: Float = 14f,
    val brushOpacity: Float = 1f,
    val eraserRadius: Float = 34f,
    val eraseWholeStroke: Boolean = false,
    val onionSkin: OnionSkinSettings = OnionSkinSettings(),
    val guides: GuideSettings = GuideSettings(),
    val isPlaying: Boolean = false,
    val isFocusMode: Boolean = false,
    val showAiGhost: Boolean = false,
    val aiGhostStrokes: List<VectorStroke> = emptyList(),
    val inProgressStroke: VectorStroke? = null,
    val lassoPolygon: List<StrokePoint> = emptyList(),
    val selectedStrokeIds: Set<String> = emptySet(),
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val isAiBusy: Boolean = false,
    val coachTip: GeminiCoachTip? = null,
    val statusBanner: String? = null,
) {
    val currentFrame: AnimationFrame?
        get() = project?.frames?.getOrNull(currentFrameIndex)
}

private data class HistoryEntry(
    val project: AnimationProject,
    val frameIndex: Int,
)

class StudioViewModel(
    private val projectId: String,
    private val repository: ProjectRepository,
    private val aiService: AiAnimationService,
    private val floodFillExecutor: FloodFillExecutor = DefaultFloodFillExecutor,
    private val persistScope: CoroutineScope? = null,
) : ViewModel() {

    private val saveCoroutineScope: CoroutineScope
        get() = persistScope ?: viewModelScope

    private val _uiState = MutableStateFlow(StudioUiState())
    val uiState: StateFlow<StudioUiState> = _uiState.asStateFlow()

    private val undoStack = ArrayDeque<HistoryEntry>()
    private val redoStack = ArrayDeque<HistoryEntry>()
    private val maxHistory = 35

    private var playbackJob: Job? = null
    private var saveJob: Job? = null

    private var activeStrokePoints = mutableListOf<StrokePoint>()
    private var lastPointTimeMs = 0L
    private var eraserPath = mutableListOf<StrokePoint>()
    private var erasePushedUndo = false
    private var shapeStartPoint: StrokePoint? = null

    private var lassoDraggingSelection = false
    private var lastLassoDragPoint: StrokePoint? = null
    private var lassoTransformPushedUndo = false

    init {
        load()
    }

    private fun load() {
        viewModelScope.launch {
            val loaded = repository.loadProject(projectId)
            if (loaded == null) {
                _uiState.update { it.copy(isLoading = false, statusBanner = "Project not found") }
                return@launch
            }
            val topLayerId = loaded.layers.lastOrNull()?.id ?: "layer_ink"
            val defaultColor = if (isDarkColor(loaded.backgroundColorArgb)) 0xFFFFFFFFL else 0xFF1A1E29L
            _uiState.update {
                it.copy(
                    project = loaded,
                    isLoading = false,
                    currentFrameIndex = 0,
                    activeLayerId = topLayerId,
                    brushColorArgb = defaultColor,
                )
            }
        }
    }

    private fun isDarkColor(argb: Long): Boolean {
        val r = ((argb shr 16) and 0xFF).toInt()
        val g = ((argb shr 8) and 0xFF).toInt()
        val b = (argb and 0xFF).toInt()
        return (0.299f * r + 0.587f * g + 0.114f * b) < 128f
    }

    private fun mutateProject(
        recordUndo: Boolean = true,
        newFrameIndex: Int? = null,
        transform: (AnimationProject) -> AnimationProject,
    ) {
        val state = _uiState.value
        val currentProj = state.project ?: return
        if (recordUndo) {
            undoStack.addLast(HistoryEntry(currentProj, state.currentFrameIndex))
            if (undoStack.size > maxHistory) undoStack.removeFirst()
            redoStack.clear()
        }
        val updatedProj = transform(currentProj)
        val safeIdx = (newFrameIndex ?: state.currentFrameIndex)
            .coerceIn(0, (updatedProj.frames.size - 1).coerceAtLeast(0))

        _uiState.update {
            it.copy(
                project = updatedProj,
                currentFrameIndex = safeIdx,
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
            )
        }
        refreshAiGhostIfEnabled()
        scheduleSave(updatedProj)
    }

    private fun scheduleSave(project: AnimationProject) {
        saveJob?.cancel()
        saveJob = saveCoroutineScope.launch {
            delay(250)
            repository.saveProject(project)
        }
    }

    fun saveNow() {
        val proj = _uiState.value.project ?: return
        saveJob?.cancel()
        saveJob = saveCoroutineScope.launch {
            repository.saveProject(proj)
        }
    }

    fun undo() {
        if (undoStack.isEmpty()) return
        cancelActivePointerGesture()
        val state = _uiState.value
        val currentProj = state.project ?: return
        redoStack.addLast(HistoryEntry(currentProj, state.currentFrameIndex))
        val prev = undoStack.removeLast()
        _uiState.update {
            val validLayerId = if (prev.project.layers.any { l -> l.id == it.activeLayerId }) {
                it.activeLayerId
            } else {
                prev.project.layers.lastOrNull()?.id ?: it.activeLayerId
            }
            it.copy(
                project = prev.project,
                currentFrameIndex = prev.frameIndex.coerceIn(0, (prev.project.frames.size - 1).coerceAtLeast(0)),
                activeLayerId = validLayerId,
                selectedStrokeIds = emptySet(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Undone",
            )
        }
        refreshAiGhostIfEnabled()
        scheduleSave(prev.project)
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        cancelActivePointerGesture()
        val state = _uiState.value
        val currentProj = state.project ?: return
        undoStack.addLast(HistoryEntry(currentProj, state.currentFrameIndex))
        val next = redoStack.removeLast()
        _uiState.update {
            val validLayerId = if (next.project.layers.any { l -> l.id == it.activeLayerId }) {
                it.activeLayerId
            } else {
                next.project.layers.lastOrNull()?.id ?: it.activeLayerId
            }
            it.copy(
                project = next.project,
                currentFrameIndex = next.frameIndex.coerceIn(0, (next.project.frames.size - 1).coerceAtLeast(0)),
                activeLayerId = validLayerId,
                selectedStrokeIds = emptySet(),
                canUndo = undoStack.isNotEmpty(),
                canRedo = redoStack.isNotEmpty(),
                statusBanner = "Redone",
            )
        }
        refreshAiGhostIfEnabled()
        scheduleSave(next.project)
    }

    fun cancelActivePointerGesture() {
        activeStrokePoints.clear()
        eraserPath.clear()
        erasePushedUndo = false
        shapeStartPoint = null
        lassoDraggingSelection = false
        lastLassoDragPoint = null
        lassoTransformPushedUndo = false
        if (_uiState.value.inProgressStroke != null || _uiState.value.lassoPolygon.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    inProgressStroke = null,
                    lassoPolygon = emptyList(),
                )
            }
        }
    }

    // --- Pointer / Drawing Interactions (in Virtual Canvas Coordinates) ---

    fun onCanvasPointerDown(x: Float, y: Float, pressure: Float? = null) {
        val state = _uiState.value
        if (state.isPlaying) togglePlayback()
        val project = state.project ?: return
        val activeLayer = project.layers.firstOrNull { it.id == state.activeLayerId }
        if (activeLayer?.locked == true) {
            showBanner("Layer '${activeLayer.name}' is locked")
            return
        }
        if (activeLayer?.visible == false) {
            showBanner("Layer '${activeLayer.name}' is hidden")
            return
        }

        val p = pressure?.takeIf { it > 0.05f && it < 1.5f } ?: 0.8f

        when (state.activeTool) {
            StudioTool.PEN, StudioTool.PENCIL, StudioTool.MARKER, StudioTool.HIGHLIGHTER -> {
                activeStrokePoints = mutableListOf(StrokePoint(x, y, p))
                lastPointTimeMs = System.currentTimeMillis()
                val brush = state.activeTool.toBrushType() ?: BrushType.PEN
                _uiState.update {
                    it.copy(
                        selectedStrokeIds = emptySet(),
                        inProgressStroke = VectorStroke(
                            points = activeStrokePoints.toList(),
                            brushType = brush,
                            colorArgb = it.brushColorArgb,
                            baseWidth = it.brushWidth,
                            opacity = it.brushOpacity,
                        ),
                    )
                }
            }

            StudioTool.ERASER -> {
                eraserPath = mutableListOf(StrokePoint(x, y, 1f))
                erasePushedUndo = false
                applyEraseStep()
            }

            StudioTool.SHAPE -> {
                shapeStartPoint = StrokePoint(x, y, 1f)
                val preview = StrokeGeometry.createShapeStroke(
                    kind = state.activeShapeKind,
                    start = shapeStartPoint!!,
                    end = StrokePoint(x + 2f, y + 2f, 1f),
                    brushType = BrushType.PEN,
                    colorArgb = state.brushColorArgb,
                    width = state.brushWidth,
                    opacity = state.brushOpacity,
                    fillColorArgb = if (state.shapeFilled) state.brushColorArgb else null,
                )
                _uiState.update { it.copy(inProgressStroke = preview, selectedStrokeIds = emptySet()) }
            }

            StudioTool.BUCKET -> {
                performBucketFill(x, y)
            }

            StudioTool.LASSO -> {
                val frame = state.currentFrame ?: return
                val layerStrokes = frame.contentFor(state.activeLayerId).strokes
                val pt = StrokePoint(x, y, 1f)
                if (state.selectedStrokeIds.isNotEmpty()) {
                    val selStrokes = layerStrokes.filter { it.id in state.selectedStrokeIds }
                    val bounds = StrokeGeometry.computeBounds(selStrokes)
                    if (bounds != null && x in (bounds.left - 36f)..(bounds.right + 36f) && y in (bounds.top - 36f)..(bounds.bottom + 36f)) {
                        lassoDraggingSelection = true
                        lastLassoDragPoint = pt
                        lassoTransformPushedUndo = false
                        return
                    }
                }
                lassoDraggingSelection = false
                _uiState.update { it.copy(lassoPolygon = listOf(pt), selectedStrokeIds = emptySet()) }
            }
        }
    }

    fun onCanvasPointerMove(x: Float, y: Float, pressure: Float? = null) {
        val state = _uiState.value
        val now = System.currentTimeMillis()
        when (state.activeTool) {
            StudioTool.PEN, StudioTool.PENCIL, StudioTool.MARKER, StudioTool.HIGHLIGHTER -> {
                val last = activeStrokePoints.lastOrNull() ?: return
                if (StrokeGeometry.distance(last.x, last.y, x, y) < 2.5f) return
                val p = if (pressure != null && pressure in 0.05f..1.4f && abs(pressure - 1f) > 0.02f) {
                    pressure
                } else {
                    StrokeGeometry.estimateFingerPressure(last, x, y, now - lastPointTimeMs)
                }
                lastPointTimeMs = now
                activeStrokePoints.add(StrokePoint(x, y, p))
                _uiState.update { s ->
                    s.copy(inProgressStroke = s.inProgressStroke?.copy(points = activeStrokePoints.toList()))
                }
            }

            StudioTool.ERASER -> {
                val last = eraserPath.lastOrNull() ?: return
                if (StrokeGeometry.distance(last.x, last.y, x, y) < 4f) return
                eraserPath.add(StrokePoint(x, y, 1f))
                applyEraseStep()
            }

            StudioTool.SHAPE -> {
                val start = shapeStartPoint ?: return
                val preview = StrokeGeometry.createShapeStroke(
                    kind = state.activeShapeKind,
                    start = start,
                    end = StrokePoint(x, y, 1f),
                    brushType = BrushType.PEN,
                    colorArgb = state.brushColorArgb,
                    width = state.brushWidth,
                    opacity = state.brushOpacity,
                    fillColorArgb = if (state.shapeFilled) state.brushColorArgb else null,
                )
                _uiState.update { it.copy(inProgressStroke = preview) }
            }

            StudioTool.LASSO -> {
                if (lassoDraggingSelection) {
                    val prev = lastLassoDragPoint ?: return
                    val dx = x - prev.x
                    val dy = y - prev.y
                    if (abs(dx) < 0.5f && abs(dy) < 0.5f) return
                    lastLassoDragPoint = StrokePoint(x, y, 1f)
                    val recordUndo = !lassoTransformPushedUndo
                    lassoTransformPushedUndo = true
                    mutateProject(recordUndo = recordUndo) { proj ->
                        updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                            content.copy(
                                strokes = StrokeGeometry.transformStrokes(
                                    strokes = content.strokes,
                                    selectedIds = state.selectedStrokeIds,
                                    dx = dx,
                                    dy = dy,
                                )
                            )
                        }
                    }
                } else {
                    val poly = state.lassoPolygon
                    val last = poly.lastOrNull() ?: return
                    if (StrokeGeometry.distance(last.x, last.y, x, y) >= 8f) {
                        _uiState.update { it.copy(lassoPolygon = it.lassoPolygon + StrokePoint(x, y, 1f)) }
                    }
                }
            }

            StudioTool.BUCKET -> Unit
        }
    }

    fun onCanvasPointerUp() {
        val state = _uiState.value
        val project = state.project ?: return
        when (state.activeTool) {
            StudioTool.PEN, StudioTool.PENCIL, StudioTool.MARKER, StudioTool.HIGHLIGHTER -> {
                val rawStroke = state.inProgressStroke
                activeStrokePoints.clear()
                _uiState.update { it.copy(inProgressStroke = null) }
                if (rawStroke != null && rawStroke.points.isNotEmpty()) {
                    val finalStroke = if (state.guides.autoSnapShapes) {
                        StrokeGeometry.trySnapRoughShape(rawStroke) ?: rawStroke
                    } else {
                        rawStroke
                    }
                    val cw = project.aspectRatio.canvasWidth
                    val strokesToAdd = if (state.guides.mirrorHorizontal) {
                        val mirrored = finalStroke.copy(
                            id = newId(),
                            points = finalStroke.points.map { pt -> pt.copy(x = cw - pt.x) },
                        )
                        listOf(finalStroke, mirrored)
                    } else {
                        listOf(finalStroke)
                    }
                    mutateProject { proj ->
                        updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                            content.copy(strokes = content.strokes + strokesToAdd)
                        }
                    }
                }
            }

            StudioTool.ERASER -> {
                eraserPath.clear()
                erasePushedUndo = false
            }

            StudioTool.SHAPE -> {
                val shapeStroke = state.inProgressStroke
                shapeStartPoint = null
                _uiState.update { it.copy(inProgressStroke = null) }
                if (shapeStroke != null && StrokeGeometry.pathLength(shapeStroke.points, shapeStroke.isClosed) > 8f) {
                    val cw = project.aspectRatio.canvasWidth
                    val strokesToAdd = if (state.guides.mirrorHorizontal) {
                        val mirrored = shapeStroke.copy(
                            id = newId(),
                            points = shapeStroke.points.map { pt -> pt.copy(x = cw - pt.x) },
                        )
                        listOf(shapeStroke, mirrored)
                    } else {
                        listOf(shapeStroke)
                    }
                    mutateProject { proj ->
                        updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                            content.copy(strokes = content.strokes + strokesToAdd)
                        }
                    }
                }
            }

            StudioTool.LASSO -> {
                if (lassoDraggingSelection) {
                    lassoDraggingSelection = false
                    lastLassoDragPoint = null
                    lassoTransformPushedUndo = false
                    return
                }
                val poly = state.lassoPolygon
                _uiState.update { it.copy(lassoPolygon = emptyList()) }
                val layerStrokes = state.currentFrame?.contentFor(state.activeLayerId)?.strokes.orEmpty()
                val selected = if (poly.size < 4 || StrokeGeometry.pathLength(poly) < 35f) {
                    val tap = poly.firstOrNull()
                    if (tap != null) {
                        StrokeGeometry.findStrokeAtPoint(layerStrokes, tap.x, tap.y)?.let { setOf(it.id) } ?: emptySet()
                    } else emptySet()
                } else {
                    StrokeGeometry.selectStrokesInLasso(layerStrokes, poly)
                }
                _uiState.update { it.copy(selectedStrokeIds = selected) }
            }

            StudioTool.BUCKET -> Unit
        }
    }

    private fun applyEraseStep() {
        val state = _uiState.value
        val frame = state.currentFrame ?: return
        val content = frame.contentFor(state.activeLayerId)
        if (content.strokes.isEmpty() && content.fills.isEmpty()) return

        val newStrokes = StrokeGeometry.eraseFromStrokes(
            strokes = content.strokes,
            eraserPath = eraserPath,
            eraserRadius = state.eraserRadius,
            wholeStroke = state.eraseWholeStroke,
        )
        val latestSegment = if (eraserPath.size >= 2) {
            listOf(eraserPath[eraserPath.lastIndex - 1], eraserPath.last())
        } else {
            eraserPath
        }
        val newFills = FloodFillEngine.eraseFromFillPatches(
            fills = content.fills,
            eraserPath = latestSegment,
            eraserRadius = state.eraserRadius,
            wholeStroke = state.eraseWholeStroke,
        )

        if (newStrokes != content.strokes || newFills != content.fills) {
            val shouldRecord = !erasePushedUndo
            erasePushedUndo = true
            mutateProject(recordUndo = shouldRecord) { proj ->
                updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) {
                    LayerContent(strokes = newStrokes, fills = newFills)
                }
            }
        }
    }

    private fun performBucketFill(x: Float, y: Float) {
        val state = _uiState.value
        val proj = state.project ?: return
        val frame = state.currentFrame ?: return
        val targetFrameId = frame.id
        val layerId = state.activeLayerId
        val content = frame.contentFor(layerId)
        val visibleContents = proj.layers.filter { it.visible }.map { frame.contentFor(it.id) }
        val baseColor = state.brushColorArgb
        val baseAlpha = ((baseColor ushr 24) and 0xFF).toInt()
        val scaledAlpha = (baseAlpha * state.brushOpacity).roundToInt().coerceIn(25, 255)
        val effectiveColor = ((scaledAlpha.toLong() shl 24) or (baseColor and 0x00FFFFFFL))

        viewModelScope.launch {
            val patch = if (floodFillExecutor === DefaultFloodFillExecutor) {
                withContext(Dispatchers.Default) {
                    FloodFillEngine.computeLayerFillPatch(
                        layerContent = content,
                        canvasWidth = proj.aspectRatio.canvasWidth,
                        canvasHeight = proj.aspectRatio.canvasHeight,
                        canvasX = x,
                        canvasY = y,
                        fillColorArgb = effectiveColor,
                        boundaryContents = visibleContents,
                    )
                }
            } else {
                val boundaryContent = LayerContent(
                    strokes = visibleContents.flatMap { it.strokes },
                    fills = visibleContents.flatMap { it.fills },
                )
                floodFillExecutor.fill(
                    layerContent = boundaryContent,
                    canvasWidth = proj.aspectRatio.canvasWidth,
                    canvasHeight = proj.aspectRatio.canvasHeight,
                    x = x,
                    y = y,
                    colorArgb = effectiveColor,
                )
            }
            if (patch == null) {
                showBanner("Tap inside an unfilled shape or region")
                return@launch
            }
            mutateProject { latest ->
                val targetIdx = latest.frames.indexOfFirst { it.id == targetFrameId }
                if (targetIdx < 0) return@mutateProject latest
                updateActiveLayerContent(latest, targetIdx, layerId) { c ->
                    c.copy(fills = c.fills + patch)
                }
            }
        }
    }

    // --- Selection Transforms ---

    fun scaleSelectedStrokes(factor: Float) {
        val state = _uiState.value
        if (state.selectedStrokeIds.isEmpty()) return
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                content.copy(
                    strokes = StrokeGeometry.transformStrokes(
                        strokes = content.strokes,
                        selectedIds = state.selectedStrokeIds,
                        dx = 0f,
                        dy = 0f,
                        scale = factor,
                    )
                )
            }
        }
    }

    fun rotateSelectedStrokes(degrees: Float) {
        val state = _uiState.value
        if (state.selectedStrokeIds.isEmpty()) return
        val rad = Math.toRadians(degrees.toDouble()).toFloat()
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                content.copy(
                    strokes = StrokeGeometry.transformStrokes(
                        strokes = content.strokes,
                        selectedIds = state.selectedStrokeIds,
                        dx = 0f,
                        dy = 0f,
                        rotationRadians = rad,
                    )
                )
            }
        }
    }

    fun duplicateSelectedStrokes() {
        val state = _uiState.value
        if (state.selectedStrokeIds.isEmpty()) return
        val newIds = mutableSetOf<String>()
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                val clones = content.strokes.filter { it.id in state.selectedStrokeIds }.map { s ->
                    val nid = newId()
                    newIds.add(nid)
                    s.copy(
                        id = nid,
                        points = s.points.map { it.copy(x = it.x + 28f, y = it.y + 28f) },
                    )
                }
                content.copy(strokes = content.strokes + clones)
            }
        }
        _uiState.update { it.copy(selectedStrokeIds = newIds) }
    }

    fun deleteSelectedStrokes() {
        val state = _uiState.value
        if (state.selectedStrokeIds.isEmpty()) return
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                content.copy(strokes = content.strokes.filterNot { it.id in state.selectedStrokeIds })
            }
        }
        _uiState.update { it.copy(selectedStrokeIds = emptySet()) }
    }

    fun clearSelection() {
        _uiState.update { it.copy(selectedStrokeIds = emptySet()) }
    }

    // --- Tools & Settings ---

    fun selectTool(tool: StudioTool) {
        cancelActivePointerGesture()
        _uiState.update { it.copy(activeTool = tool, selectedStrokeIds = emptySet()) }
    }

    fun selectShapeKind(kind: ShapeKind) {
        _uiState.update { it.copy(activeTool = StudioTool.SHAPE, activeShapeKind = kind) }
    }

    fun setShapeFilled(filled: Boolean) {
        _uiState.update { it.copy(shapeFilled = filled) }
    }

    fun setBrushColor(argb: Long) {
        _uiState.update { it.copy(brushColorArgb = argb) }
    }

    fun setBrushWidth(width: Float) {
        _uiState.update { it.copy(brushWidth = width.coerceIn(2f, 64f)) }
    }

    fun setBrushOpacity(opacity: Float) {
        _uiState.update { it.copy(brushOpacity = opacity.coerceIn(0.1f, 1f)) }
    }

    fun setEraserRadius(radius: Float) {
        _uiState.update { it.copy(eraserRadius = radius.coerceIn(10f, 90f)) }
    }

    fun setEraseWholeStroke(whole: Boolean) {
        _uiState.update { it.copy(eraseWholeStroke = whole) }
    }

    fun toggleFocusMode() {
        _uiState.update { it.copy(isFocusMode = !it.isFocusMode) }
    }

    fun updateOnionSkin(transform: (OnionSkinSettings) -> OnionSkinSettings) {
        _uiState.update { it.copy(onionSkin = transform(it.onionSkin)) }
    }

    fun updateGuides(transform: (GuideSettings) -> GuideSettings) {
        _uiState.update { it.copy(guides = transform(it.guides)) }
    }

    fun updateFps(fps: Int) {
        mutateProject(recordUndo = false) { it.copy(fps = fps.coerceIn(1, 30)) }
    }

    fun updateTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isNotEmpty()) {
            mutateProject(recordUndo = false) { it.copy(title = trimmed) }
        }
    }

    fun setBackgroundColor(argb: Long) {
        mutateProject { it.copy(backgroundColorArgb = argb) }
    }

    // --- Layers ---

    fun selectLayer(layerId: String) {
        _uiState.update { it.copy(activeLayerId = layerId, selectedStrokeIds = emptySet()) }
        refreshAiGhostIfEnabled()
    }

    fun toggleLayerVisibility(layerId: String) {
        mutateProject(recordUndo = false) { proj ->
            proj.copy(layers = proj.layers.map { if (it.id == layerId) it.copy(visible = !it.visible) else it })
        }
    }

    fun toggleLayerLock(layerId: String) {
        mutateProject(recordUndo = false) { proj ->
            proj.copy(layers = proj.layers.map { if (it.id == layerId) it.copy(locked = !it.locked) else it })
        }
    }

    fun setLayerOpacity(layerId: String, opacity: Float) {
        mutateProject(recordUndo = false) { proj ->
            proj.copy(
                layers = proj.layers.map {
                    if (it.id == layerId) it.copy(opacity = opacity.coerceIn(0.05f, 1f)) else it
                }
            )
        }
    }

    fun addLayer(name: String = "") {
        val proj = _uiState.value.project ?: return
        if (proj.layers.size >= 5) {
            showBanner("Up to 5 layers keeps playback silky smooth")
            return
        }
        val layer = LayerMeta(
            id = "layer_${newId()}",
            name = name.ifBlank { "Layer ${proj.layers.size + 1}" },
        )
        mutateProject { it.copy(layers = it.layers + layer) }
        _uiState.update { it.copy(activeLayerId = layer.id) }
    }

    fun deleteLayer(layerId: String) {
        val proj = _uiState.value.project ?: return
        if (proj.layers.size <= 1) {
            showBanner("At least 1 layer is required")
            return
        }
        val remaining = proj.layers.filterNot { it.id == layerId }
        mutateProject { p ->
            p.copy(
                layers = remaining,
                frames = p.frames.map { f -> f.copy(layers = f.layers - layerId) },
            )
        }
        if (_uiState.value.activeLayerId == layerId) {
            _uiState.update { it.copy(activeLayerId = remaining.last().id) }
        }
    }

    // --- Frame Timeline & Playback ---

    fun selectFrame(index: Int) {
        val proj = _uiState.value.project ?: return
        val safe = index.coerceIn(0, (proj.frames.size - 1).coerceAtLeast(0))
        _uiState.update {
            it.copy(
                currentFrameIndex = safe,
                selectedStrokeIds = emptySet(),
            )
        }
        refreshAiGhostIfEnabled()
    }

    fun addBlankFrameAfterCurrent() {
        val state = _uiState.value
        val proj = state.project ?: return
        val insertAt = (state.currentFrameIndex + 1).coerceIn(0, proj.frames.size)
        mutateProject(newFrameIndex = insertAt) { p ->
            val list = p.frames.toMutableList()
            list.add(insertAt, AnimationFrame())
            p.copy(frames = list)
        }
    }

    fun duplicateCurrentFrame() {
        val state = _uiState.value
        val proj = state.project ?: return
        val src = proj.frames.getOrNull(state.currentFrameIndex) ?: return
        val copy = src.copy(
            id = newId(),
            layers = src.layers.mapValues { (_, content) ->
                content.copy(strokes = content.strokes.map { it.copy(id = newId()) })
            },
        )
        val insertAt = state.currentFrameIndex + 1
        mutateProject(newFrameIndex = insertAt) { p ->
            val list = p.frames.toMutableList()
            list.add(insertAt, copy)
            p.copy(frames = list)
        }
        showBanner("Frame duplicated")
    }

    fun deleteCurrentFrame() {
        val state = _uiState.value
        val proj = state.project ?: return
        if (proj.frames.size <= 1) {
            // Clear content on single frame instead of deleting last frame
            mutateProject { p ->
                p.copy(frames = listOf(AnimationFrame()))
            }
            showBanner("Canvas cleared")
            return
        }
        val idx = state.currentFrameIndex
        val nextIdx = idx.coerceAtMost(proj.frames.size - 2)
        mutateProject(newFrameIndex = nextIdx) { p ->
            val list = p.frames.toMutableList()
            list.removeAt(idx)
            p.copy(frames = list)
        }
    }

    fun clearCurrentLayer() {
        val state = _uiState.value
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) {
                LayerContent()
            }
        }
        showBanner("Layer cleared")
    }

    fun cycleFrameHold(index: Int) {
        mutateProject { p ->
            val list = p.frames.toMutableList()
            val frame = list.getOrNull(index) ?: return@mutateProject p
            val nextHold = when (frame.holdCount) {
                1 -> 2
                2 -> 3
                3 -> 4
                else -> 1
            }
            list[index] = frame.copy(holdCount = nextHold)
            p.copy(frames = list)
        }
    }

    fun moveCurrentFrame(delta: Int) {
        val state = _uiState.value
        val proj = state.project ?: return
        val from = state.currentFrameIndex
        val to = (from + delta).coerceIn(0, proj.frames.size - 1)
        if (from == to) return
        mutateProject(newFrameIndex = to) { p ->
            val list = p.frames.toMutableList()
            val item = list.removeAt(from)
            list.add(to, item)
            p.copy(frames = list)
        }
    }

    fun togglePlayback() {
        val state = _uiState.value
        if (state.isPlaying) {
            playbackJob?.cancel()
            playbackJob = null
            _uiState.update { it.copy(isPlaying = false) }
            refreshAiGhostIfEnabled()
            return
        }
        val proj = state.project ?: return
        if (proj.frames.size <= 1) {
            showBanner("Add at least 2 frames to play")
            return
        }
        _uiState.update { it.copy(isPlaying = true, selectedStrokeIds = emptySet(), aiGhostStrokes = emptyList()) }
        playbackJob = viewModelScope.launch {
            val timeline = proj.expandedTimelineIndices()
            val frameDelayMs = (1000L / proj.fps.coerceIn(1, 30)).coerceAtLeast(30L)
            var step = timeline.indexOf(_uiState.value.currentFrameIndex).coerceAtLeast(0)
            while (_uiState.value.isPlaying) {
                val latest = _uiState.value.project ?: break
                val latestTimeline = latest.expandedTimelineIndices()
                if (latestTimeline.isEmpty()) break
                val targetIndex = latestTimeline[step % latestTimeline.size]
                _uiState.update { it.copy(currentFrameIndex = targetIndex) }
                delay((1000L / latest.fps.coerceIn(1, 30)).coerceAtLeast(30L))
                step = (step + 1) % latestTimeline.size
            }
        }
    }

    // --- AI Features ---

    fun runAiInBetween(count: Int, easing: TweenEasing, towardNext: Boolean = true) {
        val state = _uiState.value
        val proj = state.project ?: return
        val idxA = if (towardNext) state.currentFrameIndex else state.currentFrameIndex - 1
        val idxB = if (towardNext) state.currentFrameIndex + 1 else state.currentFrameIndex

        if (idxA !in proj.frames.indices || idxB !in proj.frames.indices) {
            showBanner("Need an adjacent keyframe to in-between")
            return
        }
        val frameA = proj.frames[idxA]
        val frameB = proj.frames[idxB]
        val generated = MotionTweenEngine.generateInBetweens(
            startFrame = frameA,
            endFrame = frameB,
            layers = proj.layers,
            count = count,
            easing = easing,
        )
        mutateProject(newFrameIndex = idxA + 1) { p ->
            val list = p.frames.toMutableList()
            list.addAll(idxA + 1, generated)
            p.copy(frames = list)
        }
        showBanner("Added ${generated.size} AI in-between frames (${easing.displayName})")
    }

    fun runAiGenerate(
        prompt: String,
        frameCount: Int,
        replaceBlankProject: Boolean = true,
        useCurrentFrameAsSketch: Boolean = false,
    ) {
        val state = _uiState.value
        val proj = state.project ?: return
        if (prompt.isBlank()) return
        if (state.isAiBusy) return

        val inkId = proj.layers.lastOrNull()?.id ?: state.activeLayerId
        val colorId = proj.layers.firstOrNull()?.id ?: state.activeLayerId

        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true) }
            try {
                val result = aiService.generateFrames(
                    prompt = prompt,
                    frameCount = frameCount,
                    aspectRatio = proj.aspectRatio,
                    inkLayerId = inkId,
                    colorLayerId = colorId,
                    projectForSketchContext = if (useCurrentFrameAsSketch) proj else null,
                    currentFrameIndex = state.currentFrameIndex,
                )
                val shouldReplace = replaceBlankProject &&
                    proj.frames.size == 1 &&
                    !proj.frames[0].hasAnyContent()

                val insertIdx = if (shouldReplace) 0 else state.currentFrameIndex + 1
                mutateProject(newFrameIndex = insertIdx) { p ->
                    if (shouldReplace) {
                        p.copy(
                            fps = result.suggestedFps,
                            frames = result.frames,
                        )
                    } else {
                        val list = p.frames.toMutableList()
                        val safeInsert = insertIdx.coerceIn(0, list.size)
                        list.addAll(safeInsert, result.frames)
                        p.copy(frames = list)
                    }
                }
                showBanner(result.summary)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showBanner("AI generation failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isAiBusy = false) }
            }
        }
    }

    fun polishCurrentFrame(snapShapes: Boolean = true) {
        val state = _uiState.value
        val proj = state.project ?: return
        val frame = proj.frames.getOrNull(state.currentFrameIndex) ?: return
        if (!frame.hasAnyContent()) {
            showBanner("Draw a sketch first to polish")
            return
        }
        mutateProject { p ->
            val list = p.frames.toMutableList()
            list[state.currentFrameIndex] = MotionPredictor.polishFrame(frame, p.layers, snapShapes)
            p.copy(frames = list)
        }
        showBanner(if (snapShapes) "Polished strokes & snapped shapes" else "Smoothed finger lines")
    }

    fun toggleAiGhost() {
        val next = !_uiState.value.showAiGhost
        _uiState.update { it.copy(showAiGhost = next) }
        refreshAiGhostIfEnabled()
        if (next && _uiState.value.aiGhostStrokes.isEmpty()) {
            showBanner("Draw on previous frame(s) to see AI trajectory ghost")
        }
    }

    fun commitAiGhost() {
        val state = _uiState.value
        val ghost = state.aiGhostStrokes
        if (ghost.isEmpty()) {
            showBanner("No AI motion prediction on this frame yet")
            return
        }
        mutateProject { proj ->
            updateActiveLayerContent(proj, state.currentFrameIndex, state.activeLayerId) { content ->
                content.copy(strokes = content.strokes + ghost.map { it.copy(id = newId()) })
            }
        }
        showBanner("Stamped AI predicted pose onto frame")
    }

    fun askAiCoach(question: String) {
        val state = _uiState.value
        val proj = state.project ?: return
        if (state.isAiBusy) return
        viewModelScope.launch {
            _uiState.update { it.copy(isAiBusy = true) }
            try {
                val tip = aiService.coachProject(proj, state.currentFrameIndex, question)
                _uiState.update { it.copy(coachTip = tip) }
            } finally {
                _uiState.update { it.copy(isAiBusy = false) }
            }
        }
    }

    fun dismissCoachTip() {
        _uiState.update { it.copy(coachTip = null) }
    }

    private fun refreshAiGhostIfEnabled() {
        val state = _uiState.value
        val proj = state.project
        if (!state.showAiGhost || proj == null || state.isPlaying) {
            if (state.aiGhostStrokes.isNotEmpty()) {
                _uiState.update { it.copy(aiGhostStrokes = emptyList()) }
            }
            return
        }
        val predicted = MotionPredictor.predictNextFrameStrokes(
            frames = proj.frames,
            targetFrameIndex = state.currentFrameIndex,
            layerId = state.activeLayerId,
        )
        _uiState.update { it.copy(aiGhostStrokes = predicted) }
    }

    fun clearBanner() {
        _uiState.update { it.copy(statusBanner = null) }
    }

    private fun showBanner(message: String) {
        _uiState.update { it.copy(statusBanner = message) }
    }

    private fun updateActiveLayerContent(
        project: AnimationProject,
        frameIndex: Int,
        layerId: String,
        transform: (LayerContent) -> LayerContent,
    ): AnimationProject {
        val list = project.frames.toMutableList()
        val frame = list.getOrNull(frameIndex) ?: return project
        val updatedContent = transform(frame.contentFor(layerId))
        list[frameIndex] = frame.withLayerContent(layerId, updatedContent)
        return project.copy(frames = list)
    }

    override fun onCleared() {
        playbackJob?.cancel()
        if (persistScope != null && saveJob?.isActive == true) {
            val proj = _uiState.value.project
            saveJob?.cancel()
            if (proj != null) {
                persistScope.launch { repository.saveProject(proj) }
            }
        } else {
            saveJob?.cancel()
        }
        super.onCleared()
    }
}
