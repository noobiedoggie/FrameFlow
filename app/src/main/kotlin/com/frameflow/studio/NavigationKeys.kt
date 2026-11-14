package com.frameflow.studio

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
data object ProjectsHome : NavKey

@Serializable
data class StudioRoute(val projectId: String) : NavKey
