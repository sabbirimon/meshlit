package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import org.jetbrains.skia.EncodedImageFormat
import java.io.File

/** Explicit developer smoke command for the packaged JVM/native graphics library; no network. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun renderCheck(path: String) {
    System.setProperty("java.awt.headless", "true")
    ImageComposeScene(1120, 800) { Workspace() }.use { scene ->
        scene.render().use { image ->
            image.encodeToData(EncodedImageFormat.PNG)?.use { File(path).writeBytes(it.bytes) }
                ?: error("PNG encoder unavailable")
        }
    }
    println("Native desktop workspace rendered; no model or device inference tested.")
}

/** Render the same category menu and dashboard components used by Settings. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun renderManagementCheck(path: String) {
    System.setProperty("java.awt.headless", "true")
    ImageComposeScene(1060, 800) {
        androidx.compose.material.MaterialTheme(colors = androidx.compose.material.darkColors(
            primary = androidx.compose.ui.graphics.Color(0xFFFF9656), background = androidx.compose.ui.graphics.Color(0xFF101113))) {
            androidx.compose.material.Surface(androidx.compose.ui.Modifier.fillMaxSize()) {
                androidx.compose.foundation.layout.Row {
                    androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier.width(260.dp).fillMaxHeight().padding(12.dp)) {
                        DesktopMenu("management", {}, advanced = true)
                    }
                    androidx.compose.foundation.layout.Column(androidx.compose.ui.Modifier.weight(1f).fillMaxHeight()
                        .verticalScroll(androidx.compose.foundation.rememberScrollState()).padding(12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) { ManagementPanel {} }
                }
            }
        }
    }.use { scene -> scene.render().use { image ->
        image.encodeToData(EncodedImageFormat.PNG)?.use { File(path).writeBytes(it.bytes) } ?: error("PNG encoder unavailable")
    } }
    println("Native management categories rendered; unavailable backends are not executed.")
}
