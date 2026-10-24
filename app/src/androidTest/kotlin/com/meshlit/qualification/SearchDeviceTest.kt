package com.meshlit.qualification

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.chat.ChatController
import com.meshlit.legal.LegalAgreementStore
import org.koin.core.context.GlobalContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Searches the owner's existing content; no web call, credential edit or grant mutation. */
class SearchDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun globalOptionsChatMatchesAndClusterFallback() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(LegalAgreementStore(context).accepted())
        compose.activityRule.scenario.onActivity{it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        try {
            compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()}
            if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
            if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
            val chats=GlobalContext.get().get<ChatController>();compose.waitUntil(30_000){chats.ready.isCompleted}
            val original=chats.state.value
            compose.onNodeWithContentDescription("Search all of Meshlit").performClick()
            compose.onNodeWithTag("global-search-query").performTextReplacement("token ceiling");closeSoftKeyboard()
            compose.waitUntil(10_000){compose.onAllNodesWithText("Output token ceiling").fetchSemanticsNodes().isNotEmpty()}
            compose.onNodeWithText("Output token ceiling").performClick()
            compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag("chat-budget-automatic"))
            compose.onNodeWithTag("chat-budget-automatic").performClick()
            compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag("chat-budget-explanation"))
            compose.onNodeWithTag("chat-budget-explanation").assertTextContains("using manual ceiling",substring=true)
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(original.current,chats.state.value.current)
            compose.onNodeWithContentDescription("Search all of Meshlit").performClick()
            compose.onNodeWithTag("global-search-query").performTextReplacement("Monitor");closeSoftKeyboard()
            compose.waitUntil(10_000){compose.onAllNodesWithText("Monitor",useUnmergedTree=true).fetchSemanticsNodes().isNotEmpty()}
            com.meshlit.search.SearchCategory.entries.forEach{compose.onNodeWithTag("global-search-tab-${it.name}").assertIsDisplayed()}
            val folder=File(context.filesDir,"qualification").also{check(it.mkdirs() || it.isDirectory)}
            fun screenshot(name:String){val image=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot());File(folder,name).outputStream().use{image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}}
            screenshot("global-search-device.png")
            compose.onNodeWithTag("global-search-tab-WEB").performClick()
            compose.waitUntil(10_000){compose.onAllNodesWithTag("global-web-search").fetchSemanticsNodes().isNotEmpty()}
            val service=GlobalContext.get().get<com.meshlit.search.AppSearchService>()
            if(!service.access.state.value.webEnabled) compose.onNodeWithTag("global-web-search").assertIsNotEnabled()
            compose.onNodeWithContentDescription("Search access settings").performClick()
            compose.onNodeWithTag("search-access").assertExists()
            compose.onNodeWithContentDescription("Back").performClick()
            compose.onNodeWithContentDescription("Menu").performClick()
            compose.onNodeWithTag("workspace-search").performTextReplacement("Chat")
            compose.onNodeWithTag("workspace-chat").performClick()
            compose.onNodeWithContentDescription("Chat menu").performClick()
            compose.onNodeWithText("Search this chat").performClick()
            compose.onNodeWithTag("chat-search-query").performTextReplacement("the");closeSoftKeyboard()
            compose.waitUntil(10_000){val node=compose.onAllNodesWithTag("chat-search-next").fetchSemanticsNodes().firstOrNull();node!=null && !node.config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled)}
            compose.onNodeWithTag("chat-search-next").assertIsEnabled().performClick()
            compose.onNodeWithTag("chat-search-previous").assertIsEnabled().performClick()
            screenshot("chat-search-device.png")
            compose.onNodeWithContentDescription("Close chat search").performClick()
            assertEquals(original.current,chats.state.value.current)
            assertEquals(original.selectedId,chats.state.value.selectedId)
            File(folder,"search-device.txt").writeText("global-options-result\nopen-token-settings\nautomatic-unmeasured-fallback\nsettings-cancel\nworkspace-search-result\nall-categories-visible\nweb-access-gate\nopen-search-access\ncurrent-chat-find-next-previous\nowner-chat-restored\nno-web-request\n")
        } finally{compose.activityRule.scenario.onActivity{it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    }
}
