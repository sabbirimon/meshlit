package com.meshlit.qualification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatConversation
import com.meshlit.chat.ChatOptions
import com.meshlit.legal.LegalAgreementStore
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Exercises saved per-chat selections only. Does not execute a tool or enlarge service/device grants. */
class ChatToolsSettingsDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()

    @Test fun toolSelectionsPersistAndCapacityExplainsDisabledGroups() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("Owner legal acceptance required",LegalAgreementStore(context).accepted())
        assertFalse("Experimental debug build required",com.meshlit.BuildProfile.coreCandidate || com.meshlit.BuildConfig.PLAY_REVIEW)
        compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()}
        if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
        if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
        val controller=GlobalContext.get().get<ChatController>()
        val search=GlobalContext.get().get<com.meshlit.search.AppSearchService>()
        val operations=GlobalContext.get().get<com.meshlit.operations.OperationsControl>()
        val originalPolicy=operations.gate.policy.value
        compose.waitUntil(30_000){search.ready.isCompleted}
        val originalSearchAccess=search.access.state.value
        val pipeline=GlobalContext.get().get<com.meshlit.pipeline.PipelineHost>()
        check(!pipeline.status.value.coordinator && !pipeline.status.value.starting){"Stop the cluster before settings qualification"}
        val originalGroup=pipeline.activeGroup()
        compose.waitUntil(30_000){controller.ready.isCompleted}
        val original=checkNotNull(controller.state.value.current){"Open an existing conversation before qualification"}
        assertFalse("Finish the current generation first",controller.state.value.running)
        val baseline=original.options.copy(onlineProfileId=null,routeId=null,colibriMode="OFF",webTools=false,
            phoneTools=false,memoryTools=false,localSearchTools=false,nodeTools=false,cryptoTools=false,
            gibberlinkTools=false,remoteTools=false,peerTools=false)
        fun stored():ChatOptions?=runCatching {
            Json{ignoreUnknownKeys=true}.decodeFromString<List<ChatConversation>>(File(context.filesDir,"conversations-v1.json").readText())
                .first{it.id==original.id}.options
        }.getOrNull()
        fun open(){compose.onNodeWithContentDescription("Chat menu").performClick();compose.onNodeWithText("Conversation and token settings").performClick()}
        fun tool(tag:String)=compose.onNodeWithTag(tag).also {compose.onNodeWithTag("chat-settings").performScrollToNode(hasTestTag(tag))}
        try {
            controller.setOptions(baseline)
            compose.waitUntil(10_000){stored()==baseline}
            val stagedFrom=if(originalGroup=="default") "settings-regression" else originalGroup
            pipeline.setActiveGroup(stagedFrom)
            open()
            val defaultGroup=hasText("default ·",substring=true)
            compose.onNodeWithTag("chat-settings").performScrollToNode(defaultGroup)
            compose.onNode(defaultGroup).performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("Cancel must not change the saved worker group",stagedFrom,pipeline.activeGroup())
            open()
            compose.onNodeWithTag("chat-settings").performScrollToNode(defaultGroup)
            compose.onNode(defaultGroup).performClick()
            compose.onNodeWithText("Save").performClick()
            assertEquals("default",pipeline.activeGroup())
            open()
            tool("chat-tool-node").assertIsEnabled().performClick()
            tool("chat-local-search-tools").assertIsNotEnabled()
            tool("chat-tool-memory").assertIsNotEnabled()
            tool("chat-tool-phone").assertIsNotEnabled()
            compose.onNodeWithText("Save").assertIsEnabled().performClick()
            val saved=baseline.copy(nodeTools=true)
            compose.waitUntil(10_000){stored()==saved}
            open()
            tool("chat-tool-node").assertIsOn().performClick()
            tool("chat-tool-phone").assertIsEnabled().performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals(saved,controller.state.value.current!!.options)
            val researcher=com.meshlit.agent.AgentTaskPresets.presets.first{it.key=="research"}
            open()
            tool("chat-task-${researcher.key}").performClick()
            compose.onNodeWithText("Cancel").performClick()
            assertEquals("Task preset must remain a draft until Save",saved,controller.state.value.current!!.options)
            open()
            tool("chat-task-${researcher.key}").performClick()
            compose.onNodeWithText("Save").performClick()
            val research=com.meshlit.chat.ChatTaskPreset.apply(researcher,saved)
            compose.waitUntil(10_000){stored()==research}
            assertTrue(research.webTools && research.localSearchTools)
            assertFalse(research.nodeTools || research.phoneTools)
            assertEquals("Chat settings must not grant app-wide search",originalSearchAccess,search.access.state.value)
            assertEquals("Chat settings must not change app-wide agent control",originalPolicy,operations.gate.policy.value)
            val evidence=File(context.filesDir,"qualification/chat-tools-settings.txt")
            check(evidence.parentFile!!.mkdirs() || evidence.parentFile!!.isDirectory)
            evidence.writeText("worker-group-cancel-preserved\nworker-group-save-applied\nlocal-tool-selection\nnode-counts-ten\nten-tool-limit-explained\nsave-persisted\nreopen-selected\nrevocation-frees-capacity\ncancel-keeps-saved-selection\ntask-preset-cancel-preserved\ntask-preset-save-persisted\napp-wide-search-unchanged\napp-wide-agent-control-unchanged\nno-tool-dispatch\n")
        } finally {
            pipeline.setActiveGroup(originalGroup)
            controller.setOptions(original.options)
            compose.waitUntil(10_000){stored()==original.options}
        }
        assertEquals(original,controller.state.value.current)
    }
}
