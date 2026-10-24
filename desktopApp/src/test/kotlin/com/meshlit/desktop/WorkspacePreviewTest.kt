package com.meshlit.desktop
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.ExperimentalComposeUiApi
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

class WorkspacePreviewTest {
    /** Render the real desktop workspace with the host's Skiko library; no generated reply fixtures. */
    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun nativeDesktopWorkspaceRenders() {
        ImageComposeScene(1120, 800) { Workspace() }.use { scene ->
            scene.render().use { image ->
                assertEquals(1120, image.width); assertEquals(800, image.height)
                val png = image.encodeToData(EncodedImageFormat.PNG) ?: error("PNG encoder unavailable")
                png.use { data ->
                    assertTrue(data.size > 5000)
                    System.getProperty("meshlit.previewDir")?.let { path ->
                        File(path).apply { mkdirs() }.resolve("desktop-workspace.png").writeBytes(data.bytes)
                    }
                }
            }
        }
    }
}
