package com.meshlit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.meshlit.core.common.logger
import com.meshlit.ui.MeshlitApp
import com.meshlit.ui.theme.MeshlitTheme

class MainActivity : ComponentActivity() {
    private val log = logger("MainActivity")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        log.info("activity.create", "MainActivity onCreate")
        setContent {
            MeshlitTheme {
                MeshlitApp()
            }
        }
    }
}
