package com.meshlit.bots

import org.junit.Assert.*
import org.junit.Test
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

class BotInboxTest {
    @Test fun signedWebhookQueuesOnceAndNeverReleasesAConflictingOrExpiredReplay() {
        val key = "k".repeat(64)
        val now = 1_800_000_000L
        var durable = emptyList<BotInboxRecord>()
        val queue = BotInboxQueue(emptyList()) { durable = it }
        val allow = java.util.concurrent.atomic.AtomicBoolean(true)
        val listener = BotInboxListener(0, key, queue, { check(allow.get()) }, { now })
        val client = OkHttpClient()
        try {
            listener.start(3_000, false)
            fun post(body: String, timestamp: Long = now, signature: String = sign(key, timestamp, body), origin: String? = null): Int {
                val request = Request.Builder().url("http://127.0.0.1:${listener.listeningPort}/bot/inbox")
                    .post(body.toRequestBody("application/json".toMediaType()))
                    .header("X-Meshlit-Timestamp", timestamp.toString())
                    .header("X-Meshlit-Signature", signature)
                if (origin != null) request.header("Origin", origin)
                return client.newCall(request.build()).execute().use { it.code }
            }
            val body = """{"source":"fixture","id":"event-1","text":"hello"}"""
            assertEquals(401, post(body, signature = "0".repeat(64)))
            assertEquals(401, post(body, timestamp = now - 301))
            assertEquals(403, post(body, origin = "https://example.invalid"))
            assertTrue(durable.isEmpty())
            assertEquals(202, post(body))
            assertEquals(200, post(body))
            assertEquals(409, post(body.replace("hello", "other")))
            queue.dismiss(queue.pending.value.single())
            assertTrue(BotInboxQueue(durable) {}.pending.value.isEmpty())
            assertEquals(200, post(body))
            assertEquals(1, durable.size)
            allow.set(false)
            assertEquals(403, post(body.replace("event-1", "event-2")))
        } finally { listener.disable(); listener.stop() }
    }

    @Test fun failedPersistenceDoesNotAcknowledgeOrConsumeTheEvent() {
        val event = BotEnvelope("fixture", "message", "hello")
        var writeFails = true
        val queue = BotInboxQueue(emptyList()) { check(!writeFails) }
        assertThrows(IllegalStateException::class.java) { queue.accept(event, 1) }
        assertTrue(queue.pending.value.isEmpty())
        writeFails = false
        assertEquals(InboxDecision.QUEUED, queue.accept(event, 1))
    }

    private fun sign(key: String, timestamp: Long, body: String): String =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
            .doFinal("$timestamp.$body".toByteArray()).joinToString("") { "%02x".format(it) }
}
