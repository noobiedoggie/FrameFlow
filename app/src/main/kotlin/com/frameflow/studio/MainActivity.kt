package com.frameflow.studio

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.frameflow.studio.data.AiSettingsStore
import com.frameflow.studio.data.FileProjectRepository
import com.frameflow.studio.ui.theme.FrameFlowTheme
import com.frameflow.studio.ui.theme.ObsidianBg

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        val container = FrameFlowAppContainer(
            repository = FileProjectRepository(applicationContext),
            settingsStore = AiSettingsStore(applicationContext),
        )

        setContent {
            FrameFlowTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = ObsidianBg,
                ) {
                    FrameFlowNavigation(container = container)
                }
            }
        }
    }
}
