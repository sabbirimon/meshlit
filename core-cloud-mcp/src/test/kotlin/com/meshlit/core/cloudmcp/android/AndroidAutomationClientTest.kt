package com.meshlit.core.cloudmcp.android

import com.meshlit.core.cloudmcp.ToolRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [AndroidAutomationClient] — verifies the nine
 * `app_*` tools are registered and that each [invoke] call
 * maps to the right [AutomationRequest] variant.
 */
class AndroidAutomationClientTest {

    @Test
    fun registers_nine_tools_under_one_provider() {
        val client = client()
        val registry = ToolRegistry()
        client.register(registry)
        val tools = registry.ordered().filter { it.providerId == AndroidAutomationClient.PROVIDER_ID }
        assertEquals(9, tools.size)
        val names = tools.map { it.name }.toSet()
        assertTrue(AndroidAutomationClient.TOOL_LIST in names)
        assertTrue(AndroidAutomationClient.TOOL_OPEN in names)
        assertTrue(AndroidAutomationClient.TOOL_SNAPSHOT in names)
        assertTrue(AndroidAutomationClient.TOOL_CLICK in names)
        assertTrue(AndroidAutomationClient.TOOL_TYPE in names)
        assertTrue(AndroidAutomationClient.TOOL_BACK in names)
        assertTrue(AndroidAutomationClient.TOOL_HOME in names)
        assertTrue(AndroidAutomationClient.TOOL_WAIT_FOR in names)
        assertTrue(AndroidAutomationClient.TOOL_SCREENSHOT in names)
    }

    @Test
    fun click_with_text_maps_to_click_request() = runBlocking {
        var captured: AutomationRequest? = null
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { req ->
                captured = req
                AutomationResponse.UnitResponse(ok = true)
            },
        )
        client.invoke(
            toolName = AndroidAutomationClient.TOOL_CLICK,
            args = buildJsonObject {
                put("text", "Archive")
                put("targetPackage", "com.google.android.gm")
            },
        )
        assertNotNull(captured)
        assertTrue(captured is AutomationRequest.ClickRequest)
        assertEquals("Archive", (captured as AutomationRequest.ClickRequest).text)
    }

    @Test
    fun type_with_password_flag_sets_is_password_field() = runBlocking {
        var captured: AutomationRequest? = null
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { req ->
                captured = req
                AutomationResponse.UnitResponse(ok = true)
            },
        )
        client.invoke(
            toolName = AndroidAutomationClient.TOOL_TYPE,
            args = buildJsonObject {
                put("text", "secret")
                put("isPasswordField", JsonPrimitive(true))
                put("targetPackage", "com.google.android.gm")
            },
        )
        val type = captured as AutomationRequest.TypeRequest
        assertEquals("secret", type.text)
        assertTrue(type.isPasswordField)
    }

    @Test
    fun wait_for_clamps_timeout_to_30_seconds() = runBlocking {
        var captured: AutomationRequest? = null
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { req ->
                captured = req
                AutomationResponse.UnitResponse(ok = true)
            },
        )
        client.invoke(
            toolName = AndroidAutomationClient.TOOL_WAIT_FOR,
            args = buildJsonObject {
                put("text", "Done")
                put("timeoutMs", JsonPrimitive(60_000L))
            },
        )
        val wait = captured as AutomationRequest.WaitForRequest
        assertEquals(30_000L, wait.timeoutMs)
    }

    @Test
    fun unknown_tool_returns_error() = runBlocking {
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { AutomationResponse.UnitResponse(ok = true) },
        )
        val result = client.invoke("app_nonexistent", buildJsonObject {})
        assertFalse((result as AutomationResponse.UnitResponse).ok)
        assertEquals("unknown tool: app_nonexistent", result.error)
    }

    @Test
    fun open_app_maps_to_open_app_request() = runBlocking {
        var captured: AutomationRequest? = null
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { req ->
                captured = req
                AutomationResponse.UnitResponse(ok = true)
            },
        )
        client.invoke(
            toolName = AndroidAutomationClient.TOOL_OPEN,
            args = buildJsonObject {
                put("packageName", "com.google.android.gm")
                put("targetPackage", "com.google.android.gm")
            },
        )
        assertTrue(captured is AutomationRequest.OpenApp)
    }

    @Test
    fun back_and_home_map_to_their_requests() = runBlocking {
        var lastRequest: AutomationRequest? = null
        val client = AndroidAutomationClient(
            snapshotStore = AndroidSnapshotStore(),
            bridgeProvider = { null },
            invoke = { req ->
                lastRequest = req
                AutomationResponse.UnitResponse(ok = true)
            },
        )
        client.invoke(AndroidAutomationClient.TOOL_BACK, buildJsonObject {})
        assertTrue(lastRequest is AutomationRequest.BackRequest)
        client.invoke(AndroidAutomationClient.TOOL_HOME, buildJsonObject {})
        assertTrue(lastRequest is AutomationRequest.HomeRequest)
    }

    private fun client(): AndroidAutomationClient = AndroidAutomationClient(
        snapshotStore = AndroidSnapshotStore(),
        bridgeProvider = { null },
        invoke = { AutomationResponse.UnitResponse(ok = true) },
    )
}