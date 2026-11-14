package com.frameflow.studio.ui.studio

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.engine.FrameRenderer.drawThumbnailFrame
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.ShapeKind
import com.frameflow.studio.model.StudioTool
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.ui.components.StudioIcons
import com.frameflow.studio.ui.home.AiSettingsSheet
import com.frameflow.studio.ui.theme.CoralAccent
import com.frameflow.studio.ui.theme.FrameFlowTheme
import com.frameflow.studio.ui.theme.IndigoAccent
import com.frameflow.studio.ui.theme.IndigoAccentContainer
import com.frameflow.studio.ui.theme.MintAiAccent
import com.frameflow.studio.ui.theme.MintAiContainer
import com.frameflow.studio.ui.theme.ObsidianBg
import com.frameflow.studio.ui.theme.StudioOutline
import com.frameflow.studio.ui.theme.StudioSurface
import com.frameflow.studio.ui.theme.StudioSurfaceElevated
import com.frameflow.studio.ui.theme.StudioSurfaceHighest
import com.frameflow.studio.ui.theme.StudioTextMuted
import com.frameflow.studio.ui.theme.StudioTextPrimary
import com.frameflow.studio.ui.theme.StudioTextSecondary

@Composable
fun StudioScreen(
    viewModel: StudioViewModel,
    initialApiKey: String,
    initialModel: String,
    onSaveAiSettings: (String, String) -> Unit,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveNow()
        }
    }

    StudioScreenContent(
        state = state,
        hasCloudKey = initialApiKey.isNotBlank(),
        initialApiKey = initialApiKey,
        initialModel = initialModel,
        onSaveAiSettings = onSaveAiSettings,
        onBack = {
            viewModel.saveNow()
            onBack()
        },
        onPointerDown = viewModel::onCanvasPointerDown,
        onPointerMove = viewModel::onCanvasPointerMove,
        onPointerUp = viewModel::onCanvasPointerUp,
        onCancelPointer = viewModel::cancelActivePointerGesture,
        onUndo = viewModel::undo,
        onRedo = viewModel::redo,
        onScaleSelected = viewModel::scaleSelectedStrokes,
        onRotateSelected = viewModel::rotateSelectedStrokes,
        onDuplicateSelected = viewModel::duplicateSelectedStrokes,
        onDeleteSelected = viewModel::deleteSelectedStrokes,
        onClearSelection = viewModel::clearSelection,
        onSelectTool = viewModel::selectTool,
        onSelectShapeKind = viewModel::selectShapeKind,
        onSetShapeFilled = viewModel::setShapeFilled,
        onSetBrushColor = viewModel::setBrushColor,
        onSetBrushWidth = viewModel::setBrushWidth,
        onSetBrushOpacity = viewModel::setBrushOpacity,
        onSetEraserRadius = viewModel::setEraserRadius,
        onSetEraseWholeStroke = viewModel::setEraseWholeStroke,
        onToggleFocusMode = viewModel::toggleFocusMode,
        onUpdateOnionSkin = { next -> viewModel.updateOnionSkin { next } },
        onUpdateGuides = { next -> viewModel.updateGuides { next } },
        onUpdateFps = viewModel::updateFps,
        onSetBackgroundColor = viewModel::setBackgroundColor,
        onSelectLayer = viewModel::selectLayer,
        onToggleLayerVisibility = viewModel::toggleLayerVisibility,
        onToggleLayerLock = viewModel::toggleLayerLock,
        onSetLayerOpacity = viewModel::setLayerOpacity,
        onAddLayer = { viewModel.addLayer() },
        onDeleteLayer = viewModel::deleteLayer,
        onSelectFrame = viewModel::selectFrame,
        onAddBlankFrame = viewModel::addBlankFrameAfterCurrent,
        onDuplicateFrame = viewModel::duplicateCurrentFrame,
        onDeleteFrame = viewModel::deleteCurrentFrame,
        onCycleFrameHold = viewModel::cycleFrameHold,
        onTogglePlay = viewModel::togglePlayback,
        onRunAiTween = viewModel::runAiInBetween,
        onRunAiGenerate = viewModel::runAiGenerate,
        onRunAiPolish = viewModel::polishCurrentFrame,
        onToggleAiGhost = viewModel::toggleAiGhost,
        onCommitAiGhost = viewModel::commitAiGhost,
        onAskCoach = viewModel::askAiCoach,
        onClearBanner = viewModel::clearBanner,
    )
}

@Composable
fun StudioScreenContent(
    state: StudioUiState,
    hasCloudKey: Boolean,
    initialApiKey: String = "",
    initialModel: String = AiSettingsStore.AVAILABLE_MODELS.first().first,
    onSaveAiSettings: (String, String) -> Unit = { _, _ -> },
    onBack: () -> Unit = {},
    onPointerDown: (Float, Float, Float?) -> Unit = { _, _, _ -> },
    onPointerMove: (Float, Float, Float?) -> Unit = { _, _, _ -> },
    onPointerUp: () -> Unit = {},
    onCancelPointer: () -> Unit = {},
    onUndo: () -> Unit = {},
    onRedo: () -> Unit = {},
    onScaleSelected: (Float) -> Unit = {},
    onRotateSelected: (Float) -> Unit = {},
    onDuplicateSelected: () -> Unit = {},
    onDeleteSelected: () -> Unit = {},
    onClearSelection: () -> Unit = {},
    onSelectTool: (StudioTool) -> Unit = {},
    onSelectShapeKind: (ShapeKind) -> Unit = {},
    onSetShapeFilled: (Boolean) -> Unit = {},
    onSetBrushColor: (Long) -> Unit = {},
    onSetBrushWidth: (Float) -> Unit = {},
    onSetBrushOpacity: (Float) -> Unit = {},
    onSetEraserRadius: (Float) -> Unit = {},
    onSetEraseWholeStroke: (Boolean) -> Unit = {},
    onToggleFocusMode: () -> Unit = {},
    onUpdateOnionSkin: (OnionSkinSettings) -> Unit = {},
    onUpdateGuides: (GuideSettings) -> Unit = {},
    onUpdateFps: (Int) -> Unit = {},
    onSetBackgroundColor: (Long) -> Unit = {},
    onSelectLayer: (String) -> Unit = {},
    onToggleLayerVisibility: (String) -> Unit = {},
    onToggleLayerLock: (String) -> Unit = {},
    onSetLayerOpacity: (String, Float) -> Unit = { _, _ -> },
    onAddLayer: () -> Unit = {},
    onDeleteLayer: (String) -> Unit = {},
    onSelectFrame: (Int) -> Unit = {},
    onAddBlankFrame: () -> Unit = {},
    onDuplicateFrame: () -> Unit = {},
    onDeleteFrame: () -> Unit = {},
    onCycleFrameHold: (Int) -> Unit = {},
    onTogglePlay: () -> Unit = {},
    onRunAiTween: (Int, TweenEasing, Boolean) -> Unit = { _, _, _ -> },
    onRunAiGenerate: (String, Int, Boolean, Boolean) -> Unit = { _, _, _, _ -> },
    onRunAiPolish: (Boolean) -> Unit = {},
    onToggleAiGhost: () -> Unit = {},
    onCommitAiGhost: () -> Unit = {},
    onAskCoach: (String) -> Unit = {},
    onClearBanner: () -> Unit = {},
) {
    var showColorSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSheet by rememberSaveable { mutableStateOf(false) }
    var showLayersSheet by rememberSaveable { mutableStateOf(false) }
    var showExportSheet by rememberSaveable { mutableStateOf(false) }
    var showAiSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var cloudConfigured by remember(hasCloudKey) { mutableStateOf(hasCloudKey) }

    val project = state.project

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!state.isFocusMode && project != null) {
                val activeLayerName = project.layers.firstOrNull { it.id == state.activeLayerId }?.name ?: "Ink"
                StudioTopBar(
                    title = project.title,
                    fps = project.fps,
                    activeLayerName = activeLayerName,
                    isPlaying = state.isPlaying,
                    isAiBusy = state.isAiBusy,
                    onBack = onBack,
                    onOpenStage = { showLayersSheet = true },
                    onOpenAi = { showAiSheet = true },
                    onTogglePlay = onTogglePlay,
                    onOpenExport = { showExportSheet = true },
                    onToggleFocus = onToggleFocusMode,
                )
            }
        },
        bottomBar = {
            if (!state.isFocusMode && project != null) {
                StudioBottomDeck(
                    state = state,
                    project = project,
                    onOpenColorSheet = { showColorSheet = true },
                    onSelectTool = onSelectTool,
                    onSelectShapeKind = onSelectShapeKind,
                    onSetShapeFilled = onSetShapeFilled,
                    onSetBrushWidth = onSetBrushWidth,
                    onSetBrushOpacity = onSetBrushOpacity,
                    onSetEraserRadius = onSetEraserRadius,
                    onSetEraseWholeStroke = onSetEraseWholeStroke,
                    onUndo = onUndo,
                    onRedo = onRedo,
                    onToggleOnion = {
                        onUpdateOnionSkin(state.onionSkin.copy(enabled = !state.onionSkin.enabled))
                    },
                    onOpenLayers = { showLayersSheet = true },
                    onSelectFrame = onSelectFrame,
                    onAddBlankFrame = onAddBlankFrame,
                    onDuplicateFrame = onDuplicateFrame,
                    onDeleteFrame = onDeleteFrame,
                    onCycleFrameHold = onCycleFrameHold,
                )
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isLoading || project == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = IndigoAccent)
                }
            } else {
                StudioCanvas(
                    project = project,
                    currentFrameIndex = state.currentFrameIndex,
                    activeLayerId = state.activeLayerId,
                    onionSkin = state.onionSkin,
                    guides = state.guides,
                    inProgressStroke = state.inProgressStroke,
                    lassoPolygon = state.lassoPolygon,
                    selectedStrokeIds = state.selectedStrokeIds,
                    aiGhostStrokes = if (state.showAiGhost) state.aiGhostStrokes else emptyList(),
                    isPlaying = state.isPlaying,
                    modifier = Modifier.fillMaxSize(),
                    onPointerDown = onPointerDown,
                    onPointerMove = onPointerMove,
                    onPointerUp = onPointerUp,
                    onCancelPointer = onCancelPointer,
                    onTapWhilePlaying = onTogglePlay,
                    onTwoFingerTapUndo = onUndo,
                    onThreeFingerTapRedo = onRedo,
                    onScaleSelected = onScaleSelected,
                    onRotateSelected = onRotateSelected,
                    onDuplicateSelected = onDuplicateSelected,
                    onDeleteSelected = onDeleteSelected,
                    onClearSelection = onClearSelection,
                    onCommitGhost = onCommitAiGhost,
                )

                // Status toast pill inside canvas top area
                AnimatedVisibility(
                    visible = state.statusBanner != null,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = if (state.isFocusMode) 48.dp else 10.dp),
                ) {
                    state.statusBanner?.let { banner ->
                        Surface(
                            onClick = onClearBanner,
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated.copy(alpha = 0.94f),
                            border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.65f)),
                            modifier = Modifier.padding(horizontal = 16.dp),
                        ) {
                            Text(
                                text = banner,
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioTextPrimary,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }

                // Exit focus mode + minimal undo/play controls in focus mode
                if (state.isFocusMode) {
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Surface(
                            onClick = onTogglePlay,
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, StudioOutline),
                        ) {
                            Icon(
                                imageVector = if (state.isPlaying) StudioIcons.Pause else StudioIcons.Play,
                                contentDescription = if (state.isPlaying) "Pause" else "Play",
                                tint = IndigoAccent,
                                modifier = Modifier
                                    .padding(10.dp)
                                    .size(18.dp),
                            )
                        }
                        Surface(
                            onClick = onToggleFocusMode,
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated.copy(alpha = 0.9f),
                            border = BorderStroke(1.dp, StudioOutline),
                            modifier = Modifier.testTag("exit_focus_btn"),
                        ) {
                            Text(
                                text = "Exit Focus",
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioTextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            )
                        }
                    }
                }
            }
        }
    }

    if (showColorSheet) {
        ColorPaletteSheet(
            currentColorArgb = state.brushColorArgb,
            onSelectColor = onSetBrushColor,
            onDismiss = { showColorSheet = false },
        )
    }

    if (showAiSheet && project != null) {
        AiAssistantSheet(
            state = state,
            hasCloudKey = cloudConfigured,
            onRunTween = onRunAiTween,
            onRunGenerate = onRunAiGenerate,
            onRunPolish = onRunAiPolish,
            onToggleGhost = onToggleAiGhost,
            onStampGhost = onCommitAiGhost,
            onAskCoach = onAskCoach,
            onApplyFps = onUpdateFps,
            onOpenAiSettings = { showAiSettingsSheet = true },
            onDismiss = { showAiSheet = false },
        )
    }

    if (showLayersSheet && project != null) {
        LayersAndGuidesSheet(
            project = project,
            activeLayerId = state.activeLayerId,
            onionSkin = state.onionSkin,
            guides = state.guides,
            onSelectLayer = onSelectLayer,
            onToggleLayerVisibility = onToggleLayerVisibility,
            onToggleLayerLock = onToggleLayerLock,
            onSetLayerOpacity = onSetLayerOpacity,
            onAddLayer = onAddLayer,
            onDeleteLayer = onDeleteLayer,
            onUpdateOnionSkin = onUpdateOnionSkin,
            onUpdateGuides = onUpdateGuides,
            onUpdateFps = onUpdateFps,
            onSetBackgroundColor = onSetBackgroundColor,
            onDismiss = { showLayersSheet = false },
        )
    }

    if (showExportSheet && project != null) {
        ExportSheet(
            project = project,
            currentFrameIndex = state.currentFrameIndex,
            onDismiss = { showExportSheet = false },
        )
    }

    if (showAiSettingsSheet) {
        AiSettingsSheet(
            initialApiKey = initialApiKey,
            initialModel = initialModel,
            onDismiss = { showAiSettingsSheet = false },
            onSave = { key, model ->
                onSaveAiSettings(key, model)
                cloudConfigured = key.isNotBlank()
                showAiSettingsSheet = false
            },
        )
    }
}

@Composable
private fun StudioTopBar(
    title: String,
    fps: Int,
    activeLayerName: String,
    isPlaying: Boolean,
    isAiBusy: Boolean,
    onBack: () -> Unit,
    onOpenStage: () -> Unit,
    onOpenAi: () -> Unit,
    onTogglePlay: () -> Unit,
    onOpenExport: () -> Unit,
    onToggleFocus: () -> Unit,
) {
    Surface(
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(38.dp)
                        .testTag("studio_back_btn"),
                ) {
                    Icon(
                        imageVector = StudioIcons.Back,
                        contentDescription = "Back to projects",
                        tint = StudioTextPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onOpenStage)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "$fps FPS · $activeLayerName",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                        maxLines = 1,
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                // Mint AI Copilot button
                Surface(
                    onClick = onOpenAi,
                    shape = RoundedCornerShape(999.dp),
                    color = MintAiContainer.copy(alpha = 0.78f),
                    border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("studio_ai_btn"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        if (isAiBusy) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(13.dp),
                                strokeWidth = 1.8.dp,
                                color = MintAiAccent,
                            )
                        } else {
                            Icon(
                                imageVector = StudioIcons.SparkleAi,
                                contentDescription = "AI Copilot",
                                tint = MintAiAccent,
                                modifier = Modifier.size(14.dp),
                            )
                        }
                        Text(
                            text = "AI",
                            style = MaterialTheme.typography.labelMedium,
                            color = MintAiAccent,
                        )
                    }
                }

                // Play / Pause pill
                Surface(
                    onClick = onTogglePlay,
                    shape = RoundedCornerShape(999.dp),
                    color = if (isPlaying) IndigoAccent else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (isPlaying) IndigoAccent else StudioOutline),
                    modifier = Modifier.testTag("studio_play_btn"),
                ) {
                    Icon(
                        imageVector = if (isPlaying) StudioIcons.Pause else StudioIcons.Play,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = if (isPlaying) ObsidianBg else StudioTextPrimary,
                        modifier = Modifier
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                            .size(18.dp),
                    )
                }

                IconButton(
                    onClick = onOpenExport,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("studio_export_btn"),
                ) {
                    Icon(
                        imageVector = StudioIcons.Export,
                        contentDescription = "Export animation",
                        tint = StudioTextPrimary,
                        modifier = Modifier.size(19.dp),
                    )
                }

                IconButton(
                    onClick = onToggleFocus,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("studio_focus_btn"),
                ) {
                    Icon(
                        imageVector = StudioIcons.Focus,
                        contentDescription = "Focus mode",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StudioBottomDeck(
    state: StudioUiState,
    project: AnimationProject,
    onOpenColorSheet: () -> Unit,
    onSelectTool: (StudioTool) -> Unit,
    onSelectShapeKind: (ShapeKind) -> Unit,
    onSetShapeFilled: (Boolean) -> Unit,
    onSetBrushWidth: (Float) -> Unit,
    onSetBrushOpacity: (Float) -> Unit,
    onSetEraserRadius: (Float) -> Unit,
    onSetEraseWholeStroke: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onToggleOnion: () -> Unit,
    onOpenLayers: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddBlankFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onCycleFrameHold: (Int) -> Unit,
) {
    Surface(
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.65f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(top = 6.dp, bottom = 6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // 1. Contextual quick parameter bar + Color dot + Undo/Redo (always single-thumb ready)
            ContextualToolOptionsRow(
                state = state,
                onOpenColorSheet = onOpenColorSheet,
                onSelectShapeKind = onSelectShapeKind,
                onSetShapeFilled = onSetShapeFilled,
                onSetBrushWidth = onSetBrushWidth,
                onSetBrushOpacity = onSetBrushOpacity,
                onSetEraserRadius = onSetEraserRadius,
                onSetEraseWholeStroke = onSetEraseWholeStroke,
                onUndo = onUndo,
                onRedo = onRedo,
            )

            // 2. Drawing tool strip
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StudioTool.entries.forEach { tool ->
                    val selected = tool == state.activeTool
                    val icon = toolIcon(tool)
                    Surface(
                        onClick = { onSelectTool(tool) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) IndigoAccentContainer else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) IndigoAccent else StudioOutline.copy(alpha = 0.7f)),
                        modifier = Modifier.testTag("tool_${tool.name.lowercase()}"),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = tool.displayName,
                                tint = if (selected) IndigoAccent else StudioTextSecondary,
                                modifier = Modifier.size(17.dp),
                            )
                            Text(
                                text = tool.displayName,
                                fontSize = 10.sp,
                                lineHeight = 12.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                color = if (selected) IndigoAccent else StudioTextSecondary,
                            )
                        }
                    }
                }
            }

            // 3. Flipbook Timeline Strip + Compact Frame Controls
            TimelineStrip(
                project = project,
                currentFrameIndex = state.currentFrameIndex,
                onionEnabled = state.onionSkin.enabled,
                onToggleOnion = onToggleOnion,
                onOpenLayers = onOpenLayers,
                onSelectFrame = onSelectFrame,
                onAddBlankFrame = onAddBlankFrame,
                onDuplicateFrame = onDuplicateFrame,
                onDeleteFrame = onDeleteFrame,
                onCycleFrameHold = onCycleFrameHold,
            )
        }
    }
}

@Composable
private fun ContextualToolOptionsRow(
    state: StudioUiState,
    onOpenColorSheet: () -> Unit,
    onSelectShapeKind: (ShapeKind) -> Unit,
    onSetShapeFilled: (Boolean) -> Unit,
    onSetBrushWidth: (Float) -> Unit,
    onSetBrushOpacity: (Float) -> Unit,
    onSetEraserRadius: (Float) -> Unit,
    onSetEraseWholeStroke: (Boolean) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        // Left: contextual tool parameters (scrollable horizontally for narrow screens)
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (state.activeTool != StudioTool.ERASER && state.activeTool != StudioTool.LASSO) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(state.brushColorArgb.toInt()))
                        .border(2.dp, StudioTextPrimary.copy(alpha = 0.85f), CircleShape)
                        .clickable(onClick = onOpenColorSheet)
                        .semantics { contentDescription = "Pick color" }
                        .testTag("studio_color_swatch_btn"),
                )
            }

            when (state.activeTool) {
                StudioTool.PEN, StudioTool.PENCIL, StudioTool.MARKER, StudioTool.HIGHLIGHTER -> {
                    val widths = listOf(6f to "S", 14f to "M", 24f to "L", 38f to "XL")
                    widths.forEach { (w, label) ->
                        val selected = kotlin.math.abs(state.brushWidth - w) < 3f
                        MiniOptionPill(
                            text = label,
                            selected = selected,
                            onClick = { onSetBrushWidth(w) },
                        )
                    }
                    val opLabel = "${(state.brushOpacity * 100f).toInt()}%"
                    MiniOptionPill(
                        text = opLabel,
                        selected = state.brushOpacity < 0.98f,
                        onClick = {
                            val next = when {
                                state.brushOpacity > 0.9f -> 0.65f
                                state.brushOpacity > 0.5f -> 0.35f
                                else -> 1f
                            }
                            onSetBrushOpacity(next)
                        },
                    )
                }

                StudioTool.ERASER -> {
                    listOf(18f to "Fine", 34f to "Med", 56f to "Wide").forEach { (r, label) ->
                        val selected = kotlin.math.abs(state.eraserRadius - r) < 4f
                        MiniOptionPill(
                            text = label,
                            selected = selected,
                            onClick = { onSetEraserRadius(r) },
                        )
                    }
                    MiniOptionPill(
                        text = if (state.eraseWholeStroke) "Whole" else "Split",
                        selected = state.eraseWholeStroke,
                        onClick = { onSetEraseWholeStroke(!state.eraseWholeStroke) },
                    )
                }

                StudioTool.BUCKET -> {
                    Text(
                        text = "Tap inside region to fill",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                }

                StudioTool.SHAPE -> {
                    ShapeKind.entries.forEach { kind ->
                        MiniOptionPill(
                            text = kind.displayName,
                            selected = kind == state.activeShapeKind,
                            onClick = { onSelectShapeKind(kind) },
                        )
                    }
                    MiniOptionPill(
                        text = if (state.shapeFilled) "Filled" else "Outline",
                        selected = state.shapeFilled,
                        onClick = { onSetShapeFilled(!state.shapeFilled) },
                    )
                }

                StudioTool.LASSO -> {
                    Text(
                        text = "Draw loop around strokes",
                        style = MaterialTheme.typography.labelMedium,
                        color = StudioTextSecondary,
                    )
                }
            }
        }

        // Right: Instant Undo & Redo buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = onUndo,
                enabled = state.canUndo,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("studio_undo_btn"),
            ) {
                Icon(
                    imageVector = StudioIcons.Undo,
                    contentDescription = "Undo",
                    tint = if (state.canUndo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = onRedo,
                enabled = state.canRedo,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("studio_redo_btn"),
            ) {
                Icon(
                    imageVector = StudioIcons.Redo,
                    contentDescription = "Redo",
                    tint = if (state.canRedo) StudioTextPrimary else StudioTextMuted.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniOptionPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (selected) IndigoAccentContainer else StudioSurfaceElevated,
        border = BorderStroke(1.dp, if (selected) IndigoAccent else StudioOutline),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) IndigoAccent else StudioTextSecondary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
        )
    }
}

@Composable
private fun TimelineStrip(
    project: AnimationProject,
    currentFrameIndex: Int,
    onionEnabled: Boolean,
    onToggleOnion: () -> Unit,
    onOpenLayers: () -> Unit,
    onSelectFrame: (Int) -> Unit,
    onAddBlankFrame: () -> Unit,
    onDuplicateFrame: () -> Unit,
    onDeleteFrame: () -> Unit,
    onCycleFrameHold: (Int) -> Unit,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(currentFrameIndex) {
        if (currentFrameIndex in project.frames.indices) {
            listState.animateScrollToItem(currentFrameIndex)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        // Timeline control strip (horizontally scrollable for narrow phones / split screen)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Frame ${currentFrameIndex + 1}/${project.frames.size}",
                style = MaterialTheme.typography.labelMedium,
                color = StudioTextPrimary,
                modifier = Modifier.testTag("timeline_frame_counter"),
            )

            Surface(
                onClick = onToggleOnion,
                shape = RoundedCornerShape(999.dp),
                color = if (onionEnabled) IndigoAccentContainer else StudioSurfaceElevated,
                border = BorderStroke(1.dp, if (onionEnabled) IndigoAccent else StudioOutline),
                modifier = Modifier.testTag("timeline_onion_toggle"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = StudioIcons.OnionSkin,
                        contentDescription = "Toggle Onion Skin",
                        tint = if (onionEnabled) IndigoAccent else StudioTextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Onion",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (onionEnabled) IndigoAccent else StudioTextSecondary,
                    )
                }
            }

            Surface(
                onClick = onOpenLayers,
                shape = RoundedCornerShape(999.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.testTag("timeline_layers_btn"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = StudioIcons.Layers,
                        contentDescription = "Layers & Guides",
                        tint = StudioTextSecondary,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Layers",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                    )
                }
            }

            Surface(
                onClick = onDuplicateFrame,
                shape = RoundedCornerShape(999.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline),
                modifier = Modifier.testTag("timeline_dup_btn"),
            ) {
                Text(
                    text = "Dup",
                    style = MaterialTheme.typography.labelSmall,
                    color = StudioTextPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            if (project.frames.size > 1) {
                Surface(
                    onClick = onDeleteFrame,
                    shape = RoundedCornerShape(999.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.testTag("timeline_del_btn"),
                ) {
                    Text(
                        text = "Del",
                        style = MaterialTheme.typography.labelSmall,
                        color = CoralAccent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }

            Surface(
                onClick = onAddBlankFrame,
                shape = RoundedCornerShape(999.dp),
                color = IndigoAccent,
                modifier = Modifier.testTag("timeline_add_btn"),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = StudioIcons.Plus,
                        contentDescription = "Add frame",
                        tint = ObsidianBg,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "Frame",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = ObsidianBg,
                    )
                }
            }
        }

        // Scrubbable filmstrip thumbnails
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp),
        ) {
            itemsIndexed(project.frames, key = { _, frame -> frame.id }) { index, frame ->
                TimelineFrameCell(
                    index = index,
                    frame = frame,
                    project = project,
                    isSelected = index == currentFrameIndex,
                    onSelect = { onSelectFrame(index) },
                    onCycleHold = { onCycleFrameHold(index) },
                )
            }
        }
    }
}

@Composable
private fun TimelineFrameCell(
    index: Int,
    frame: AnimationFrame,
    project: AnimationProject,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onCycleHold: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(54.dp)
            .aspectRatio(project.aspectRatio.ratio.coerceIn(0.75f, 1.45f))
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) IndigoAccent else StudioOutline,
                shape = RoundedCornerShape(10.dp),
            )
            .clickable(onClick = onSelect)
            .testTag("timeline_frame_$index"),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawThumbnailFrame(
                frame = frame,
                layers = project.layers,
                canvasWidth = project.aspectRatio.canvasWidth,
                canvasHeight = project.aspectRatio.canvasHeight,
                backgroundColorArgb = project.backgroundColorArgb,
            )
        }

        // Frame index number top-left
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(2.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(ObsidianBg.copy(alpha = 0.6f))
                .padding(horizontal = 3.dp, vertical = 0.5.dp),
        ) {
            Text(
                text = "${index + 1}",
                fontSize = 8.sp,
                lineHeight = 10.sp,
                color = if (isSelected) IndigoAccent else StudioTextPrimary,
            )
        }

        // Tap-to-cycle hold count badge bottom-right (x2, x3, ...)
        if (frame.holdCount > 1 || isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(2.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (frame.holdCount > 1) IndigoAccent else ObsidianBg.copy(alpha = 0.6f))
                    .clickable(onClick = onCycleHold)
                    .padding(horizontal = 3.dp, vertical = 0.5.dp),
            ) {
                Text(
                    text = "${frame.holdCount}x",
                    fontSize = 8.sp,
                    lineHeight = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (frame.holdCount > 1) ObsidianBg else StudioTextSecondary,
                )
            }
        }
    }
}

private fun toolIcon(tool: StudioTool): ImageVector = when (tool) {
    StudioTool.PEN -> StudioIcons.Pen
    StudioTool.PENCIL -> StudioIcons.Pencil
    StudioTool.MARKER -> StudioIcons.Marker
    StudioTool.HIGHLIGHTER -> StudioIcons.Highlighter
    StudioTool.ERASER -> StudioIcons.Eraser
    StudioTool.BUCKET -> StudioIcons.Bucket
    StudioTool.SHAPE -> StudioIcons.Shapes
    StudioTool.LASSO -> StudioIcons.Lasso
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun StudioScreenPreview() {
    val layers = AnimationProject.defaultLayers()
    val bounce = LocalAnimationSynthesizer.synthesize(
        prompt = "Bouncing squash and stretch ball",
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        inkLayerId = layers[1].id,
        colorLayerId = layers[0].id,
        frameCount = 6,
    )
    val previewProject = AnimationProject(
        id = "preview_studio",
        title = "Squash & Bounce",
        fps = 12,
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        layers = layers,
        frames = bounce.frames,
    )

    FrameFlowTheme {
        StudioScreenContent(
            state = StudioUiState(
                project = previewProject,
                isLoading = false,
                currentFrameIndex = 2,
                activeLayerId = layers[1].id,
                canUndo = true,
            ),
            hasCloudKey = false,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 620, backgroundColor = 0xFF131722)
@Composable
fun AiSheetPreview() {
    val layers = AnimationProject.defaultLayers()
    val bounce = LocalAnimationSynthesizer.synthesize(
        prompt = "Bouncing ball",
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        inkLayerId = layers[1].id,
        colorLayerId = layers[0].id,
        frameCount = 6,
    )
    val previewProject = AnimationProject(
        id = "preview_ai",
        title = "Squash & Bounce",
        fps = 12,
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        layers = layers,
        frames = bounce.frames,
    )

    FrameFlowTheme {
        Box(modifier = Modifier.background(StudioSurface)) {
            AiAssistantSheetContent(
                state = StudioUiState(
                    project = previewProject,
                    isLoading = false,
                    currentFrameIndex = 1,
                ),
                hasCloudKey = false,
                onRunTween = { _, _, _ -> },
                onRunGenerate = { _, _, _, _ -> },
                onRunPolish = {},
                onToggleGhost = {},
                onStampGhost = {},
                onAskCoach = {},
                onOpenAiSettings = {},
            )
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 560, backgroundColor = 0xFF131722)
@Composable
fun ExportSheetPreview() {
    val previewProject = AnimationProject(
        id = "preview_export",
        title = "Squash & Bounce",
        fps = 12,
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
    )

    FrameFlowTheme {
        Box(modifier = Modifier.background(StudioSurface)) {
            ExportSheetContent(
                project = previewProject,
                selectedFormat = com.frameflow.studio.export.ExportFormat.GIF,
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
}
