package com.meshlit.core.mcp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMcpServerTest {

    @Test
    fun namespaced_tool_name_uses_server_name_as_prefix() {
        val server = UserMcpServer(
            id = "github",
            name = "github",
            command = "/usr/local/bin/github-mcp",
        )
        assertEquals("github.list_repos", server.namespaced("list_repos"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_id_throws() {
        UserMcpServer(id = "", name = "x", command = "/bin/true")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_name_throws() {
        UserMcpServer(id = "x", name = "", command = "/bin/true")
    }

    @Test(expected = IllegalArgumentException::class)
    fun blank_command_throws() {
        UserMcpServer(id = "x", name = "x", command = "")
    }

    @Test(expected = IllegalArgumentException::class)
    fun timeout_below_floor_throws() {
        UserMcpServer(id = "x", name = "x", command = "/bin/true", timeoutMs = 50)
    }

    @Test(expected = IllegalArgumentException::class)
    fun timeout_above_ceiling_throws() {
        UserMcpServer(id = "x", name = "x", command = "/bin/true", timeoutMs = 100_000)
    }

    @Test
    fun defaults_apply_when_only_minimum_required() {
        val s = UserMcpServer(id = "x", name = "x", command = "/bin/true")
        assertEquals(emptyList<String>(), s.args)
        assertEquals(emptyMap<String, String>(), s.env)
        assertEquals(10_000, s.timeoutMs)
        assertTrue(s.enabled)
    }
}