package com.meshlit.qualification

import android.util.Log
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
        val ids = SettingsDestinations.all.map { it.id }
        val selected = mutableStateOf(ids.first())
        var displayed: String? = null
        compose.setContent {
            MeshlitTheme {
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
            Log.i("MeshlitSettingsSweep", "rendered " + id)
        }
    }
}
