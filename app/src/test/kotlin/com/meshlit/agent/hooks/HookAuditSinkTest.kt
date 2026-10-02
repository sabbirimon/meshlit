package com.meshlit.agent.hooks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * JVM tests for [HookAuditSink]. We exercise the JSONL write path,
 * the secret-redaction rules, and the size cap on `vars_summary`.
 *
 * No Robolectric — the sink is pure file I/O + a `ReentrantLock`,
 * which a [TemporaryFolder] handles directly.
 */
class HookAuditSinkTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun sink(): HookAuditSink {
        val file = File(tempFolder.root, "hooks-audit.jsonl")
        return HookAuditSink(auditFile = file)
    }

    @Test
    fun `append writes a parseable JSON line with required fields`() {
        val s = sink()
        s.append(
            hookId = "hook-1",
            hookName = "log-tool",
            triggerTag = "pre_tool_call",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 42,
            vars = mapOf("tool_name" to "fs.read", "call_id" to "abc"),
            error = null,
        )
        val lines = s.readAllLines()
        assertEquals(1, lines.size)
        val line = lines.single()
        // Sanity check — every line is one JSON object containing
        // the fields the schema documents.
        assertTrue(line.startsWith("{"))
        assertTrue(line.endsWith("}"))
        for (key in listOf(
            "\"ts\"",
            "\"hook_id\"",
            "\"hook_name\"",
            "\"trigger\"",
            "\"outcome\"",
            "\"duration_ms\"",
            "\"vars_summary\"",
            "\"error\":null",
        )) {
            assertTrue("line missing $key: $line", line.contains(key))
        }
    }

    @Test
    fun `redact replaces bearer tokens in vars_summary`() {
        val s = sink()
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "post_tool_call",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 1,
            vars = mapOf(
                "tool_name" to "fs.read",
                "bearer_token" to "sk-abcdef0123456789",
            ),
            error = null,
        )
        val lines = s.readAllLines()
        assertEquals(1, lines.size)
        // The redaction rule replaces the value of a `bearer_token`
        // key with `<redacted>`. The original token must NOT appear.
        assertTrue(lines.single().contains("bearer_token=<redacted>"))
        assertTrue(!lines.single().contains("sk-abcdef0123456789"))
    }

    @Test
    fun `redact masks JWT-shaped values even when key is innocent`() {
        val s = sink()
        // eyJ… is a standard JWT header; our regex matches anywhere
        // a value contains three base64url segments separated by
        // dots. The key here is `response`, which doesn't trip the
        // key-name regex — only the value-shape one does.
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "on_turn_end",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 0,
            vars = mapOf(
                "response" to "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIn0.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c",
            ),
            error = null,
        )
        val line = s.readAllLines().single()
        assertTrue(line.contains("response=<redacted>"))
        assertTrue(!line.contains("SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c"))
    }

    @Test
    fun `vars_summary is truncated at 8KB with truncated suffix`() {
        val s = sink()
        // A 100KB vars map to overflow the AUDIT_MAX_VARS_TOTAL cap.
        val big = buildMap {
            repeat(2000) { i -> put("k$i", "v".repeat(80)) }
        }
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "on_turn_end",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 0,
            vars = big,
            error = null,
        )
        val line = s.readAllLines().single()
        // The truncation suffix is documented in the kdoc.
        assertTrue("line missing truncation suffix: ...$line", line.contains("…(truncated)"))
        // Whole line stays under the cap + a reasonable JSON overhead.
        assertTrue("line too long: ${line.length}", line.length < 9 * 1024)
    }

    @Test
    fun `error field serializes as JSON null when null`() {
        val s = sink()
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "on_inference_end",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 0,
            vars = emptyMap(),
            error = null,
        )
        val line = s.readAllLines().single()
        assertTrue(line.contains("\"error\":null"))
    }

    @Test
    fun `error field carries the exception message when not null`() {
        val s = sink()
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "on_error",
            outcome = HookAuditSink.Outcome.ERROR,
            durationMs = 0,
            vars = emptyMap(),
            error = "boom",
        )
        val line = s.readAllLines().single()
        assertTrue(line.contains("\"error\":\"boom\""))
        assertTrue(line.contains("\"outcome\":\"ERROR\""))
    }

    @Test
    fun `parent directory is created lazily on first write`() {
        val root = tempFolder.root
        val deepFile = File(File(root, "a/b/c"), "hooks-audit.jsonl")
        val parent = deepFile.parentFile
        assertNotNull("deepFile.parentFile should not be null", parent)
        assertTrue(!parent!!.exists())
        val s = HookAuditSink(auditFile = deepFile)
        s.append(
            hookId = "h",
            hookName = "n",
            triggerTag = "on_turn_end",
            outcome = HookAuditSink.Outcome.SUCCESS,
            durationMs = 0,
            vars = emptyMap(),
            error = null,
        )
        assertNotNull(deepFile.parentFile)
        assertTrue(deepFile.parentFile!!.exists())
    }
}