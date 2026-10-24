package com.meshlit.crypto
import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[34])
class CryptoGrantTest {
    @Test fun offlineHumanWorkDoesNotGrantAgentAccessAndSavedRevocationIsEnforced(): Unit = runBlocking {
        val context = RuntimeEnvironment.getApplication(); val host = CryptoHost(context)
        assertFalse(host.agents.value)
        assertEquals(64, host.run("sha256", "fixture", "", "", "").length)
        assertThrows(IllegalStateException::class.java) { runBlocking { host.run("sha256", "fixture", "", "", "", true) } }
        host.saveAgentGrantHuman(true)
        assertTrue(CryptoHost(context).agents.value)
        assertEquals(64, host.run("sha256", "fixture", "", "", "", true).length)
        host.saveAgentGrantHuman(false)
        assertFalse(CryptoHost(context).agents.value)
        assertThrows(IllegalStateException::class.java) { runBlocking { host.run("sha256", "fixture", "", "", "", true) } }
    }
}
