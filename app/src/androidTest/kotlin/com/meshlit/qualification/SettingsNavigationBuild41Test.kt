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
        compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() ||
            compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNodeWithContentDescription("Settings").performClick()
        compose.onNodeWithText("Basic").performClick()
        compose.onNodeWithText("Everyday setup and privacy").assertExists()
        compose.onNodeWithText("Advanced").performClick()
        compose.onNodeWithText("Advanced tools and integrations").assertExists()
        compose.onNodeWithTag("settings-search").performTextInput("rewards")
        closeSoftKeyboard()
        compose.onNodeWithText("Mesh rewards wallet").performClick()
        compose.onNodeWithText("Your local contribution record").assertExists()
        compose.onNodeWithText("Back").performClick()
        compose.onNodeWithTag("settings-search").performTextReplacement("network")
        closeSoftKeyboard()
        compose.onNodeWithText("Network and pairing").performClick()
        compose.onNodeWithText("Connect your hive").assertExists()
        println("MESHLIT_SETTINGS_NAVIGATION basic=true advanced=true rewards=true network=true")
    }
}
