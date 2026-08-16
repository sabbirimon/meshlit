package com.meshlit.core.mcp

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the bundled-MCP permission gate. These cover the
 * three load-bearing paths:
 *
 *  - Ungranted call → returns `PERMISSION_DENIED` without
 *    invoking the handler.
 *  - Granted call → handler runs.
 *  - Tool without `requiredResource` → gate is bypassed even
 *    when no resources are granted (the gate never blocks a
 *    tool that doesn't ask for permission).
 */
class McpPermissionGateTest {

    @Test
    fun ungranted_call_returns_permission_denied() = runBlocking {
        val gate = McpPermissionGate(initialGranted = emptySet())
        val reg = McpToolRegistry(initialGate = gate)
        reg.register(McpToolSpec(
            name = "notes_list",
            description = "List notes",
            requiredResource = InAppResource.Notes.id,
            handler = { McpToolResult.Text("should not run") },
        ))
        val result = reg.invoke(McpToolRequest(name = "notes_list", arguments = JsonNull))
        assertTrue("expected Error, got $result", result is McpToolResult.Error)
        assertEquals(
            McpToolResult.ErrorCode.PERMISSION_DENIED,
            (result as McpToolResult.Error).code,
        )
        assertTrue(result.message.contains("notes"))
    }

    @Test
    fun granted_call_invokes_handler() = runBlocking {
        val gate = McpPermissionGate(initialGranted = setOf(InAppResource.Notes.id))
        val reg = McpToolRegistry(initialGate = gate)
        reg.register(McpToolSpec(
            name = "notes_list",
            description = "List notes",
            requiredResource = InAppResource.Notes.id,
            handler = { McpToolResult.Text("invoked") },
        ))
        val result = reg.invoke(McpToolRequest(name = "notes_list", arguments = JsonNull))
        assertTrue(result is McpToolResult.Text)
        assertEquals("invoked", (result as McpToolResult.Text).text)
    }

    @Test
    fun tool_without_required_resource_skips_gate() = runBlocking {
        val gate = McpPermissionGate(initialGranted = emptySet())
        val reg = McpToolRegistry(initialGate = gate)
        reg.register(McpToolSpec(
            name = "echo",
            description = "Echo",
            handler = { args -> McpToolResult.Text(args.toString()) },
        ))
        val result = reg.invoke(McpToolRequest(
            name = "echo",
            arguments = buildJsonObject { put("msg", "hi") },
        ))
        assertTrue(result is McpToolResult.Text)
        assertTrue((result as McpToolResult.Text).text.contains("\"msg\":\"hi\""))
    }

    @Test
    fun runtime_grant_unblocks_call() = runBlocking {
        val gate = McpPermissionGate(initialGranted = emptySet())
        val reg = McpToolRegistry(initialGate = gate)
        reg.register(McpToolSpec(
            name = "calendar_upcoming",
            description = "Upcoming events",
            requiredResource = InAppResource.Calendar.id,
            handler = { McpToolResult.Text("cal") },
        ))
        // First call is denied.
        val denied = reg.invoke(McpToolRequest(name = "calendar_upcoming"))
        assertTrue(denied is McpToolResult.Error)
        // After grant, the same call succeeds.
        gate.grant(InAppResource.Calendar.id)
        val ok = reg.invoke(McpToolRequest(name = "calendar_upcoming"))
        assertTrue(ok is McpToolResult.Text)
        assertEquals("cal", (ok as McpToolResult.Text).text)
    }

    @Test
    fun runtime_revoke_blocks_call() = runBlocking {
        val gate = McpPermissionGate(initialGranted = setOf(InAppResource.Contacts.id))
        val reg = McpToolRegistry(initialGate = gate)
        reg.register(McpToolSpec(
            name = "contacts_search",
            description = "Search contacts",
            requiredResource = InAppResource.Contacts.id,
            handler = { McpToolResult.Text("ok") },
        ))
        val ok = reg.invoke(McpToolRequest(name = "contacts_search"))
        assertTrue(ok is McpToolResult.Text)
        gate.revoke(InAppResource.Contacts.id)
        val denied = reg.invoke(McpToolRequest(name = "contacts_search"))
        assertTrue(denied is McpToolResult.Error)
        assertEquals(
            McpToolResult.ErrorCode.PERMISSION_DENIED,
            (denied as McpToolResult.Error).code,
        )
    }

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