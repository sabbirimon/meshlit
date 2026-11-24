package com.meshlit.qualification

import android.app.NotificationManager
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.chat.ChatController
import com.meshlit.chat.ChatInferenceService
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.util.UUID

/** Real Android regression for a reply finishing before service creation reaches the main loop. */
class ChatServiceLifecycleDeviceTest {
    @Test fun immediateCompletionPromotesBeforeStopping()=runBlocking {
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        assumeTrue(InstrumentationRegistry.getArguments().getString("qualifyChatServiceLifecycle")=="true")
        val context=instrumentation.targetContext
        assertFalse("Run exclusively, without an owner's active reply",GlobalContext.get().get<ChatController>().state.value.running)
        repeat(3) {
            val id="qa-service-${UUID.randomUUID()}"
            // Both requests precede the service callbacks on the main looper.
            instrumentation.runOnMainSync {
                ContextCompat.startForegroundService(context,Intent(context,ChatInferenceService::class.java).putExtra("generation",id))
                ChatInferenceService.finish(context,id)
            }
            delay(250)
        }
        // The original bug kills this process on Android's delayed foreground-start check.
        delay(6500)
        assertTrue(context.getSystemService(NotificationManager::class.java).activeNotifications.none{it.id==706})
        assertFalse(GlobalContext.get().get<ChatController>().state.value.running)
    }
}
