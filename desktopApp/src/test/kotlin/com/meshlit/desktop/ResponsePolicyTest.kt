package com.meshlit.desktop
import kotlin.test.*
import org.junit.Test
class ResponsePolicyTest {
    @Test fun nativeOmitsAppPromptAndCustomRequiresContent() {
        assertNull(ResponsePolicy.MODEL_NATIVE.localPrompt("ignored"))
        assertEquals(DesktopStarter.prompt,ResponsePolicy.ASSISTANT.localPrompt("ignored"))
        assertEquals("Answer in Bengali",ResponsePolicy.CUSTOM.localPrompt("Answer in Bengali"))
        assertFailsWith<IllegalArgumentException>{ResponsePolicy.CUSTOM.localPrompt(" ")}
    }
}
