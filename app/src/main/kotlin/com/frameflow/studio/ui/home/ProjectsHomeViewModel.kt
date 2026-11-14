package com.frameflow.studio.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.data.ProjectRepository
import com.frameflow.studio.model.AnimationFrame
import com.frameflow.studio.model.AnimationProject
import com.frameflow.studio.model.AspectRatioPreset
import com.frameflow.studio.model.ProjectSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

data class ProjectsHomeUiState(
    val projects: List<ProjectSummary> = emptyList(),
    val isCreating: Boolean = false,
    val statusMessage: String? = null,
)

class ProjectsHomeViewModel(
    private val repository: ProjectRepository,
    private val aiService: AiAnimationService,
) : ViewModel() {

    private val isCreating = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<ProjectsHomeUiState> = combine(
        repository.summaries,
        isCreating,
        statusMessage,
    ) { list, creating, msg ->
        ProjectsHomeUiState(
            projects = list,
            isCreating = creating,
            statusMessage = msg,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ProjectsHomeUiState())

    init {
        viewModelScope.launch {
            repository.refresh()
        }
    }

    fun createProject(
        title: String,
        fps: Int,
        aspectRatio: AspectRatioPreset,
        backgroundColorArgb: Long,
        aiPrompt: String = "",
        aiFrameCount: Int = 12,
        onCreated: (String) -> Unit,
    ) {
        if (isCreating.value) return
        isCreating.value = true
        viewModelScope.launch {
            try {
                val layers = AnimationProject.defaultLayers()
                val trimmedPrompt = aiPrompt.trim()
                val project = if (trimmedPrompt.isNotEmpty()) {
                    val generated = aiService.generateFrames(
                        prompt = trimmedPrompt,
                        frameCount = aiFrameCount,
                        aspectRatio = aspectRatio,
                        inkLayerId = layers[1].id,
                        colorLayerId = layers[0].id,
                    )
                    if (generated.summary.contains("fallback", ignoreCase = true)) {
                        statusMessage.value = generated.summary
                    }
                    AnimationProject(
                        title = title.trim().ifBlank { generated.title },
                        fps = fps.coerceIn(1, 30),
                        aspectRatio = aspectRatio,
                        backgroundColorArgb = backgroundColorArgb,
                        layers = layers,
                        frames = generated.frames,
                    )
                } else {
                    AnimationProject(
                        title = title.trim().ifBlank { "Untitled Flip" },
                        fps = fps.coerceIn(1, 30),
                        aspectRatio = aspectRatio,
                        backgroundColorArgb = backgroundColorArgb,
                        layers = layers,
                        frames = listOf(AnimationFrame()),
                    )
                }
                repository.saveProject(project)
                onCreated(project.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                statusMessage.value = "Could not create flipbook: ${e.message ?: "Unknown error"}"
            } finally {
                isCreating.value = false
            }
        }
    }

    fun duplicateProject(id: String) {
        viewModelScope.launch {
            repository.duplicateProject(id)
            statusMessage.value = "Project duplicated"
        }
    }

    fun deleteProject(id: String) {
        viewModelScope.launch {
            repository.deleteProject(id)
            statusMessage.value = "Project deleted"
        }
    }

    fun clearStatus() {
        statusMessage.update { null }
    }
}
