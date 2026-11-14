package com.frameflow.studio.ui.studio

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.frameflow.studio.ai.GeminiCoachTip
import com.frameflow.studio.export.ExportFormat
import com.frameflow.studio.export.ExportManager
import com.frameflow.studio.export.ExportResult
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.GuideSettings
import com.frameflow.studio.model.LayerMeta
import com.frameflow.studio.model.OnionSkinSettings
import com.frameflow.studio.model.TweenEasing
import com.frameflow.studio.ui.components.StudioIcons
import com.frameflow.studio.ui.theme.CoralAccent
import com.frameflow.studio.ui.theme.DefaultCanvasSwatches
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
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt
import android.graphics.Color as AndroidColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPaletteSheet(
    currentColorArgb: Long,
    onSelectColor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        ColorPaletteSheetContent(
            currentColorArgb = currentColorArgb,
            onSelectColor = onSelectColor,
            onDone = onDismiss,
        )
    }
}

@Composable
fun ColorPaletteSheetContent(
    currentColorArgb: Long,
    onSelectColor: (Long) -> Unit,
    onDone: () -> Unit,
) {
    val hsv = remember(currentColorArgb) {
        FloatArray(3).also {
            AndroidColor.colorToHSV(currentColorArgb.toInt(), it)
        }
    }
    var hue by remember(currentColorArgb) { mutableFloatStateOf(hsv[0]) }
    var sat by remember(currentColorArgb) { mutableFloatStateOf(hsv[1]) }
    var value by remember(currentColorArgb) { mutableFloatStateOf(hsv[2]) }

    fun pushHsv() {
        val rgb = AndroidColor.HSVToColor(floatArrayOf(hue, sat, value))
        onSelectColor((0xFF000000L or (rgb.toLong() and 0x00FFFFFFL)))
    }

    val previewColor = Color(currentColorArgb.toInt())
    val hexString = String.format(Locale.US, "#%06X", (currentColorArgb and 0xFFFFFFL).toInt())

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ink & Fill Color",
                style = MaterialTheme.typography.titleLarge,
                color = StudioTextPrimary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(previewColor)
                        .border(1.5.dp, StudioOutline, CircleShape),
                )
                Text(
                    text = hexString,
                    style = MaterialTheme.typography.labelLarge,
                    color = StudioTextSecondary,
                )
            }
        }

        // Quick swatches (2 rows x 5)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            DefaultCanvasSwatches.chunked(5).forEach { rowSwatches ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    rowSwatches.forEach { swatchArgb ->
                        val selected = (swatchArgb and 0xFFFFFFL) == (currentColorArgb and 0xFFFFFFL)
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(Color(swatchArgb.toInt()))
                                .border(
                                    width = if (selected) 3.dp else 1.dp,
                                    color = if (selected) IndigoAccent else StudioOutline,
                                    shape = CircleShape,
                                )
                                .clickable { onSelectColor(swatchArgb) },
                        )
                    }
                }
            }
        }

        // HSV Custom Tuning
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SliderLabelRow("Hue", "${hue.roundToInt()}°")
            Slider(
                value = hue,
                onValueChange = {
                    hue = it
                    pushHsv()
                },
                valueRange = 0f..360f,
                colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = IndigoAccent),
            )

            SliderLabelRow("Saturation", "${(sat * 100f).roundToInt()}%")
            Slider(
                value = sat,
                onValueChange = {
                    sat = it
                    pushHsv()
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = IndigoAccent),
            )

            SliderLabelRow("Brightness", "${(value * 100f).roundToInt()}%")
            Slider(
                value = value,
                onValueChange = {
                    value = it
                    pushHsv()
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(thumbColor = previewColor, activeTrackColor = IndigoAccent),
            )
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = ObsidianBg),
        ) {
            Text("Done", fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

private enum class AiSheetTab(val label: String) {
    TWEEN("In-Between"),
    GENERATE("Prompt"),
    ASSIST("Polish & Ghost"),
    COACH("Coach"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantSheet(
    state: StudioUiState,
    hasCloudKey: Boolean,
    onRunTween: (count: Int, easing: TweenEasing, towardNext: Boolean) -> Unit,
    onRunGenerate: (prompt: String, frameCount: Int, replaceBlank: Boolean, useCurrentSketch: Boolean) -> Unit,
    onRunPolish: (snapShapes: Boolean) -> Unit,
    onToggleGhost: () -> Unit,
    onStampGhost: () -> Unit,
    onAskCoach: (String) -> Unit,
    onApplyFps: (Int) -> Unit = {},
    onOpenAiSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        AiAssistantSheetContent(
            state = state,
            hasCloudKey = hasCloudKey,
            onRunTween = { count, easing, next ->
                onRunTween(count, easing, next)
                onDismiss()
            },
            onRunGenerate = { prompt, frames, replace, sketch ->
                onRunGenerate(prompt, frames, replace, sketch)
                onDismiss()
            },
            onRunPolish = { snap ->
                onRunPolish(snap)
                onDismiss()
            },
            onToggleGhost = onToggleGhost,
            onStampGhost = {
                onStampGhost()
                onDismiss()
            },
            onAskCoach = onAskCoach,
            onApplyFps = onApplyFps,
            onOpenAiSettings = onOpenAiSettings,
        )
    }
}

@Composable
fun AiAssistantSheetContent(
    state: StudioUiState,
    hasCloudKey: Boolean,
    onRunTween: (count: Int, easing: TweenEasing, towardNext: Boolean) -> Unit,
    onRunGenerate: (prompt: String, frameCount: Int, replaceBlank: Boolean, useCurrentSketch: Boolean) -> Unit,
    onRunPolish: (snapShapes: Boolean) -> Unit,
    onToggleGhost: () -> Unit,
    onStampGhost: () -> Unit,
    onAskCoach: (String) -> Unit,
    onApplyFps: (Int) -> Unit = {},
    onOpenAiSettings: () -> Unit,
) {
    var activeTab by rememberSaveable { mutableStateOf(AiSheetTab.TWEEN) }

    // Tween options
    var tweenCountFloat by rememberSaveable { mutableFloatStateOf(3f) }
    var tweenEasing by rememberSaveable { mutableStateOf(TweenEasing.EASE_IN_OUT) }
    var tweenTowardNext by rememberSaveable { mutableStateOf(true) }

    // Generate options
    var prompt by rememberSaveable { mutableStateOf("") }
    var genFrameCountFloat by rememberSaveable { mutableFloatStateOf(12f) }
    var replaceBlankProject by rememberSaveable { mutableStateOf(true) }
    var useCurrentSketch by rememberSaveable { mutableStateOf(false) }

    // Polish options
    var snapShapes by rememberSaveable { mutableStateOf(true) }

    // Coach options
    var coachQuestion by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("ai_assistant_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = StudioIcons.SparkleAi,
                    contentDescription = null,
                    tint = MintAiAccent,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = "AI Studio Copilot",
                    style = MaterialTheme.typography.titleLarge,
                    color = StudioTextPrimary,
                )
            }

            Surface(
                onClick = onOpenAiSettings,
                shape = RoundedCornerShape(999.dp),
                color = if (hasCloudKey) MintAiContainer else StudioSurfaceElevated,
                border = BorderStroke(1.dp, if (hasCloudKey) MintAiAccent else StudioOutline),
            ) {
                Text(
                    text = if (hasCloudKey) "Gemini Cloud" else "Offline AI · Key",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasCloudKey) MintAiAccent else StudioTextSecondary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        // Segmented tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(StudioSurfaceElevated)
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AiSheetTab.entries.forEach { tab ->
                val selected = tab == activeTab
                Surface(
                    onClick = { activeTab = tab },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("ai_tab_${tab.name.lowercase()}"),
                    shape = RoundedCornerShape(10.dp),
                    color = if (selected) StudioSurfaceHighest else Color.Transparent,
                    border = if (selected) BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.55f)) else null,
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = tab.label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MintAiAccent else StudioTextSecondary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        if (state.isAiBusy) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MintAiContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MintAiAccent,
                    )
                    Text(
                        text = "AI is building frames...",
                        style = MaterialTheme.typography.labelLarge,
                        color = MintAiAccent,
                    )
                }
            }
        }

        when (activeTab) {
            AiSheetTab.TWEEN -> {
                val frameCount = state.project?.frames?.size ?: 0
                Text(
                    text = "Generate smooth vector transitions between two keyframes without redrawing every frame.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = StudioTextSecondary,
                )

                SliderLabelRow("Intermediate frames", "${tweenCountFloat.roundToInt()} frames")
                Slider(
                    value = tweenCountFloat,
                    onValueChange = { tweenCountFloat = it },
                    valueRange = 1f..6f,
                    steps = 4,
                    colors = SliderDefaults.colors(thumbColor = MintAiAccent, activeTrackColor = MintAiAccent),
                )

                Text("Motion curve", style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TweenEasing.entries.forEach { easing ->
                        val selected = easing == tweenEasing
                        Surface(
                            onClick = { tweenEasing = easing },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) MintAiContainer else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (selected) MintAiAccent else StudioOutline),
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 9.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = easing.displayName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) MintAiAccent else StudioTextPrimary,
                                )
                            }
                        }
                    }
                }

                if (frameCount >= 2) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val hasNext = state.currentFrameIndex < frameCount - 1
                        val hasPrev = state.currentFrameIndex > 0
                        Surface(
                            onClick = { if (hasNext) tweenTowardNext = true },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (tweenTowardNext && hasNext) IndigoAccentContainer else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (tweenTowardNext && hasNext) IndigoAccent else StudioOutline),
                        ) {
                            Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Current → Next",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasNext) StudioTextPrimary else StudioTextMuted,
                                )
                            }
                        }
                        Surface(
                            onClick = { if (hasPrev) tweenTowardNext = false },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(12.dp),
                            color = if (!tweenTowardNext && hasPrev) IndigoAccentContainer else StudioSurfaceElevated,
                            border = BorderStroke(1.dp, if (!tweenTowardNext && hasPrev) IndigoAccent else StudioOutline),
                        ) {
                            Box(modifier = Modifier.padding(vertical = 9.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    text = "Prev → Current",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (hasPrev) StudioTextPrimary else StudioTextMuted,
                                )
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        onRunTween(tweenCountFloat.roundToInt(), tweenEasing, tweenTowardNext)
                    },
                    enabled = !state.isAiBusy && frameCount >= 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_run_tween_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintAiAccent, contentColor = ObsidianBg),
                ) {
                    Text(
                        text = if (frameCount >= 2) {
                            "Insert ${tweenCountFloat.roundToInt()} In-Betweens ✦"
                        } else {
                            "Draw at least 2 frames first"
                        },
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            AiSheetTab.GENERATE -> {
                OutlinedTextField(
                    value = prompt,
                    onValueChange = { prompt = it },
                    label = { Text("What should animate?") },
                    placeholder = { Text("e.g. Rocket launch, Beating heart, Stick jump") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("ai_studio_prompt_input"),
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
                    listOf("Bouncing ball", "Rocket launch", "Beating heart", "Stick dance", "Campfire", "Blinking cat").forEach { chip ->
                        Surface(
                            onClick = { prompt = chip },
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioOutline),
                        ) {
                            Text(
                                text = chip,
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioTextSecondary,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                SliderLabelRow("Generated frames", "${genFrameCountFloat.roundToInt()} frames")
                Slider(
                    value = genFrameCountFloat,
                    onValueChange = { genFrameCountFloat = it },
                    valueRange = 6f..24f,
                    steps = 17,
                    colors = SliderDefaults.colors(thumbColor = MintAiAccent, activeTrackColor = MintAiAccent),
                )

                SettingSwitchRow(
                    title = "Continue from current sketch",
                    subtitle = if (hasCloudKey) {
                        "Sends current canvas frame to Gemini Vision"
                    } else {
                        "Uses current drawing context cleanly"
                    },
                    checked = useCurrentSketch,
                    onCheckedChange = { useCurrentSketch = it },
                    accent = MintAiAccent,
                )

                SettingSwitchRow(
                    title = "Replace blank canvas",
                    subtitle = "Uncheck to insert generated frames after current frame",
                    checked = replaceBlankProject,
                    onCheckedChange = { replaceBlankProject = it },
                    accent = MintAiAccent,
                )

                Button(
                    onClick = {
                        onRunGenerate(
                            prompt.ifBlank { "Bouncing ball" },
                            genFrameCountFloat.roundToInt(),
                            replaceBlankProject,
                            useCurrentSketch,
                        )
                    },
                    enabled = !state.isAiBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_run_generate_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintAiAccent, contentColor = ObsidianBg),
                ) {
                    Text("Generate Editable Frames ✦", fontWeight = FontWeight.Bold)
                }
            }

            AiSheetTab.ASSIST -> {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "AI Sketch Polish & Shape Snap",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                        )
                        Text(
                            text = "Smooths jittery finger strokes and optionally snaps rough circles and lines into clean geometric shapes.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                        )
                        SettingSwitchRow(
                            title = "Snap rough circles & lines",
                            subtitle = "Turns closed loops into crisp circles/ovals",
                            checked = snapShapes,
                            onCheckedChange = { snapShapes = it },
                            accent = MintAiAccent,
                        )
                        Button(
                            onClick = { onRunPolish(snapShapes) },
                            enabled = !state.isAiBusy,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("ai_run_polish_btn"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MintAiAccent, contentColor = ObsidianBg),
                        ) {
                            Text("Polish Active Layer ✦", fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = StudioSurfaceElevated,
                    border = BorderStroke(1.dp, StudioOutline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = "AI Ghost Trajectory",
                            style = MaterialTheme.typography.titleMedium,
                            color = StudioTextPrimary,
                        )
                        Text(
                            text = "Predicts where your drawing should land next based on motion across recent frames.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = onToggleGhost,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (state.showAiGhost) MintAiAccent else StudioOutline,
                                ),
                            ) {
                                Text(
                                    text = if (state.showAiGhost) "Ghost: ON" else "Show Ghost",
                                    color = if (state.showAiGhost) MintAiAccent else StudioTextPrimary,
                                )
                            }
                            Button(
                                onClick = onStampGhost,
                                enabled = state.aiGhostStrokes.isNotEmpty(),
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MintAiAccent,
                                    contentColor = ObsidianBg,
                                ),
                            ) {
                                Text("Stamp Next Frame", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            AiSheetTab.COACH -> {
                OutlinedTextField(
                    value = coachQuestion,
                    onValueChange = { coachQuestion = it },
                    label = { Text("Ask AI Animation Coach") },
                    placeholder = { Text("e.g. How can I make this bounce feel heavier?") },
                    minLines = 2,
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth(),
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
                    listOf("Check timing & FPS", "How to add squash?", "Make loop smoother").forEach { q ->
                        Surface(
                            onClick = { coachQuestion = q },
                            shape = RoundedCornerShape(999.dp),
                            color = StudioSurfaceElevated,
                            border = BorderStroke(1.dp, StudioOutline),
                        ) {
                            Text(
                                text = q,
                                style = MaterialTheme.typography.labelMedium,
                                color = StudioTextSecondary,
                                modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                val tip: GeminiCoachTip? = state.coachTip
                if (tip != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MintAiContainer.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, MintAiAccent.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tip.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MintAiAccent,
                                )
                                val currentFps = state.project?.fps
                                if (currentFps != null && tip.recommendedFps != currentFps) {
                                    Surface(
                                        onClick = { onApplyFps(tip.recommendedFps) },
                                        shape = RoundedCornerShape(999.dp),
                                        color = MintAiAccent.copy(alpha = 0.18f),
                                        border = BorderStroke(1.dp, MintAiAccent),
                                        modifier = Modifier.testTag("ai_apply_fps_btn"),
                                    ) {
                                        Text(
                                            text = "Apply ${tip.recommendedFps} FPS",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MintAiAccent,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                        )
                                    }
                                } else {
                                    Text(
                                        text = "${tip.recommendedFps} FPS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = StudioTextPrimary,
                                    )
                                }
                            }
                            Text(
                                text = tip.advice,
                                style = MaterialTheme.typography.bodyMedium,
                                color = StudioTextPrimary,
                            )
                        }
                    }
                }

                Button(
                    onClick = { onAskCoach(coachQuestion) },
                    enabled = !state.isAiBusy,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("ai_ask_coach_btn"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MintAiAccent, contentColor = ObsidianBg),
                ) {
                    Text("Get Coach Critique ✦", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayersAndGuidesSheet(
    project: AnimationProject,
    activeLayerId: String,
    onionSkin: OnionSkinSettings,
    guides: GuideSettings,
    onSelectLayer: (String) -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onToggleLayerLock: (String) -> Unit,
    onSetLayerOpacity: (String, Float) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (String) -> Unit,
    onUpdateOnionSkin: (OnionSkinSettings) -> Unit,
    onUpdateGuides: (GuideSettings) -> Unit,
    onUpdateFps: (Int) -> Unit,
    onSetBackgroundColor: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        LayersAndGuidesSheetContent(
            project = project,
            activeLayerId = activeLayerId,
            onionSkin = onionSkin,
            guides = guides,
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
        )
    }
}

@Composable
fun LayersAndGuidesSheetContent(
    project: AnimationProject,
    activeLayerId: String,
    onionSkin: OnionSkinSettings,
    guides: GuideSettings,
    onSelectLayer: (String) -> Unit,
    onToggleLayerVisibility: (String) -> Unit,
    onToggleLayerLock: (String) -> Unit,
    onSetLayerOpacity: (String, Float) -> Unit,
    onAddLayer: () -> Unit,
    onDeleteLayer: (String) -> Unit,
    onUpdateOnionSkin: (OnionSkinSettings) -> Unit,
    onUpdateGuides: (GuideSettings) -> Unit,
    onUpdateFps: (Int) -> Unit,
    onSetBackgroundColor: (Long) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("layers_guides_sheet"),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "Stage, Layers & Onion Skin",
            style = MaterialTheme.typography.titleLarge,
            color = StudioTextPrimary,
        )

        // Onion Skin section
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StudioSurfaceElevated,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingSwitchRow(
                    title = "Onion Skinning",
                    subtitle = "Red = previous pose, Mint = next pose",
                    checked = onionSkin.enabled,
                    onCheckedChange = { onUpdateOnionSkin(onionSkin.copy(enabled = it)) },
                    accent = IndigoAccent,
                )
                if (onionSkin.enabled) {
                    SliderLabelRow("Prev frames", "${onionSkin.prevFrames}")
                    Slider(
                        value = onionSkin.prevFrames.toFloat(),
                        onValueChange = { onUpdateOnionSkin(onionSkin.copy(prevFrames = it.roundToInt())) },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = CoralAccent, activeTrackColor = CoralAccent),
                    )

                    SliderLabelRow("Next frames", "${onionSkin.nextFrames}")
                    Slider(
                        value = onionSkin.nextFrames.toFloat(),
                        onValueChange = { onUpdateOnionSkin(onionSkin.copy(nextFrames = it.roundToInt())) },
                        valueRange = 0f..3f,
                        steps = 2,
                        colors = SliderDefaults.colors(thumbColor = MintAiAccent, activeTrackColor = MintAiAccent),
                    )

                    SliderLabelRow("Ghost opacity", "${(onionSkin.opacity * 100f).roundToInt()}%")
                    Slider(
                        value = onionSkin.opacity,
                        onValueChange = { onUpdateOnionSkin(onionSkin.copy(opacity = it)) },
                        valueRange = 0.08f..0.65f,
                        colors = SliderDefaults.colors(thumbColor = IndigoAccent, activeTrackColor = IndigoAccent),
                    )
                }
            }
        }

        // Layers section
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Layers (${project.layers.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = StudioTextPrimary,
                )
                Surface(
                    onClick = onAddLayer,
                    shape = RoundedCornerShape(999.dp),
                    color = IndigoAccentContainer,
                    border = BorderStroke(1.dp, IndigoAccent.copy(alpha = 0.6f)),
                    modifier = Modifier.testTag("add_layer_btn"),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(StudioIcons.Plus, contentDescription = null, tint = IndigoAccent, modifier = Modifier.size(14.dp))
                        Text("Add Layer", style = MaterialTheme.typography.labelMedium, color = IndigoAccent)
                    }
                }
            }

            project.layers.reversed().forEach { layer: LayerMeta ->
                val isActive = layer.id == activeLayerId
                Surface(
                    onClick = { onSelectLayer(layer.id) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isActive) IndigoAccentContainer.copy(alpha = 0.55f) else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (isActive) IndigoAccent else StudioOutline),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("layer_item_${layer.id}"),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (isActive) IndigoAccent else StudioTextMuted),
                                )
                                Text(
                                    text = layer.name,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StudioTextPrimary,
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Surface(
                                    onClick = { onToggleLayerVisibility(layer.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (layer.visible) StudioSurfaceHighest else ObsidianBg,
                                    border = BorderStroke(1.dp, StudioOutline),
                                ) {
                                    Text(
                                        text = if (layer.visible) "Shown" else "Hidden",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (layer.visible) StudioTextPrimary else StudioTextMuted,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                Surface(
                                    onClick = { onToggleLayerLock(layer.id) },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (layer.locked) CoralAccent.copy(alpha = 0.2f) else StudioSurfaceHighest,
                                    border = BorderStroke(1.dp, if (layer.locked) CoralAccent else StudioOutline),
                                ) {
                                    Text(
                                        text = if (layer.locked) "Locked" else "Open",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (layer.locked) CoralAccent else StudioTextSecondary,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    )
                                }
                                if (project.layers.size > 1) {
                                    IconButton(
                                        onClick = { onDeleteLayer(layer.id) },
                                        modifier = Modifier.size(28.dp),
                                    ) {
                                        Icon(
                                            imageVector = StudioIcons.Trash,
                                            contentDescription = "Delete layer ${layer.name}",
                                            tint = CoralAccent,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                        }

                        if (isActive) {
                            Spacer(modifier = Modifier.height(6.dp))
                            SliderLabelRow("Layer opacity", "${(layer.opacity * 100f).roundToInt()}%")
                            Slider(
                                value = layer.opacity,
                                onValueChange = { onSetLayerOpacity(layer.id, it) },
                                valueRange = 0.1f..1f,
                                colors = SliderDefaults.colors(thumbColor = IndigoAccent, activeTrackColor = IndigoAccent),
                            )
                        }
                    }
                }
            }
        }

        // Guides & Stage settings
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = StudioSurfaceElevated,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                SettingSwitchRow(
                    title = "Rule-of-Thirds Grid",
                    subtitle = "Subtle framing lines on canvas",
                    checked = guides.showGrid,
                    onCheckedChange = { onUpdateGuides(guides.copy(showGrid = it)) },
                    accent = IndigoAccent,
                )
                SettingSwitchRow(
                    title = "Horizontal Symmetry Mirror",
                    subtitle = "Draws both left and right sides in real time",
                    checked = guides.mirrorHorizontal,
                    onCheckedChange = { onUpdateGuides(guides.copy(mirrorHorizontal = it)) },
                    accent = IndigoAccent,
                )
                SettingSwitchRow(
                    title = "Auto-Snap Rough Shapes",
                    subtitle = "Cleanly snaps hand-drawn circles & lines as you lift",
                    checked = guides.autoSnapShapes,
                    onCheckedChange = { onUpdateGuides(guides.copy(autoSnapShapes = it)) },
                    accent = MintAiAccent,
                )

                SliderLabelRow("Playback speed", "${project.fps} FPS")
                Slider(
                    value = project.fps.toFloat(),
                    onValueChange = { onUpdateFps(it.roundToInt()) },
                    valueRange = 1f..30f,
                    colors = SliderDefaults.colors(thumbColor = IndigoAccent, activeTrackColor = IndigoAccent),
                )

                Text("Paper Tone", style = MaterialTheme.typography.labelLarge, color = StudioTextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PaperBackgroundSwatches.forEach { (swatchArgb, _) ->
                        val selected = swatchArgb == project.backgroundColorArgb
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape)
                                .background(Color(swatchArgb.toInt()))
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = if (selected) IndigoAccent else StudioOutline,
                                    shape = CircleShape,
                                )
                                .clickable { onSetBackgroundColor(swatchArgb) },
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(
    project: AnimationProject,
    currentFrameIndex: Int,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var selectedFormat by rememberSaveable { mutableStateOf(ExportFormat.GIF) }
    var maxDimension by rememberSaveable { mutableIntStateOf(720) }
    var saveToGallery by rememberSaveable { mutableStateOf(true) }
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var lastResult by remember { mutableStateOf<ExportResult?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = StudioSurface,
        contentColor = StudioTextPrimary,
    ) {
        ExportSheetContent(
            project = project,
            selectedFormat = selectedFormat,
            maxDimension = maxDimension,
            saveToGallery = saveToGallery,
            isExporting = isExporting,
            exportError = exportError,
            lastResult = lastResult,
            onSelectFormat = {
                selectedFormat = it
                lastResult = null
                exportError = null
            },
            onSelectResolution = {
                maxDimension = it
                lastResult = null
            },
            onToggleSaveToGallery = { saveToGallery = it },
            onExportClick = {
                if (isExporting) return@ExportSheetContent
                scope.launch {
                    isExporting = true
                    exportError = null
                    runCatching {
                        ExportManager.exportProject(
                            context = context,
                            project = project,
                            format = selectedFormat,
                            currentFrameIndex = currentFrameIndex,
                            maxDimension = maxDimension,
                            saveToGallery = saveToGallery,
                        )
                    }.onSuccess { result ->
                        lastResult = result
                    }.onFailure { err ->
                        exportError = err.message ?: "Export failed"
                    }
                    isExporting = false
                }
            },
            onShareClick = { res ->
                ExportManager.launchShareSheet(context, res)
            },
        )
    }
}

@Composable
fun ExportSheetContent(
    project: AnimationProject,
    selectedFormat: ExportFormat,
    maxDimension: Int,
    saveToGallery: Boolean,
    isExporting: Boolean,
    exportError: String?,
    lastResult: ExportResult?,
    onSelectFormat: (ExportFormat) -> Unit,
    onSelectResolution: (Int) -> Unit,
    onToggleSaveToGallery: (Boolean) -> Unit,
    onExportClick: () -> Unit,
    onShareClick: (ExportResult) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("export_sheet"),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = "Render & Export",
            style = MaterialTheme.typography.titleLarge,
            color = StudioTextPrimary,
        )

        Surface(
            shape = RoundedCornerShape(14.dp),
            color = StudioSurfaceElevated,
            border = BorderStroke(1.dp, StudioOutline),
            modifier = Modifier.fillMaxWidth(),
        ) {
            val drawingWord = if (project.frames.size == 1) "drawing" else "drawings"
            val exposureWord = if (project.totalExposureFrames == 1) "exposure" else "exposures"
            Text(
                text = "${project.frames.size} $drawingWord · ${project.totalExposureFrames} $exposureWord · ${project.fps} FPS · ${
                    String.format(Locale.US, "%.1fs", project.durationSeconds)
                }",
                style = MaterialTheme.typography.bodyMedium,
                color = StudioTextSecondary,
                modifier = Modifier.padding(12.dp),
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ExportFormat.entries.forEach { format ->
                val selected = format == selectedFormat
                val subtitle = when (format) {
                    ExportFormat.GIF -> "Looping animation ideal for social & chats"
                    ExportFormat.MP4 -> "Hardware-encoded H.264 video"
                    ExportFormat.PNG -> "High-resolution single frame still"
                }
                Surface(
                    onClick = { onSelectFormat(format) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selected) IndigoAccentContainer.copy(alpha = 0.65f) else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) IndigoAccent else StudioOutline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = format.label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) IndigoAccent else StudioTextPrimary,
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = StudioTextSecondary,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(720 to "720p Fast", 1080 to "1080p HD").forEach { (res, label) ->
                val selected = maxDimension == res
                Surface(
                    onClick = { onSelectResolution(res) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    color = if (selected) IndigoAccentContainer else StudioSurfaceElevated,
                    border = BorderStroke(1.dp, if (selected) IndigoAccent else StudioOutline),
                ) {
                    Box(modifier = Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (selected) IndigoAccent else StudioTextPrimary,
                        )
                    }
                }
            }
        }

        SettingSwitchRow(
            title = "Save copy to Photos / Movies",
            subtitle = "Creates a shareable copy in device gallery",
            checked = saveToGallery,
            onCheckedChange = onToggleSaveToGallery,
            accent = IndigoAccent,
        )

        if (exportError != null) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = CoralAccent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, CoralAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = exportError,
                    style = MaterialTheme.typography.bodyMedium,
                    color = CoralAccent,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        if (lastResult != null) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MintAiContainer.copy(alpha = 0.6f),
                border = BorderStroke(1.dp, MintAiAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Exported ${lastResult.file.name}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MintAiAccent,
                    )
                    Text(
                        text = if (lastResult.savedToGallery) "Saved to gallery & ready to share." else "Ready to share.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = StudioTextPrimary,
                    )
                    Button(
                        onClick = { onShareClick(lastResult) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MintAiAccent, contentColor = ObsidianBg),
                    ) {
                        Text("Share ${lastResult.format.extension.uppercase()}", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        Button(
            onClick = onExportClick,
            enabled = !isExporting,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .testTag("export_confirm_btn"),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = IndigoAccent, contentColor = ObsidianBg),
        ) {
            if (isExporting) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = ObsidianBg)
            } else {
                Text("Render ${selectedFormat.extension.uppercase()}", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun SliderLabelRow(title: String, valueText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.labelMedium, color = StudioTextSecondary)
        Text(text = valueText, style = MaterialTheme.typography.labelMedium, color = StudioTextPrimary)
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    accent: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.labelLarge, color = StudioTextPrimary)
            Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = StudioTextMuted)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = ObsidianBg,
                checkedTrackColor = accent,
                uncheckedThumbColor = StudioTextSecondary,
                uncheckedTrackColor = StudioSurfaceHighest,
            ),
        )
    }
}
