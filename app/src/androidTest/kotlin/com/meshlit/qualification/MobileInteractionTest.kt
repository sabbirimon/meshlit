package com.meshlit.qualification

import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.core.view.WindowCompat
import com.meshlit.ThemedAppWindow
import com.meshlit.ui.theme.MeshlitThemeConfig
import com.meshlit.ui.theme.ThemeMode
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.meshlit.ui.modern.ModernSettingsScreen
import com.meshlit.ui.modern.RadarTopology
import com.meshlit.ui.theme.MeshlitTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MobileInteractionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun systemBarsFollowTheSelectedAppTheme() {
        val mode = mutableStateOf(ThemeMode.DARK)
        compose.setContent {
            MeshlitTheme(MeshlitThemeConfig.Default.copy(themeMode=mode.value, dynamicColors=false)) {
                compose.activity.ThemedAppWindow { }
            }
        }
        for (expectedMode in listOf(ThemeMode.DARK, ThemeMode.LIGHT)) {
            compose.runOnIdle { mode.value = expectedMode }
            compose.waitForIdle()
            val light = expectedMode == ThemeMode.LIGHT
            compose.runOnIdle {
                val window = compose.activity.window
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                assertEquals(light, controller.isAppearanceLightStatusBars)
                if (android.os.Build.VERSION.SDK_INT >= 26) assertEquals(light, controller.isAppearanceLightNavigationBars)
            }
            val frame = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            try {
                val x = frame.width * 3 / 4
                val rows = mutableListOf(4, frame.height / 2)
                if (android.os.Build.VERSION.SDK_INT >= 26) rows.add(frame.height - 5)
                for (y in rows) {
                    assertEquals("Background at y=$y in $expectedMode", light, Color.red(frame.getPixel(x, y)) > 128)
                }
            } finally { frame.recycle() }
        }
    }

    @Test fun liveRadarSweepChangesAcrossFrames() {
        compose.mainClock.autoAdvance=false
        compose.setContent { MeshlitTheme { RadarTopology(emptyList(),scanning=true,animateScan=true) } }
        compose.mainClock.advanceTimeBy(32)
        val node=compose.onNodeWithTag("radar-topology")
        val first=node.captureToImage().asAndroidBitmap()
        compose.mainClock.advanceTimeBy(780)
        val next=node.captureToImage().asAndroidBitmap()
        assertFalse("A live discovery sweep should move",first.sameAs(next))
    }

    @Test fun longPressShowsSettingsDetailsWithoutEnteringThePage() {
        compose.setContent { MeshlitTheme { ModernSettingsScreen() } }
        compose.onNodeWithText("General").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry-voice-settings"))
        compose.onNodeWithTag("settings-entry-voice-settings").performTouchInput { longClick() }
        compose.onNodeWithText("Open").assertExists()
        compose.onNodeWithText("Close").performClick()
        compose.onNodeWithTag("settings-list").assertExists()
    }
}
