package com.meshlit.desktop
import java.net.URI
import kotlin.test.*
class HubClientTest {
    @Test fun pinsRepositoryPathsAndRedirectOrigins() {
        HubClient.validateRepository("Qwen/Qwen2.5-1.5B-Instruct-GGUF")
        HubClient.validateFile("folder/model.gguf")
        assertFails { HubClient.validateRepository("../model") }
        assertFails { HubClient.validateFile("folder/../model.gguf") }
        assertTrue(HubClient.allowedHubDownload(URI("https://cas-bridge.xethub.hf.co/model?signature=value")))
        assertFalse(HubClient.allowedHubDownload(URI("https://huggingface.co.evil.org/model")))
        assertFalse(HubClient.allowedHubDownload(URI("https://secret@huggingface.co/model")))
        assertFalse(HubClient.allowedHubDownload(URI("http://127.0.0.1/model")))
    }
}
