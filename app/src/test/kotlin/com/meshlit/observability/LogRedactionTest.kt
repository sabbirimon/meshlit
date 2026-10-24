package com.meshlit.observability
import org.junit.Test
import org.junit.Assert.*
class LogRedactionTest {
    @Test fun credentialsAndSensitiveContextAreRemoved(){
        val entry=LogBuffer.Entry(0,LogBuffer.Level.ERROR,"test","Bearer secret123 token=private hf_abcdefghijk",mapOf("api_key" to "supersecret","prompt" to "private message","phase" to "Failed"))
        val safe=LogRedaction.entry(entry).toJsonLine()
        listOf("secret123","private","hf_abcdefghijk","supersecret","private message").forEach{assertFalse(safe.contains(it))}
        assertTrue(safe.contains("Failed"))
    }
}
