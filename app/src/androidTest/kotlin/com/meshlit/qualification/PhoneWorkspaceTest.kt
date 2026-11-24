package com.meshlit.qualification

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.ThemedAppWindow
import com.meshlit.ui.modern.TouchIcon
import com.meshlit.ui.phone.*
import com.meshlit.ui.theme.*
import org.junit.Assert.assertEquals
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import java.io.File

class PhoneWorkspaceTest {
    @get:Rule val compose=createAndroidComposeRule<ComponentActivity>()

    @Test fun promptUsesLargeTargetsAndKeepsActionsAboveKeyboard() {
        var sent=0
        val focus=FocusRequester()
        compose.setContent {
            var draft by remember{mutableStateOf("")}
            MeshlitTheme(MeshlitThemeConfig.Default.copy(themeMode=ThemeMode.DARK)) {
                compose.activity.ThemedAppWindow {
                    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(16.dp)) {
                        Box(Modifier.weight(1f),contentAlignment=Alignment.Center) { PhoneGreeting("How can I help?") {} }
                        PhonePrompt(draft,{draft=it},"Ask Meshlit",true,focus,{sent++},
                            {TouchIcon(Icons.Default.Add,"Add attachment"){}},{TouchIcon(Icons.Default.Tune,"Chat tools"){}},
                            {TouchIcon(Icons.Default.Send,"Send message"){sent++}})
                    }
                }
            }
        }
        compose.onNodeWithContentDescription("Add attachment").assertWidthIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Send message").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithTag("chat-composer").performClick().performTextInput("Hello")
        compose.onNodeWithContentDescription("Send message").assertIsDisplayed().performTouchInput { longClick() }
        compose.runOnIdle { assertEquals(0,sent) }
        compose.onNodeWithContentDescription("Send message").performTouchInput { click() }
        compose.runOnIdle { assertEquals(1,sent) }
    }

    @Test fun turnRailOpensAndJumpsToTheChosenPrompt() {
        var selected=0
        compose.setContent { MeshlitTheme { PhoneTurnAnchors(listOf("First prompt","Second prompt","Third prompt"),1){selected=it} } }
        compose.onNodeWithContentDescription("Conversation anchors, turn 2 of 3").performClick()
        compose.onNodeWithText("Third prompt").performClick()
        compose.runOnIdle { assertEquals(2,selected) }
    }

    @Test fun hubListsMoreThanOnePageAndReturnsPublisherArtOnDevice() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePhoneCatalog")=="true")
        kotlinx.coroutines.runBlocking {
            val catalog=com.meshlit.models.PhoneHubCatalog
            val first=catalog.page("",null,"")
            val next=catalog.page("",requireNotNull(first.next),"")
            org.junit.Assert.assertTrue((first.ids+next.ids).distinct().size>50)
            val fits=catalog.page("stories260K",null,"",ggufOnly=true,capacity=2_000_000L)
            org.junit.Assert.assertTrue("ggml-org/test-model-stories260K" in fits.ids)
            org.junit.Assert.assertNotNull(catalog.logo("Qwen"))
        }
    }

    @Test fun publicCatalogsResolveDownloadChoicesOnDevice() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("livePhoneCatalog")=="true")
        compose.setContent { MeshlitTheme { PhoneModelPicker() } }
        compose.onNodeWithText("LM Studio").performClick()
        compose.onNodeWithTag("phone-catalog-search").performTextInput("Bonsai")
        fun waitForText(value:String) {
            compose.waitUntil(60_000) {compose.onAllNodes(hasText(value,substring=true)).fetchSemanticsNodes().isNotEmpty()}
        }
        waitForText("Bonsai 27B")
        compose.onNodeWithText("Bonsai 27B").performClick()
        waitForText("bonsai-27b")
        compose.onNodeWithText("bonsai-27b",substring=true).performClick()
        waitForText("GGUF")
        compose.onAllNodes(hasText("GGUF",substring=true)).onFirst().performClick()
        waitForText("Download ·")
        compose.onNodeWithText("Hugging Face").performClick()
        compose.onNodeWithTag("phone-catalog-search").performTextInput("stories260K")
        waitForText("ggml-org/test-model-stories260K")
        compose.onNodeWithText("ggml-org/test-model-stories260K").performClick()
        waitForText("Publisher model card")
        compose.onNodeWithText("GGUF").assertIsDisplayed()
        compose.onNodeWithText("llama",substring=true).assertExists()
        val dir=InstrumentationRegistry.getInstrumentation().targetContext.cacheDir
        File(dir,"phone-picker-proof.png").outputStream().use { out->
            compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,out)
        }
    }
}
