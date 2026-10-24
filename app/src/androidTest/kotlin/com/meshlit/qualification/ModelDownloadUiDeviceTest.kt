package com.meshlit.qualification

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.espresso.Espresso.closeSoftKeyboard
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.MainActivity
import com.meshlit.R
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatOptions
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.LocalModelBackend
import com.meshlit.core.inference.models.ModelFiles
import com.meshlit.legal.LegalAgreementStore
import com.meshlit.models.ModelLibrary
import com.meshlit.models.ModelCatalog
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.io.File
import java.util.UUID

/** A real public HTTPS download through the application's Add URL button,
 * followed by checksum validation, UI Load/Send/Unload. No cached asset is
 * substituted for the download. Only this test's new transfer is paused on exit. */
@RunWith(AndroidJUnit4::class)
class ModelDownloadUiDeviceTest {
    @get:Rule val compose=createAndroidComposeRule<MainActivity>()
    private fun openModels() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val label=context.getString(R.string.modern_models)
        compose.onNodeWithContentDescription(context.getString(R.string.modern_menu)).performClick()
        compose.onNodeWithTag("app-drawer").performScrollToNode(hasTestTag("workspace-models"))
        compose.onNodeWithTag("workspace-models").performClick()
    }
    private fun modelButton(id:String,text:String) {
        val matcher=hasText(text) and hasAnyAncestor(hasTestTag("model-row-$id"))
        compose.onNodeWithTag("models-list").performScrollToNode(matcher)
        compose.onNode(matcher).assertIsEnabled().performClick()
    }
    @Test fun realHttpsDownloadLoadChatSendAndUnload() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue("The owner must accept the current Terms and Privacy manually",LegalAgreementStore(context).accepted())
        // Keeps the already-unlocked display awake only for this activity's test;
        // does not dismiss a keyguard or change system lock/timeout settings.
        compose.runOnUiThread {compose.activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}
        val transport=InstrumentationRegistry.getArguments().getString("transport","unspecified")
        require(transport in listOf("usb","wireless","unspecified"))
        val koin=GlobalContext.get()
        val library=koin.get<ModelLibrary>();val inference=koin.get<InferenceCoordinator>();val chat=koin.get<ChatController>()
        runBlocking {withTimeout(180_000){library.ready.await();chat.ready.await()}}
        val ownerConversation=chat.state.value.selectedId
        val ownerModel=inference.loadedModel()?.modelPath
        check(chat.state.value.conversations.size<40){"Qualification must not evict owner chats"}
        val manifest=BundledModelInstaller().manifest(context)
        // Unique request avoids reusing an installed file or a partial transfer.
        // The upstream revision and expected content hash stay pinned.
        val url=manifest.url+"?download=true&meshlit_qualification="+UUID.randomUUID().toString()
        val id=ModelCatalog.idFromUrl(url)
        assertTrue(library.models.value.none{it.id==id})
        val measurements=linkedMapOf<String,Double>()
        var verified=false;var succeeded=false;var outputCharacters=0
        try {
            compose.waitUntil(180_000){compose.onAllNodesWithContentDescription("Menu").fetchSemanticsNodes().isNotEmpty() || compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()}
            if(compose.onAllNodesWithTag("boot-open-app").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("boot-open-app").performClick()
            if(compose.onAllNodesWithText("Later").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithText("Later").performClick()
            openModels()
            compose.waitUntil(180_000){inference.state.value !is CoordinatorState.Starting && inference.state.value !is CoordinatorState.Loading}
            inference.loadedModel()?.let{loaded->
                val original=library.models.value.single{it.installed && it.path==loaded.modelPath}
                modelButton(original.id,context.getString(R.string.modern_unload))
                compose.waitUntil(30_000){inference.loadedModel()==null}
            }
            compose.onNodeWithTag("models-list").performScrollToNode(hasText(context.getString(R.string.modern_add_url)))
            compose.onNodeWithText(context.getString(R.string.modern_add_url)).performClick()
            compose.onNode(hasSetTextAction() and hasText("HTTPS URL")).performTextInput(url)
            closeSoftKeyboard()
            val downloadStart=System.nanoTime()
            compose.onNode(hasText(context.getString(R.string.modern_download)) and hasAnyAncestor(isDialog())).performClick()
            compose.waitUntil(360_000){library.models.value.any{it.id==id && (it.installed || it.phase=="Failed")}}
            measurements["downloadValidateInstallWallMs"]=(System.nanoTime()-downloadStart)/1_000_000.0
            val installed=library.models.value.single{it.id==id}
            assertTrue("Real HTTPS download failed: ${installed.error}",installed.installed)
            assertFalse(installed.bundled)
            assertEquals("VERIFIED_HTTP",installed.downloadBackend)
            assertEquals(manifest.sizeBytes,installed.sizeBytes)
            assertEquals(manifest.sha256,runBlocking {ModelFiles.sha256(File(installed.path))})
            verified=true
            println("MESHLIT_DOWNLOAD_PHASE transport=$transport installed=true checksumVerified=true bytes=${installed.sizeBytes}")
            if(installed.runtimeOptions.backend!=LocalModelBackend.RUNANYWHERE) {
                // Configure only this new test entry through the owner's real UI.
                modelButton(id,"Context and KV cache")
                compose.onNode(hasText("RunAnywhere") and hasAnyAncestor(isDialog())).performClick()
                compose.onNodeWithText("Save for next load").performClick()
                compose.waitUntil(10_000){library.models.value.single{it.id==id}.runtimeOptions.backend==LocalModelBackend.RUNANYWHERE}
            }
            val loadStart=System.nanoTime()
            modelButton(id,context.getString(R.string.modern_load))
            compose.waitUntil(180_000){inference.state.value is CoordinatorState.Ready || inference.state.value is CoordinatorState.Error}
            assertEquals("Real UI load failed: ${inference.state.value}",installed.path,inference.loadedModel()?.modelPath)
            assertEquals("runanywhere",inference.engineTag)
            measurements["uiLoadWallMs"]=(System.nanoTime()-loadStart)/1_000_000.0
            chat.newChat();chat.setOptions(ChatOptions(maxTokens=16,temperature=0f,historyMessages=0))
            compose.onNodeWithContentDescription(context.getString(R.string.modern_menu)).performClick()
            compose.onNodeWithTag("app-drawer").performScrollToNode(hasTestTag("workspace-chat"))
            compose.onNodeWithTag("workspace-chat").performClick()
            compose.onNodeWithTag("chat-composer").performTextInput("Say hello in one short sentence.")
            closeSoftKeyboard()
            val generationStart=System.nanoTime()
            compose.onNodeWithContentDescription(context.getString(R.string.modern_send)).assertIsEnabled().performClick()
            compose.waitUntil(180_000){
                val state=chat.state.value
                !state.running && (state.error!=null || state.current?.messages?.lastOrNull()?.role=="assistant")
            }
            measurements["uiSendToCompletionWallMs"]=(System.nanoTime()-generationStart)/1_000_000.0
            assertNull("Chat reported an actual generation failure",chat.state.value.error)
            val reply=chat.state.value.current!!.messages.last()
            assertEquals("assistant",reply.role);assertTrue("Actual chat output is empty",reply.text.isNotBlank())
            outputCharacters=reply.text.length
            assertEquals(installed.path,inference.loadedModel()?.modelPath)
            openModels()
            val unloadStart=System.nanoTime()
            modelButton(id,context.getString(R.string.modern_unload))
            compose.waitUntil(30_000){inference.loadedModel()==null}
            measurements["uiUnloadWallMs"]=(System.nanoTime()-unloadStart)/1_000_000.0
            succeeded=true
        } finally {
            library.pause(id)
            runBlocking {withTimeout(30_000) {
                if(chat.state.value.running) {chat.stop();while(chat.state.value.running) kotlinx.coroutines.delay(25)}
                if(ownerConversation!=null) chat.select(ownerConversation) else chat.newChat()
                // Preserve owner configuration; this test entry remains inspectable but is no longer selected.
                if(ownerModel!=null) library.models.value.firstOrNull{it.installed && it.path==ownerModel}?.let{library.load(it.id)}
            }}
            runCatching {compose.runOnUiThread {compose.activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)}}
            val file=File(context.filesDir,"qualification/model-download-$transport.json")
            check(file.parentFile!!.mkdirs() || file.parentFile!!.isDirectory)
            file.writeText(buildJsonObject {
                put("format","meshlit-model-download-ui/1");put("passed",succeeded);put("transport",transport)
                put("model","SmolLM2 135M Instruct Q4_K_M");put("sourceUrl",manifest.url)
                put("modelSha256",manifest.sha256);put("expectedBytes",manifest.sizeBytes);put("checksumVerified",verified)
                put("freshTransfer",true);put("uiButtons","Add URL / Download / Load / Send / Unload")
                put("requestedOutputTokenLimit",16);put("outputCharacters",outputCharacters)
                put("timingScope","UI action through completion; download includes validation, model download uses phone internet independently of ADB transport")
                put("measurements",buildJsonObject{measurements.forEach{(key,value)->put(key,value)}})
            }.toString())
        }
        println("MESHLIT_DOWNLOAD_UI transport=$transport passed=true report=qualification/model-download-$transport.json")
    }
}
