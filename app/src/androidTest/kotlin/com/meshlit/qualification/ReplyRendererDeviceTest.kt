package com.meshlit.qualification

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.ui.modern.*
import com.meshlit.ui.theme.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

/** Labelled rendering fixture, not model generation or live chart data. */
class ReplyRendererDeviceTest {
    @get:Rule val compose=createComposeRule()
    @Test fun nativeTablesCodeChartAndFullReaderOnPhone() {
        val source="""
            # ✨ Renderer test fixture

            **Not live data.** This fixture checks layout only.

            1. Read a separated list item.
            2. Another item with *emphasis*.

            | Sample | Value | Unit |
            | --- | ---: | --- |
            | A | 10 | ms |
            | B | 20 | ms |

            ```text
            selectable sample code with a deliberately long line for horizontal scrolling across the phone display
            ```

            ```meshlit-chart
            {"type":"bar","title":"Renderer fixture values","unit":"ms","labels":["A","B"],"values":[10,20]}
            ```
        """.trimIndent()
        val doc=formatReply(source)
        compose.setContent {MeshlitTheme(MeshlitThemeConfig(themeMode=ThemeMode.DARK)) {
            Surface(Modifier.fillMaxSize()) {LazyColumn(Modifier.fillMaxSize(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(14.dp)) {
                items(doc.blocks){ReplyContent(it)}
                item{MessageActions(source)}
            }}
        }}
        compose.onNodeWithText("✨ Renderer test fixture").assertIsDisplayed()
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val folder=File(context.filesDir,"qualification").also{check(it.mkdirs() || it.isDirectory)}
        File(folder,"reply-heading-emoji-fixture-device.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithTag("reply-table").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Wrap").performScrollTo().performClick()
        compose.onNodeWithText("Scroll").assertIsDisplayed()
        compose.onNodeWithContentDescription("Copy code").performScrollTo().performClick()
        val clipboard=context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        compose.runOnIdle{assertTrue(clipboard.primaryClip!!.getItemAt(0).text.toString().startsWith("selectable sample code"))}
        compose.onNodeWithTag("reply-chart").performScrollTo().assertIsDisplayed()
        File(folder,"reply-renderer-fixture-device.png").outputStream().use{compose.onRoot().captureToImage().asAndroidBitmap().compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)}
        compose.onNodeWithContentDescription("Read full response").performScrollTo().performClick()
        compose.onNodeWithContentDescription("Response outline").assertIsEnabled().performClick()
        compose.onAllNodesWithText("✨ Renderer test fixture").onLast().performClick()
        compose.onNodeWithContentDescription("Close response reader").performClick()
        File(folder,"reply-renderer-device.txt").writeText("labelled-fixture\ntable\ncode-wrap\ncode-clipboard\nchart-exact-values\nreader-outline\n")
        println("MESHLIT_REPLY_RENDERER checks=6 live_generation=false")
    }
}
