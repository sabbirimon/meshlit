package com.meshlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.meshlit.core.common.logger
import com.meshlit.ui.MeshlitApp
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import com.meshlit.ui.theme.MeshlitTheme

class MainActivity : ComponentActivity() {
    private val log = logger("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        log.info("activity.create", "MainActivity onCreate")
        val app = application as MeshlitApplication
        setContent {
            // Bind the live theme config to the CompositionLocal so any
            // control in the Settings panel that writes to DataStore
            // immediately re-themes the whole UI.
            val config by app.settingsRepository.flow.collectAsState(
                initial = com.meshlit.ui.theme.MeshlitThemeConfig.Default,
            )
            CompositionLocalProvider(LocalMeshlitThemeConfig provides config) {
                MeshlitTheme {
                    MeshlitApp()
                }
            }
        }
    }
}