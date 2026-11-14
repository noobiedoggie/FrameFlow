package com.frameflow.studio

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.frameflow.studio.ai.AiAnimationService
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.data.FileProjectRepository
import com.frameflow.studio.data.ProjectRepository
import com.frameflow.studio.ui.home.ProjectsHomeScreen
import com.frameflow.studio.ui.home.ProjectsHomeViewModel
import com.frameflow.studio.ui.studio.StudioScreen
import com.frameflow.studio.ui.studio.StudioViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class FrameFlowAppContainer(
    val repository: ProjectRepository,
    val settingsStore: AiSettingsStore,
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) {
    val aiService = AiAnimationService(
        getApiKey = { settingsStore.settings.value.apiKey },
        getModel = { settingsStore.settings.value.model },
    )
}

@Composable
fun FrameFlowNavigation(container: FrameFlowAppContainer? = null) {
    val context = LocalContext.current
    val appContainer = container ?: remember(context.applicationContext) {
        FrameFlowAppContainer(
            repository = FileProjectRepository(context.applicationContext),
            settingsStore = AiSettingsStore(context.applicationContext),
        )
    }

    val aiSettings by appContainer.settingsStore.settings.collectAsStateWithLifecycle()
    val backStack = rememberNavBackStack(ProjectsHome)

    NavDisplay(
        backStack = backStack,
        onBack = { backStack.removeLastOrNull() },
        entryProvider = entryProvider {
            entry<ProjectsHome> {
                val homeVm: ProjectsHomeViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            ProjectsHomeViewModel(
                                repository = appContainer.repository,
                                aiService = appContainer.aiService,
                            ) as T
                    },
                )
                ProjectsHomeScreen(
                    viewModel = homeVm,
                    initialApiKey = aiSettings.apiKey,
                    initialModel = aiSettings.model,
                    onSaveAiSettings = { key, model ->
                        appContainer.settingsStore.updateSettings(key, model)
                    },
                    onOpenProject = { projectId ->
                        backStack.add(StudioRoute(projectId))
                    },
                )
            }

            entry<StudioRoute> { route ->
                val studioVm: StudioViewModel = viewModel(
                    key = "studio_${route.projectId}",
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T =
                            StudioViewModel(
                                projectId = route.projectId,
                                repository = appContainer.repository,
                                aiService = appContainer.aiService,
                                persistScope = appContainer.appScope,
                            ) as T
                    },
                )
                StudioScreen(
                    viewModel = studioVm,
                    initialApiKey = aiSettings.apiKey,
                    initialModel = aiSettings.model,
                    onSaveAiSettings = { key, model ->
                        appContainer.settingsStore.updateSettings(key, model)
                    },
                    onBack = {
                        backStack.removeLastOrNull()
                    },
                )
            }
        },
    )
}
