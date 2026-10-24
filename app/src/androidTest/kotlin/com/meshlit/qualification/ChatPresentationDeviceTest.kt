package com.meshlit.qualification

import android.view.WindowManager
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.meshlit.MainActivity
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatConversation
import com.meshlit.chat.ChatOptions
import kotlinx.serialization.json.Json
import com.meshlit.legal.LegalAgreementStore
import org.koin.core.context.GlobalContext
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Actual reply/navigation controls. Temporary settings edits are restored, including disk persistence. */
class ChatPresentationDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    @Test fun currentReplyReaderAndTokenSettingsWithoutBottomTabs() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Owner legal acceptance required",LegalAgreementStore(context).accepted())
        compose.activityRule.scenario.onActivity{it.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        try {
            compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()}
            if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
            if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
            val controller=GlobalContext.get().get<ChatController>()
            compose.waitUntil(30_000){controller.ready.isCompleted}
            val original=controller.state.value
            assertFalse("Existing owner response required",original.current?.messages?.none{it.role=="assistant" && it.text.isNotBlank()} ?: true)
            compose.onNodeWithTag("workspace-bottom-bar").assertDoesNotExist()
            compose.onNodeWithTag("chat-timeline").performScrollToNode(hasContentDescription("Read full response"))
            compose.onAllNodesWithContentDescription("Read full response").onFirst().performClick()
            compose.onNodeWithTag("reply-reader").assertIsDisplayed()
            val folder=File(context.filesDir,"qualification").also{check(it.mkdirs() || it.isDirectory)}
            File(folder,"chat-reader-device.png").outputStream().use{compose.onNodeWithTag("reply-reader").captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithContentDescription("Show original Markdown").performClick()
            compose.onNodeWithContentDescription("Show original Markdown").performClick()
            compose.onNodeWithTag("reply-search").performTextReplacement("the")
            closeSoftKeyboard()
            compose.onNode(hasText("Next",substring=true)).assertIsEnabled().performClick()
            compose.onNodeWithContentDescription("Close response reader").performClick()
            compose.onNodeWithContentDescription("Chat menu").performClick()
            compose.onNodeWithText("Conversation and token settings").performClick()
            compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag("chat-output-budget"))
            compose.onNodeWithTag("chat-output-budget").performTextReplacement("512")
            closeSoftKeyboard()
            val coordinator=GlobalContext.get().get<com.meshlit.core.inference.InferenceCoordinator>()
            if(original.current!!.options.onlineProfileId==null && original.current!!.options.routeId==null && (coordinator.loadedModel()?.contextSize ?: 0)<=0) {
                val unknown=hasText("Loaded context capacity: unknown tokens.",substring=true)
                compose.onNodeWithTag("chat-settings").performScrollToNode(unknown)
                compose.onNode(unknown).assertIsDisplayed()
            }
            val screenshot=checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            File(folder,"chat-token-settings-device.png").outputStream().use{screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(original.selectedId,controller.state.value.selectedId)
            assertEquals(original.current,controller.state.value.current)
            val ownerOptions=checkNotNull(original.current).options
            fun storedOptions():ChatOptions?=runCatching {
                Json{ignoreUnknownKeys=true}.decodeFromString<List<ChatConversation>>(File(context.filesDir,"conversations-v1.json").readText())
                    .first{it.id==original.selectedId}.options
            }.getOrNull()
            try {
                compose.onNodeWithContentDescription("Chat menu").performClick()
                compose.onNodeWithText("Conversation and token settings").performClick()
                compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag("chat-output-budget"))
                compose.onNodeWithTag("chat-output-budget").performTextReplacement("512")
                closeSoftKeyboard()
                val speed=hasContentDescription("Show token speed indicator")
                compose.onNodeWithTag("chat-settings").performScrollToNode(speed)
                compose.onNode(speed).performClick()
                compose.onNodeWithText("Save").assertIsEnabled().performClick()
                val changed=ownerOptions.copy(maxTokens=512,showTokenStats=!ownerOptions.showTokenStats)
                compose.waitUntil(10_000){storedOptions()==changed}
                assertEquals(changed,controller.state.value.current!!.options)
                compose.onNodeWithContentDescription("Chat menu").performClick()
                compose.onNodeWithText("Conversation and token settings").performClick()
                compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag("chat-output-budget"))
                compose.onNodeWithTag("chat-output-budget").assertTextContains("512")
                compose.onNodeWithText("Cancel").performClick()
            } finally {
                controller.setOptions(ownerOptions)
                compose.waitUntil(10_000){storedOptions()==ownerOptions}
            }
            assertEquals(original.current,controller.state.value.current)
            File(folder,"chat-presentation-device.txt").writeText("no-bottom-tabs\nexisting-response-reader\noriginal-markdown-toggle\nfind-and-next\ntoken-budget-edit-cancel\nunknown-context-sentinel\nsave-output-and-speed\nsettings-persisted\nsettings-reopened\nowner-chat-restored\n")
            println("MESHLIT_CHAT_PRESENTATION checks=10 live_generation=false")
        } finally {compose.activityRule.scenario.onActivity{it.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
    }
}
