package com.meshlit.desktop
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.use
import androidx.compose.ui.ExperimentalComposeUiApi
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.*

class WorkspacePreviewTest {
    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun onlineAndCodingPanelsRenderWithoutRequests() {
        for (panel in listOf("online", "code")) {
            ImageComposeScene(600, 1000) {
                androidx.compose.material.MaterialTheme(colors = androidx.compose.material.darkColors()) {
                    androidx.compose.material.Surface {
                        if(panel == "code") CodingWorkspace(androidx.compose.ui.Modifier, {}, {}, {})
                        else androidx.compose.foundation.layout.Column {
                            OnlineModelsPanel("OpenRouter", "https://openrouter.ai/api/v1", "", HostProtocol.OPENAI,
                                true, false, false, false, emptyList(), "", {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
                        }
                    }
                }
            }.use { scene -> scene.render().use { image ->
                assertEquals(600, image.width)
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    System.getProperty("meshlit.previewDir")?.let { path -> File(path).apply { mkdirs() }.resolve("$panel-panel.png").writeBytes(data.bytes) }
                }
            } }
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun nativeVpnDashboardRendersWithoutRequestingStatus() {
        ImageComposeScene(600, 1000) {
            androidx.compose.material.MaterialTheme(colors = androidx.compose.material.darkColors()) {
                androidx.compose.material.Surface { VpnDashboard({}, {}) }
            }
        }.use { scene -> scene.render().use { image ->
            assertEquals(600, image.width)
            image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                System.getProperty("meshlit.previewDir")?.let { path -> File(path).apply { mkdirs() }.resolve("vpn-dashboard.png").writeBytes(data.bytes) }
            }
        } }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    @Test fun dashboardHubRendersAtCompactAndWideWidths() {
        for (width in listOf(420, 600, 1060)) {
            ImageComposeScene(width, 1000) {
                androidx.compose.material.MaterialTheme(colors = androidx.compose.material.darkColors()) {
                    androidx.compose.material.Surface {
                        androidx.compose.foundation.layout.Column { DashboardHub {} }
                    }
                }
            }.use { scene -> scene.render().use { image ->
                assertEquals(width, image.width)
                image.encodeToData(EncodedImageFormat.PNG)!!.use { data ->
                    System.getProperty("meshlit.previewDir")?.let { path ->
                        File(path).apply { mkdirs() }.resolve("dashboards-$width.png").writeBytes(data.bytes)
                    }
                }
            } }
        }
    }
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
