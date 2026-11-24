package com.meshlit.qualification

import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import java.io.File
import androidx.activity.ComponentActivity
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.meshlit.ui.modern.ModernSettingsScreen
import com.meshlit.ui.modern.SettingsDestinations
import com.meshlit.ui.theme.MeshlitTheme
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsRouteSweepTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun everyEnabledDestinationRenders() {
        compose.runOnUiThread {
            compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        val captureLight = InstrumentationRegistry.getArguments().getString("captureLightSettings") == "true"
        val theme = if(captureLight) MeshlitThemeConfig.Default.copy(themeMode=ThemeMode.LIGHT,animationsEnabled=false) else MeshlitThemeConfig.Default
        val ids = SettingsDestinations.all.map { it.id }
        val selected = mutableStateOf(ids.first())
        var displayed: String? = null
        compose.setContent {
            MeshlitTheme(config=theme) {
                key(selected.value) {
                    ModernSettingsScreen(initialDestination = selected.value, onDestinationChanged = { displayed = it })
                }
            }
        }
        val readable = SemanticsMatcher.keyIsDefined(SemanticsProperties.Text) or
            SemanticsMatcher.keyIsDefined(SemanticsProperties.ContentDescription)
        for (id in ids) {
            compose.runOnIdle { selected.value = id }
            compose.waitUntil(15_000) { displayed == id }
            assertTrue("Empty settings screen: " + id, compose.onAllNodes(readable).fetchSemanticsNodes().isNotEmpty())
            if(captureLight) {
                val folder=File(compose.activity.cacheDir,"settings-light-audit").apply { mkdirs() }
                // AndroidView content is composited by the OS outside Compose's captured layer.
                if(id=="ide" || id=="host-map") {
                    Thread.sleep(if(id=="ide") 4600 else 1200)
                    val full=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
                    File(folder,"$id-window.png").outputStream().use { full.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                    full.recycle()
                }
                val root=compose.onRoot()
                val bitmap=root.captureToImage().asAndroidBitmap()
                File(folder,"$id.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
                val labels=compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Text),useUnmergedTree=true).fetchSemanticsNodes()
                File(folder,"$id.bounds").writeText(labels.joinToString("\n") {
                    val r=it.boundsInRoot
                    listOf(r.left,r.top,r.right,r.bottom).joinToString(",")
                })
            }
            Log.i("MeshlitSettingsSweep", "rendered " + id)
        }
    }
}
