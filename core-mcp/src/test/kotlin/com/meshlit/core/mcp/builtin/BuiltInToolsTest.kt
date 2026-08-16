package com.meshlit.core.mcp.builtin

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BuiltInToolsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun files_list_returns_children_inside_allowed_root() = runBlocking {
        val root = tmp.newFolder("app")
        root.resolve("a.txt").writeText("a")
        root.resolve("sub").mkdir()
        val policy = FileSystemPolicy(allowedRoots = listOf(root))
        val tool = FilesListTool(policy)
        val result = tool.spec().handler(buildJsonObject { put("path", root.absolutePath) })
        assertTrue("expected Json result, got $result", result is com.meshlit.core.mcp.McpToolResult.Json)
        val arr = (result as com.meshlit.core.mcp.McpToolResult.Json).value
            as kotlinx.serialization.json.JsonArray
        assertEquals(2, arr.size)
        val names = arr.map { (it as kotlinx.serialization.json.JsonObject)["name"] }
            .map { (it as kotlinx.serialization.json.JsonPrimitive).content }
            .toSet()
        assertTrue("a.txt" in names)
        assertTrue("sub" in names)
    }

    @Test
    fun files_list_denies_path_outside_allowed_root() = runBlocking {
        val policy = FileSystemPolicy(allowedRoots = listOf(tmp.newFolder("safe")))
        val tool = FilesListTool(policy)
        val result = tool.spec().handler(buildJsonObject {
            put("path", "/etc/passwd")
        })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Error)
        assertEquals(
            com.meshlit.core.mcp.McpToolResult.ErrorCode.PERMISSION_DENIED,
            (result as com.meshlit.core.mcp.McpToolResult.Error).code,
        )
    }

    @Test
    fun files_read_returns_text() = runBlocking {
        val root = tmp.newFolder("app")
        val file = root.resolve("note.md")
        file.writeText("# hello")
        val policy = FileSystemPolicy(allowedRoots = listOf(root))
        val tool = FilesReadTool(policy)
        val result = tool.spec().handler(buildJsonObject { put("path", file.absolutePath) })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Text)
        assertEquals("# hello", (result as com.meshlit.core.mcp.McpToolResult.Text).text)
    }

    @Test
    fun files_read_rejects_oversized_file() = runBlocking {
        val root = tmp.newFolder("app")
        val file = root.resolve("big.txt")
        file.writeText("x".repeat(2048))
        val policy = FileSystemPolicy(allowedRoots = listOf(root))
        val tool = FilesReadTool(policy)
        val result = tool.spec().handler(buildJsonObject {
            put("path", file.absolutePath)
            put("maxBytes", 512)
        })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Error)
        assertEquals(
            com.meshlit.core.mcp.McpToolResult.ErrorCode.IO_ERROR,
            (result as com.meshlit.core.mcp.McpToolResult.Error).code,
        )
    }

    @Test
    fun shell_exec_runs_echo() = runBlocking {
        val policy = ShellPolicy()
        val tool = ShellExecTool(policy)
        val result = tool.spec().handler(buildJsonObject {
            put("command", "echo")
            put("args", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("hi"))
            })
        })
        assertTrue("expected Json, got $result", result is com.meshlit.core.mcp.McpToolResult.Json)
        val obj = (result as com.meshlit.core.mcp.McpToolResult.Json).value
            as kotlinx.serialization.json.JsonObject
        assertEquals(0, (obj["exitCode"] as kotlinx.serialization.json.JsonPrimitive).content.toInt())
        assertTrue((obj["stdout"] as kotlinx.serialization.json.JsonPrimitive).content.contains("hi"))
    }

    @Test
    fun shell_exec_denies_command_not_in_allowlist() = runBlocking {
        val policy = ShellPolicy()
        val tool = ShellExecTool(policy)
        val result = tool.spec().handler(buildJsonObject { put("command", "rm") })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Error)
        assertEquals(
            com.meshlit.core.mcp.McpToolResult.ErrorCode.PERMISSION_DENIED,
            (result as com.meshlit.core.mcp.McpToolResult.Error).code,
        )
    }

    @Test
    fun shell_exec_denies_shell_metacharacter_in_arg() = runBlocking {
        val policy = ShellPolicy()
        val tool = ShellExecTool(policy)
        val result = tool.spec().handler(buildJsonObject {
            put("command", "echo")
            put("args", kotlinx.serialization.json.buildJsonArray {
                add(kotlinx.serialization.json.JsonPrimitive("a;b"))
            })
        })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Error)
        assertEquals(
            com.meshlit.core.mcp.McpToolResult.ErrorCode.PERMISSION_DENIED,
            (result as com.meshlit.core.mcp.McpToolResult.Error).code,
        )
    }

    @Test
    fun model_info_returns_error_when_provider_returns_null() = runBlocking {
        val tool = ModelInfoTool(provider = { null })
        val result = tool.spec().handler(buildJsonObject { })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Error)
        assertEquals(
            com.meshlit.core.mcp.McpToolResult.ErrorCode.IO_ERROR,
            (result as com.meshlit.core.mcp.McpToolResult.Error).code,
        )
    }

    @Test
    fun model_info_returns_payload_when_provider_returns_value() = runBlocking {
        val tool = ModelInfoTool(provider = {
            buildJsonObject {
                put("name", "qwen2.5-1.5b-instruct-q4_k_m")
                put("format", "gguf")
            }
        })
        val result = tool.spec().handler(buildJsonObject { })
        assertTrue(result is com.meshlit.core.mcp.McpToolResult.Json)
        val obj = (result as com.meshlit.core.mcp.McpToolResult.Json).value
            as kotlinx.serialization.json.JsonObject
        assertEquals(
            "qwen2.5-1.5b-instruct-q4_k_m",
            (obj["name"] as kotlinx.serialization.json.JsonPrimitive).content,
        )
    }
}