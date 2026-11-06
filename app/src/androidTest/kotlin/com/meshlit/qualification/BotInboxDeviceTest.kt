package com.meshlit.qualification

import android.os.Build
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.meshlit.bots.BotInbox
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatExecutionMode
import com.meshlit.ui.modern.BotInboxScreen
import com.meshlit.ui.theme.MeshlitTheme
import com.meshlit.ui.theme.MeshlitThemeConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A disposable emulator's real foreground service, encrypted inbox and local Chat draft. */
class BotInboxDeviceTest {
    @get:Rule val compose = createComposeRule()

    @Test fun signedMessageWaitsForReviewAndForegroundReceiverStops() {
        assumeTrue("Use a disposable Android emulator", Build.FINGERPRINT.contains("generic") ||
            Build.HARDWARE in setOf("ranchu", "goldfish"))
        val inbox = GlobalContext.get().get<BotInbox>()
        val chat = GlobalContext.get().get<ChatController>()
        val id = UUID.randomUUID().toString()
        val message = "Signed fixture $id"
        val body = """{"source":"android-qa","id":"$id","text":"$message"}"""
        compose.setContent { MeshlitTheme(MeshlitThemeConfig()) { BotInboxScreen {} } }
        try {
            compose.onNodeWithText("Start receiver").performClick()
            compose.waitUntil(15_000) { inbox.status.value.startsWith("Listening") }
            val time = System.currentTimeMillis() / 1000
            val key = inbox.secret()
            val signature = Mac.getInstance("HmacSHA256").apply {
                init(SecretKeySpec(key.toByteArray(), "HmacSHA256"))
            }.doFinal("$time.$body".toByteArray()).joinToString("") { "%02x".format(it) }
            val request = Request.Builder().url("http://127.0.0.1:${inbox.port}/bot/inbox")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("X-Meshlit-Timestamp", time.toString())
                .header("X-Meshlit-Signature", signature).build()
            val client = OkHttpClient.Builder().proxy(java.net.Proxy.NO_PROXY).build()
            assertEquals(202, client.newCall(request).execute().use { it.code })
            assertEquals(200, client.newCall(request).execute().use { it.code })
            compose.waitUntil(10_000) { inbox.pending.value.any { it.id == id } && chat.ready.isCompleted }
            assertTrue(chat.state.value.conversations.none { it.draft.contains(message) })
            compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("Save as Chat draft").performScrollTo().performClick()
            compose.waitUntil(10_000) { chat.state.value.current?.draft?.contains(message) == true }
            assertFalse(chat.state.value.running)
            assertEquals(ChatExecutionMode.CHAT, chat.state.value.current?.options?.executionMode)
            compose.onNodeWithText("Save as Chat draft").performScrollTo().performClick()
            compose.waitForIdle()
            assertEquals(1, chat.state.value.conversations.count { it.draft.contains(message) })
            compose.onNodeWithText("Dismiss").performScrollTo().performClick()
            compose.waitUntil(10_000) { inbox.pending.value.none { it.id == id } }
            compose.onNodeWithText("Stop").performScrollTo().performClick()
            compose.waitUntil(10_000) { inbox.status.value == "Stopped" }
            assertTrue(runCatching {
                Socket().use { it.connect(InetSocketAddress("127.0.0.1", inbox.port), 1_000) }
            }.isFailure)
        } finally { inbox.stop() }
    }
}
