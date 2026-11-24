package com.meshlit.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChatSetupTest {
    @Test fun setupIsExplicitAndIndependentOfInstalledModels() {
        assertEquals("device", chatSetupIntent("Can you pair my remote laptop?"))
        assertEquals("lmstudio", chatSetupIntent("Connect to LM Studio"))
        assertEquals("bionic", chatSetupIntent("use LM Bionic"))
        assertEquals("local", chatSetupIntent("load a local model"))
        assertNull(chatSetupIntent("Explain how to connect Ollama"))
        assertNull(chatSetupIntent("Pair my laptop and send a file"))
    }
}
