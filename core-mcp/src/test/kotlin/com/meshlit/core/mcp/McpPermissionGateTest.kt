package com.meshlit.core.mcp

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the bundled-MCP permission gate. The gate is consulted
 * by per-tool handlers (see [InAppTools]); the registry itself does
 * not enforce permissions in `invoke()`. These tests cover the
 * gate's own state surface:
 *
 *  - `snapshot()` returns an immutable view that doesn't mutate
 *    when `grant()` / `revoke()` are applied.
 *  - `isGranted()` returns a synchronous boolean for known /
 *    unknown resources.
 *  - `denyIfNotGranted()` returns a `PERMISSION_DENIED` error
 *    envelope for ungranted resources and `null` for granted ones.
 *  - `setGranted()` replaces the granted set atomically and is
 *    idempotent.
 *
 * The previous version of this file exercised the gate via
 * `McpToolRegistry(initialGate = gate)`, but the registry
 * constructor no longer takes a gate — the matching registry-level
 * tests were removed along with the constructor parameter.
 */
class McpPermissionGateTest {

    @Test
    fun snapshot_returns_immutable_view() = runBlocking {
        val gate = McpPermissionGate(initialGranted = setOf("notes"))
        val s1 = gate.snapshot()
        assertEquals(setOf("notes"), s1)
        gate.grant("calendar")
        val s2 = gate.snapshot()
        assertEquals(setOf("notes", "calendar"), s2)
        // s1 must not have changed — the snapshot is independent.
        assertEquals(setOf("notes"), s1)
    }

    @Test
    fun isGranted_returns_true_for_known_false_for_unknown() {
        val gate = McpPermissionGate(initialGranted = setOf("notes"))
        assertTrue(gate.isGranted("notes"))
        assertFalse(gate.isGranted("calendar"))
        assertFalse(gate.isGranted(""))
    }

    @Test
    fun denyIfNotGranted_returns_null_when_granted() {
        val gate = McpPermissionGate(initialGranted = setOf("notes"))
        // Direct check: ungranted returns a non-null Error.
        val denied = gate.denyIfNotGranted("calendar")
        assertNotNull(denied)
        assertEquals(McpToolResult.ErrorCode.PERMISSION_DENIED, denied!!.code)
        assertTrue(denied.message.contains("calendar"))
        // Granted resource returns null (no error).
        val ok = gate.denyIfNotGranted("notes")
        assertEquals(null, ok)
    }

    @Test
    fun setGranted_replaces_atomically() = runBlocking {
        val gate = McpPermissionGate(initialGranted = setOf("notes"))
        gate.setGranted(setOf("calendar", "contacts"))
        assertEquals(setOf("calendar", "contacts"), gate.snapshot())
        // Idempotent — re-applying the same set is a no-op.
        gate.setGranted(setOf("calendar", "contacts"))
        assertEquals(setOf("calendar", "contacts"), gate.snapshot())
    }
}