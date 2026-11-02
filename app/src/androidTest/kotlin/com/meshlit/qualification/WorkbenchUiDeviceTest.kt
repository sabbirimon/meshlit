package com.meshlit.qualification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso.closeSoftKeyboard
import com.meshlit.MainActivity
import com.meshlit.legal.LegalAgreementStore
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Exercises the actual application navigation and editor controls on a device.
 * Does not grant permissions, accept legal agreements, or enable remote/lab tools. */
@RunWith(AndroidJUnit4::class)
class WorkbenchUiDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private val checks=mutableListOf<String>()
    private fun outputContains(text:String) {
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-output"))
        compose.waitUntil(10_000){compose.onAllNodesWithTag("hyperl-output").fetchSemanticsNodes().any{node->
            node.config.getOrElse(SemanticsProperties.Text){emptyList()}.any{it.text.replace(Regex("\\s+"),"").contains(text.replace(Regex("\\s+"),""))}
        }}
    }
    private fun click(text:String) {
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasText(text))
        compose.onNodeWithText(text).performClick()
    }
    private fun navigate(id:String,label:String) {
        compose.onNodeWithContentDescription("Menu").performClick()
        compose.onNode(hasTestTag("workspace-search") and hasSetTextAction()).performTextReplacement(label)
        closeSoftKeyboard()
        compose.onNodeWithTag("app-drawer").performScrollToNode(hasTestTag("workspace-$id"))
        compose.onNodeWithTag("workspace-$id").performClick()
    }
    @Test fun cpuModesWarningsDraftsAndLiveDashboard() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(LegalAgreementStore(context).accepted())
        compose.runOnUiThread{compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        compose.waitUntil(60_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty()}
        navigate("settings","Settings")
        compose.onNodeWithTag("settings-search").performTextReplacement("Acceleration")
        closeSoftKeyboard()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry-acceleration"))
        compose.onNodeWithTag("settings-entry-acceleration").performClick()
        fun mode(value:String) {
            compose.onNodeWithTag("acceleration-list").performScrollToNode(hasTestTag("cpu-mode-$value"))
            compose.onNodeWithTag("cpu-mode-$value").performClick()
        }
        val preferences=com.meshlit.models.AccelerationPreferences(context)
        try {
            mode("OVERDRIVE_PLUS")
            compose.waitUntil(6000){compose.onAllNodesWithText("Overdrive+ is on · review resource limits").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(com.meshlit.core.inference.models.CpuMode.OVERDRIVE_PLUS,preferences.cpuMode())
            mode("OVERDRIVE")
            compose.waitUntil(6000){compose.onAllNodesWithText("Overdrive+ is on · review resource limits").fetchSemanticsNodes().isEmpty()}
            mode("PERFORMANCE")
            compose.onNodeWithTag("acceleration-list").performScrollToNode(hasTestTag("cpu-worker-cap"))
            compose.onNodeWithTag("cpu-worker-cap").performSemanticsAction(SemanticsActions.SetProgress){it(6f)}
            assertEquals(6,preferences.threadLimit())
        } finally {
            // Owner-authorized measured choice; never leave the phone in a test override mode.
            preferences.setCpuMode(com.meshlit.core.inference.models.CpuMode.PERFORMANCE)
            preferences.setThreadLimit(6)
        }
        compose.onNodeWithTag("acceleration-list").performScrollToNode(hasText("Back to settings"))
        compose.onNodeWithText("Back to settings").performClick()
        navigate("chat","Chat")
        val chats=org.koin.core.context.GlobalContext.get().get<com.meshlit.chat.ChatController>()
        assertTrue("Qualification needs room for two temporary chats without evicting owner history",chats.state.value.conversations.size<=38)
        val original=chats.state.value.selectedId
        val temporary=mutableListOf<String>()
        try {
            compose.runOnIdle{chats.newChat();temporary+=chats.state.value.selectedId!!}
            compose.onNodeWithTag("chat-composer").performTextInput("Draft qualification alpha")
            closeSoftKeyboard()
            compose.runOnIdle{chats.newChat();temporary+=chats.state.value.selectedId!!}
            compose.onNodeWithTag("chat-composer").performTextInput("Draft qualification beta")
            closeSoftKeyboard()
            compose.runOnIdle{chats.select(temporary.first())}
            compose.onNodeWithTag("chat-composer").assertTextContains("Draft qualification alpha")
            compose.runOnIdle{chats.select(temporary.last())}
            compose.onNodeWithTag("chat-composer").assertTextContains("Draft qualification beta")
            compose.waitUntil(6000){File(context.filesDir,"conversations-v1.json").readText().contains("Draft qualification beta")}
        } finally {
            compose.runOnIdle{temporary.forEach(chats::delete);original?.let(chats::select)}
        }
        navigate("settings","Settings")
        compose.onNodeWithTag("settings-search").performTextReplacement("Resources and sensors")
        closeSoftKeyboard()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry-resources"))
        compose.onNodeWithTag("settings-entry-resources").performClick()
        compose.onNodeWithTag("resources-list").performScrollToNode(hasText("Live resource deep dive and CPU controls"))
        compose.onNodeWithText("Live resource deep dive and CPU controls").performClick()
        compose.onNodeWithTag("resource-deep-dive").assertExists()
        compose.onNodeWithText("Meshlit CPU").assertExists()
        File(context.filesDir,"qualification/current-ux.txt").apply{parentFile!!.mkdirs();writeText("Overdrive+ persistent warning; normal Overdrive clears warning; saved Performance six; per-chat persisted drafts; live dashboard navigation")}
    }
    @Test fun navigationEditorsValidationCpuSourceAndFailureControls() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Owner must accept the app's legal agreement manually before UI qualification",LegalAgreementStore(context).accepted())
        compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
        if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
        navigate("settings","Settings");checks+="drawer-settings"
        compose.onNodeWithTag("settings-search").performTextReplacement("hyperl")
        closeSoftKeyboard()
        compose.onNodeWithText("HyperL libraries").performClick();checks+="settings-search-hyperl"
        // Leave the actual licence and opt-in visible for the owner. Never toggle
        // acceptance from the test or write its preference on the owner's behalf.
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-enable"))
        println("MESHLIT_HYPERL_WAITING_FOR_OWNER_TERMS")
        compose.waitUntil(180_000) {
            context.getSharedPreferences("hyperl-alpha6",android.content.Context.MODE_PRIVATE)
                .getBoolean("community-1-enabled",false)
        }
        click("Validate");outputContains("admitted true");checks+="validate-admitted"
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasText("Stop"))
        compose.onNodeWithText("Stop").assertIsNotEnabled();checks+="idle-stop-disabled"
        click("Run CPU");outputContains("CPU_REFERENCE");outputContains("[0.0,6.0,12.0]");checks+="actual-weighted-relu"
        click("Generate source");outputContains("kernel");checks+="metal-source"
        click("Vulkan source");click("Generate source");outputContains("#version");checks+="vulkan-source"
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-recipe"))
        compose.onNodeWithTag("hyperl-recipe").performClick()
        compose.onNodeWithText("Ordered sum").performClick()
        click("Run CPU");outputContains("[4.0]");checks+="actual-reduction"
        click("Generate source");outputContains("Reduction lowering unavailable");checks+="reduction-source-rejected"
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-inputs"))
        compose.onNodeWithTag("hyperl-inputs").performTextReplacement("{\"x\":[\"3\"]}")
        closeSoftKeyboard()
        click("Run CPU");outputContains("Numbers must not be strings");checks+="invalid-number-rejected"
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-inputs"))
        compose.onNodeWithTag("hyperl-inputs").performTextReplacement("{\"x\":[-1,2,3]}")
        closeSoftKeyboard()
        compose.onNodeWithTag("hyperl-list").performScrollToNode(hasTestTag("hyperl-budget"))
        compose.onNodeWithTag("hyperl-budget").performTextReplacement("0")
        closeSoftKeyboard()
        click("Run CPU");outputContains("Failed:");checks+="invalid-budget-rejected"
        compose.onNodeWithText("Back").performClick();checks+="workbench-back"
        compose.onNodeWithTag("settings-search").performTextReplacement("no-such-setting-qualification")
        closeSoftKeyboard()
        compose.onNodeWithText("No matching settings. Search covers Basic and Advanced.").assertExists();checks+="empty-search"
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.onNodeWithTag("settings-search").performTextReplacement("permissions")
        closeSoftKeyboard()
        compose.onNodeWithTag("settings-list").performScrollToNode(hasTestTag("settings-entry-permissions"))
        compose.onNodeWithTag("settings-entry-permissions").performClick()
        for(feature in listOf("notifications","nearby devices","microphone","camera","location")) {
            compose.onNodeWithTag("permissions-list").performScrollToNode(hasText("Manage $feature"))
            compose.onNodeWithText("Manage $feature").assertIsEnabled()
        }
        if(android.os.Build.VERSION.SDK_INT<33) {
            compose.onNodeWithTag("permissions-list").performScrollToNode(hasText("Manage notifications"))
            compose.onNodeWithText("No runtime permission required on this Android version").assertExists()
            compose.onNodeWithText("Request notifications").assertDoesNotExist()
        }
        if(!com.meshlit.BuildConfig.PLAY_REVIEW) {
            for(button in listOf("Select APK to install","Manage target app permissions","Request app uninstall")) {
                compose.onNodeWithTag("permissions-list").performScrollToNode(hasText(button))
                compose.onNodeWithText(button).assertIsEnabled()
            }
        }
        checks+="permission-manage-buttons-and-administration-visible-no-grants-changed"
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithContentDescription("Clear search").performClick()
        navigate("monitor","Monitor")
        compose.onNodeWithText("Your device, at a glance").assertExists();checks+="monitor-real-readings"
        navigate("models","Models")
        navigate("chat","Chat");checks+="models-chat-navigation"
        val file=File(context.filesDir,"qualification/workbench-ui.txt")
        check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
        file.writeText(checks.joinToString("\n"))
        println("MESHLIT_UI_QUALIFICATION package=${context.packageName} checks=${checks.size} report=qualification/workbench-ui.txt")
    }
}
