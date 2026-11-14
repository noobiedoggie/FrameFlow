package com.frameflow.studio.ui.home

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.frameflow.studio.ai.LocalAnimationSynthesizer
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.engine.FrameRenderer.drawThumbnailFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.ProjectSummary
import com.frameflow.studio.model.toSummary
import com.frameflow.studio.ui.components.StudioIcons
import com.frameflow.studio.ui.theme.FrameFlowTheme
import com.frameflow.studio.ui.theme.IndigoAccent
import com.frameflow.studio.ui.theme.IndigoAccentContainer
import com.frameflow.studio.ui.theme.MintAiAccent
import com.frameflow.studio.ui.theme.MintAiContainer
import com.frameflow.studio.ui.theme.ObsidianBg
import com.frameflow.studio.ui.theme.PaperBackgroundSwatches
import com.frameflow.studio.ui.theme.StudioOutline
import com.frameflow.studio.ui.theme.StudioSurface
import com.frameflow.studio.ui.theme.StudioSurfaceElevated
import com.frameflow.studio.ui.theme.StudioSurfaceHighest
import com.frameflow.studio.ui.theme.StudioTextMuted
import com.frameflow.studio.ui.theme.StudioTextPrimary
import com.frameflow.studio.ui.theme.StudioTextSecondary
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val HomePromptSuggestions = listOf(
    "Bouncing ball",
    "Rocket launch",
    "Stick wave",
    "Beating heart",
    "Campfire flames",
    "Blinking cat",
    "Flying bird",
    "Solar orbit",
    "Rain ripples",
)

@Composable
fun ProjectsHomeScreen(
    viewModel: ProjectsHomeViewModel,
    initialApiKey: String,
    initialModel: String,
    onSaveAiSettings: (apiKey: String, model: String) -> Unit,
    onOpenProject: (String) -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.statusMessage) {
        val msg = state.statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearStatus()
    }

    ProjectsHomeContent(
        state = state,
        hasCloudKey = initialApiKey.isNotBlank(),
        initialApiKey = initialApiKey,
        initialModel = initialModel,
        snackbarHostState = snackbarHostState,
        onSaveAiSettings = onSaveAiSettings,
        onOpenProject = onOpenProject,
        onCreateProject = { title, fps, aspect, bgArgb, prompt, frames ->
            viewModel.createProject(
                title = title,
                fps = fps,
                aspectRatio = aspect,
                backgroundColorArgb = bgArgb,
                aiPrompt = prompt,
                aiFrameCount = frames,
                onCreated = onOpenProject,
            )
        },
        onDuplicateProject = viewModel::duplicateProject,
        onDeleteProject = viewModel::deleteProject,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectsHomeContent(
    state: ProjectsHomeUiState,
    hasCloudKey: Boolean,
    initialApiKey: String = "",
    initialModel: String = AiSettingsStore.AVAILABLE_MODELS.first().first,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    onSaveAiSettings: (apiKey: String, model: String) -> Unit = { _, _ -> },
    onOpenProject: (String) -> Unit = {},
    onCreateProject: (
        title: String,
        fps: Int,
        aspectRatio: AspectRatioPreset,
        backgroundColorArgb: Long,
        aiPrompt: String,
        aiFrameCount: Int,
    ) -> Unit = { _, _, _, _, _, _ -> },
    onDuplicateProject: (String) -> Unit = {},
    onDeleteProject: (String) -> Unit = {},
    animateThumbnails: Boolean = true,
) {
    var showCreateSheet by rememberSaveable { mutableStateOf(false) }
    var awaitingCreateClose by rememberSaveable { mutableStateOf(false) }
    var createWithAiDefault by rememberSaveable { mutableStateOf(false) }
    var showSettingsSheet by rememberSaveable { mutableStateOf(false) }
    var cloudConfigured by remember(hasCloudKey) { mutableStateOf(hasCloudKey) }

    LaunchedEffect(awaitingCreateClose, state.isCreating) {
        if (awaitingCreateClose && !state.isCreating) {
            showCreateSheet = false
            awaitingCreateClose = false
        }
    }

    Scaffold(
        containerColor = ObsidianBg,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.navigationBarsPadding(),
            )
        },
        bottomBar = {
            Surface(
                color = ObsidianBg.copy(alpha = 0.94f),
                border = BorderStroke(1.dp, StudioOutline.copy(alpha = 0.55f)),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = {
                            createWithAiDefault = true
                            showCreateSheet = true
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                            .testTag("home_ai_create_btn"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.55f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = MintAiContainer.copy(alpha = 0.5f),
                            contentColor = MintAiAccent,
                        ),
                    ) {
                        Icon(
                            imageVector = StudioIcons.SparkleAi,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "AI Prompt",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Button(
                        onClick = {
                            createWithAiDefault = false
                            showCreateSheet = true
                        },
                        modifier = Modifier
                            .weight(1.25f)
                            .height(50.dp)
                            .testTag("home_new_canvas_btn"),
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndigoAccent,
                            contentColor = ObsidianBg,
                        ),
                    ) {
                        Icon(
                            imageVector = StudioIcons.Plus,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "New Flipbook",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(top = 8.dp),
        ) {
            HomeHeaderBar(
                cloudConfigured = cloudConfigured,
                onOpenSettings = { showSettingsSheet = true },
            )

            if (state.projects.isEmpty()) {
                EmptyProjectsState(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    onNewBlank = {
                        createWithAiDefault = false
                        showCreateSheet = true
                    },
                    onNewAi = {
                        createWithAiDefault = true
                        showCreateSheet = true
                    },
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 164.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("projects_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectCard(
                            summary = project,
                            animateThumbnail = animateThumbnails,
                            onClick = { onOpenProject(project.id) },
                            onDuplicate = { onDuplicateProject(project.id) },
                            onDelete = { onDeleteProject(project.id) },
                        )
                    }
                }
            }
        }
    }

    if (showCreateSheet) {
        CreateProjectSheet(
            startInAiMode = createWithAiDefault,
            isCreating = state.isCreating,
            onDismiss = {
                if (!state.isCreating) {
                    awaitingCreateClose = false
                    showCreateSheet = false
                }
            },
            onCreate = { title, fps, aspect, bgArgb, prompt, frameCount ->
                awaitingCreateClose = true
                onCreateProject(title, fps, aspect, bgArgb, prompt, frameCount)
            },
        )
    }

    if (showSettingsSheet) {
        AiSettingsSheet(
            initialApiKey = initialApiKey,
            initialModel = initialModel,
            onDismiss = { showSettingsSheet = false },
            onSave = { key, model ->
                onSaveAiSettings(key, model)
                cloudConfigured = key.isNotBlank()
                showSettingsSheet = false
            },
        )
    }
}

@Composable
private fun HomeHeaderBar(
    cloudConfigured: Boolean,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(IndigoAccent),
                )
                Text(
                    text = "FrameFlow",
                    style = MaterialTheme.typography.headlineMedium,
                    color = StudioTextPrimary,
                )
            }
            Text(
                text = "Pocket 2D flipbook studio",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary,
            )
        }

        Surface(
            onClick = onOpenSettings,
            shape = RoundedCornerShape(999.dp),
            color = if (cloudConfigured) MintAiContainer.copy(alpha = 0.7f) else StudioSurfaceElevated,
            border = BorderStroke(
                1.dp,
                if (cloudConfigured) MintAiAccent.copy(alpha = 0.5f) else StudioOutline,
            ),
            modifier = Modifier.testTag("home_ai_settings_btn"),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = StudioIcons.SparkleAi,
                    contentDescription = "AI Settings",
                    tint = if (cloudConfigured) MintAiAccent else StudioTextSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (cloudConfigured) "Gemini AI" else "On-Device AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (cloudConfigured) MintAiAccent else StudioTextPrimary,
                )
            }
        }
    }
}

@Composable
private fun EmptyProjectsState(
    modifier: Modifier = Modifier,
    onNewBlank: () -> Unit,
    onNewAi: () -> Unit,
) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = StudioSurface,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(IndigoAccentContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = StudioIcons.SparkleAi,
                        contentDescription = null,
                        tint = IndigoAccent,
                        modifier = Modifier.size(26.dp),
                    )
                }
                Text(
                    text = "No animations yet",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioTextPrimary,
                )
                Text(
                    text = "Sketch frame by frame with one thumb, or let AI build your base motion loop.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onNewAi,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MintAiAccent),
                    ) {
                        Text("Try AI Prompt")
                    }
                    Button(
                        onClick = onNewBlank,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IndigoAccent,
                            contentColor = ObsidianBg,
                        ),
                    ) {
                        Text("Blank Canvas")
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectCard(
    summary: ProjectSummary,
    animateThumbnail: Boolean,
    onClick: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var previewIndex by remember(summary.id, summary.previewFrames.size) { mutableIntStateOf(0) }

    LaunchedEffect(summary.id, summary.previewFrames.size, summary.fps, animateThumbnail) {
        if (!animateThumbnail || summary.previewFrames.size <= 1) return@LaunchedEffect
        val frameDelayMs = (1000L / summary.fps.coerceIn(2, 24)).coerceIn(45L, 450L)
        while (true) {
            delay(frameDelayMs)
            previewIndex = (previewIndex + 1) % summary.previewFrames.size
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = StudioSurface,
        border = BorderStroke(1.dp, StudioOutline),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("project_card_${summary.id}"),
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0E1118))
                    .border(1.dp, StudioOutline.copy(alpha = 0.6f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                val frame = summary.previewFrames.getOrNull(previewIndex)
                if (frame != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxSize(0.88f)
                            .aspectRatio(summary.aspectRatio.ratio)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        drawThumbnailFrame(
                            frame = frame,
                            layers = summary.layers,
                            canvasWidth = summary.aspectRatio.canvasWidth,
                            canvasHeight = summary.aspectRatio.canvasHeight,
                            backgroundColorArgb = summary.backgroundColorArgb,
                        )
                    }
                }

                // Subtle top-left aspect pill
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ObsidianBg.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        text = summary.aspectRatio.shortLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextSecondary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }

                // Bottom-right FPS badge
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = ObsidianBg.copy(alpha = 0.72f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp),
                ) {
                    Text(
                        text = "${summary.fps} FPS",
                        style = MaterialTheme.typography.labelSmall,
                        color = StudioTextPrimary,
                        modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = summary.title,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontSize = 14.sp,
                            lineHeight = 18.sp,
                        ),
                        color = StudioTextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "${summary.frameCount} frame${if (summary.frameCount == 1) "" else "s"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextMuted,
                        maxLines = 1,
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier
                            .size(32.dp)
                            .semantics { contentDescription = "Project options for ${summary.title}" },
                    ) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            repeat(3) {
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(StudioTextSecondary),
                                )
                            }
                        }
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = StudioSurfaceElevated,
                    ) {
                        DropdownMenuItem(
                            text = { Text("Duplicate", color = StudioTextPrimary) },
                            leadingIcon = {
                                Icon(
                                    StudioIcons.Duplicate,
                                    contentDescription = null,
                                    tint = StudioTextPrimary,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDuplicate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color(0xFFFF5C7A)) },
                            leadingIcon = {
                                Icon(
                                    StudioIcons.Trash,
                                    contentDescription = null,
                                    tint = Color(0xFFFF5C7A),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CreateProjectSheet(
    startInAiMode: Boolean,
    isCreating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (
        title: String,
        fps: Int,
        aspectRatio: AspectRatioPreset,
        backgroundColorArgb: Long,
        aiPrompt: String,
        aiFrameCount: Int,
    ) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var useAi by rememberSaveable(startInAiMode) { mutableStateOf(startInAiMode) }
    var title by rememberSaveable { mutableStateOf("") }
    var aiPrompt by rememberSaveable { mutableStateOf("") }
    var aiFramesFloat by rememberSaveable { mutableFloatStateOf(12f) }
    var selectedRatio by rememberSaveable { mutableStateOf(AspectRatioPreset.PORTRAIT_9_16) }
    var fpsFloat by rememberSaveable { mutableFloatStateOf(12f) }
    var bgArgb by rememberSaveable { mutableLongStateOf(PaperBackgroundSwatches.first().first) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Create Animation",
                style = MaterialTheme.typography.titleLarge,
                color = StudioTextPrimary,
            )

            // Mode switcher: Blank vs AI Starter
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(StudioSurfaceElevated)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ModeSegmentButton(
                    label = "Blank Canvas",
                    selected = !useAi,
                    accent = IndigoAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { useAi = false },
                )
                ModeSegmentButton(
                    label = "AI Starter ✦",
                    selected = useAi,
                    accent = MintAiAccent,
                    modifier = Modifier.weight(1f),
                    onClick = { useAi = true },
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (useAi) "Project title (optional)" else "Project title") },
                placeholder = { Text(if (useAi) "Auto-named from prompt" else "Untitled Flip") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_title_input"),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = IndigoAccent,
                    unfocusedBorderColor = StudioOutline,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary,
                ),
            )

            if (useAi) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = aiPrompt,
                        onValueChange = { aiPrompt = it },
                        label = { Text("Describe motion to animate") },
                        placeholder = { Text("e.g. Bouncing cyan ball or Stick character waving") },
                        minLines = 2,
                        maxLines = 3,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("create_ai_prompt_input"),
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MintAiAccent,
                            unfocusedBorderColor = StudioOutline,
                            focusedTextColor = StudioTextPrimary,
                            unfocusedTextColor = StudioTextPrimary,
                        ),
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        HomePromptSuggestions.forEach { suggestion ->
                            Surface(
                                onClick = { aiPrompt = suggestion },
                                shape = RoundedCornerShape(999.dp),
                                color = if (aiPrompt.equals(suggestion, ignoreCase = true)) {
                                    MintAiContainer
                                } else {
                                    StudioSurfaceElevated
                                },
                                border = BorderStroke(
                                    1.dp,
                                    if (aiPrompt.equals(suggestion, ignoreCase = true)) MintAiAccent else StudioOutline,
                                ),
                            ) {
                                Text(
                                    text = suggestion,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (aiPrompt.equals(suggestion, ignoreCase = true)) {
                                        MintAiAccent
                                    } else {
                                        StudioTextSecondary
                                    },
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Generated frames",
                            style = MaterialTheme.typography.labelLarge,
                            color = StudioTextSecondary,
                        )
                        Text(
                            text = "${aiFramesFloat.roundToInt()} frames",
                            style = MaterialTheme.typography.labelLarge,
                            color = MintAiAccent,
                        )
                    }
                    Slider(
                        value = aiFramesFloat,
                        onValueChange = { aiFramesFloat = it },
                        valueRange = 6f..24f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = MintAiAccent,
                            activeTrackColor = MintAiAccent,
                        ),
                    )
                }
            }

            // Aspect Ratio selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Canvas Format",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AspectRatioPreset.entries.forEach { preset ->
                        val selected = preset == selectedRatio
                        Surface(
                            onClick = { selectedRatio = preset },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) IndigoAccentContainer else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) IndigoAccent else StudioOutline),
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = preset.shortLabel,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (selected) IndigoAccent else StudioTextPrimary,
                                )
                                Text(
                                    text = preset.label.substringAfter(" "),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = StudioTextMuted,
                                    maxLines = 1,
                                )
                            }
                        }
                    }
                }
            }

            // FPS Selector
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Frame Rate",
                        style = MaterialTheme.typography.labelLarge,
                        color = StudioTextSecondary,
                    )
                    Text(
                        text = "${fpsFloat.roundToInt()} FPS",
                        style = MaterialTheme.typography.labelLarge,
                        color = IndigoAccent,
                    )
                }
                Slider(
                    value = fpsFloat,
                    onValueChange = { fpsFloat = it },
                    valueRange = 4f..24f,
                    colors = SliderDefaults.colors(
                        thumbColor = IndigoAccent,
                        activeTrackColor = IndigoAccent,
                    ),
                )
            }

            // Paper swatch selector
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Paper Tone",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PaperBackgroundSwatches.forEach { (swatchArgb, swatchName) ->
                        val selected = swatchArgb == bgArgb
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(Color(swatchArgb.toInt()))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) IndigoAccent else StudioOutline,
                                    shape = CircleShape,
                                )
                                .clickable { bgArgb = swatchArgb }
                                .semantics { contentDescription = swatchName },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onCreate(
                        title,
                        fpsFloat.roundToInt(),
                        selectedRatio,
                        bgArgb,
                        if (useAi) aiPrompt.ifBlank { "Bouncing ball" } else "",
                        aiFramesFloat.roundToInt(),
                    )
                },
                enabled = !isCreating,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("create_confirm_btn"),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (useAi) MintAiAccent else IndigoAccent,
                    contentColor = ObsidianBg,
                ),
            ) {
                if (isCreating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.2.dp,
                        color = ObsidianBg,
                    )
                } else {
                    Text(
                        text = if (useAi) "Generate & Open Studio ✦" else "Create Studio Canvas",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun ModeSegmentButton(
    label: String,
    selected: Boolean,
    accent: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = if (selected) StudioSurfaceHighest else Color.Transparent,
        border = if (selected) BorderStroke(1.dp, accent.copy(alpha = 0.5f)) else null,
    ) {
        Box(
            modifier = Modifier.padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) accent else StudioTextSecondary,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsSheet(
    initialApiKey: String,
    initialModel: String,
    onDismiss: () -> Unit,
    onSave: (apiKey: String, model: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val normalizedInitialModel = AiSettingsStore.normalizeModelId(initialModel).let { normalized ->
        normalized.takeIf { id -> AiSettingsStore.AVAILABLE_MODELS.any { it.first == id } }
            ?: AiSettingsStore.AVAILABLE_MODELS.first().first
    }
    var apiKey by rememberSaveable(initialApiKey) { mutableStateOf(initialApiKey) }
    var selectedModel by rememberSaveable(normalizedInitialModel) { mutableStateOf(normalizedInitialModel) }
    var revealKey by rememberSaveable { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = StudioIcons.SparkleAi,
                    contentDescription = null,
                    tint = MintAiAccent,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = "AI Engine & Gemini Key",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioTextPrimary,
                )
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = StudioSurfaceElevated,
                border = BorderStroke(1.dp, StudioOutline),
            ) {
                Text(
                    text = "On-Device AI always works offline (Motion Tween, Sketch Polish, Ghost Path, and Local Animation Synthesizer). Add an optional Gemini API key below to enable cloud multimodal generation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                    modifier = Modifier.padding(12.dp),
                )
            }

            OutlinedTextField(
                value = apiKey,
                onValueChange = { apiKey = it },
                label = { Text("Gemini API Key (optional)") },
                placeholder = { Text("AIza...") },
                singleLine = true,
                visualTransformation = if (revealKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    TextButton(onClick = { revealKey = !revealKey }) {
                        Text(if (revealKey) "Hide" else "Show", color = MintAiAccent)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MintAiAccent,
                    unfocusedBorderColor = StudioOutline,
                    focusedTextColor = StudioTextPrimary,
                    unfocusedTextColor = StudioTextPrimary,
                ),
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Cloud Model",
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                )
                AiSettingsStore.AVAILABLE_MODELS.forEach { (modelId, label) ->
                    val selected = modelId == selectedModel
                    Surface(
                        onClick = { selectedModel = modelId },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) MintAiContainer.copy(alpha = 0.65f) else StudioSurfaceElevated,
                        border = BorderStroke(1.dp, if (selected) MintAiAccent else StudioOutline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelLarge,
                                color = if (selected) MintAiAccent else StudioTextPrimary,
                            )
                            Text(
                                text = modelId,
                                style = MaterialTheme.typography.labelSmall,
                                color = StudioTextMuted,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        apiKey = ""
                        onSave("", selectedModel)
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, StudioOutline),
                ) {
                    Text("Use Offline Only", color = StudioTextSecondary)
                }
                Button(
                    onClick = { onSave(apiKey.trim(), selectedModel) },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MintAiAccent,
                        contentColor = ObsidianBg,
                    ),
                ) {
                    Text("Save Settings", fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
fun ProjectsHomePreview() {
    val layers = AnimationProject.defaultLayers()
    val bounce = LocalAnimationSynthesizer.synthesize(
        prompt = "Bouncing squash and stretch ball",
        aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
        inkLayerId = layers[1].id,
        colorLayerId = layers[0].id,
        frameCount = 8,
    )
    val rocket = LocalAnimationSynthesizer.synthesize(
        prompt = "Rocket launch",
        aspectRatio = AspectRatioPreset.SQUARE_1_1,
        inkLayerId = layers[1].id,
        colorLayerId = layers[0].id,
        frameCount = 8,
    )
    val previewSummaries = listOf(
        AnimationProject(
            id = "preview_bounce",
            title = "Squash & Bounce",
            fps = 12,
            aspectRatio = AspectRatioPreset.PORTRAIT_9_16,
            layers = layers,
            frames = bounce.frames,
        ).toSummary(),
        AnimationProject(
            id = "preview_rocket",
            title = "Rocket Launch",
            fps = 14,
            aspectRatio = AspectRatioPreset.SQUARE_1_1,
            backgroundColorArgb = 0xFF0B1F2A,
            layers = layers,
            frames = rocket.frames,
        ).toSummary(),
    )

    FrameFlowTheme {
        ProjectsHomeContent(
            state = ProjectsHomeUiState(projects = previewSummaries),
            hasCloudKey = false,
            animateThumbnails = false,
        )
    }
}
