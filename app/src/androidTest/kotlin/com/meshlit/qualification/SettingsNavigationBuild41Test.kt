package com.meshlit.qualification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.legal.LegalAgreementStore
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks the installed app's actual Basic/Advanced and wallet navigation. */
@RunWith(AndroidJUnit4::class)
class SettingsNavigationBuild41Test {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun advancedMenuAndCrossTabSearchReachRewardsAndNetwork() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Owner must accept the app notices manually",LegalAgreementStore(context).accepted())
        val preferences = context.getSharedPreferences("settings-navigation", 0)
        val hadAdvanced = preferences.contains("advanced")
        val previousAdvanced = preferences.getBoolean("advanced", false)
        try {
        compose.waitUntil(180_000){runCatching {
            compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty() ||
                compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()
        }.getOrDefault(false)}
        if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
        if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Basic").performClick()
        compose.onNodeWithTag("open-dashboards").assertExists()
        compose.onNodeWithText("Everyday preferences.", substring=true).assertExists()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry-voice-settings"))
        compose.onNodeWithTag("settings-entry-voice-settings").performClick()
        compose.onNodeWithTag("voice-panel").assertExists()
        compose.onNodeWithContentDescription("Voice settings").assertExists()
        compose.onNodeWithContentDescription("End and close voice").performClick()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasText("Advanced"))
        compose.onNodeWithText("Advanced").performClick()
        compose.onNodeWithText("Technical controls and integrations").assertExists()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-group-MODELS"))
        compose.onNodeWithTag("settings-group-MODELS").performClick()
        compose.onNodeWithTag("settings-all-categories").assertExists()
        // Search must escape the current model category and include every permitted setting.
        compose.onNodeWithTag("settings-search").performTextInput("rewards")
        closeSoftKeyboard()
        compose.onNodeWithText("Mesh rewards wallet").performClick()
        compose.onNodeWithText("Your local contribution record").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("settings-search").performTextReplacement("network")
        closeSoftKeyboard()
        compose.onNodeWithText("Network and pairing").performClick()
        compose.onNodeWithText("Connect devices").assertExists()
        println("MESHLIT_SETTINGS_NAVIGATION basic=true voice=true categories=true cross_category_search=true rewards=true network=true")
        } finally {
            if (hadAdvanced) preferences.edit().putBoolean("advanced", previousAdvanced).commit()
            else preferences.edit().remove("advanced").commit()
        }
    }
}
