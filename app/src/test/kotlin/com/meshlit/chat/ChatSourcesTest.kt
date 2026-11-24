package com.meshlit.chat

import com.meshlit.core.mcp.McpToolResult
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class ChatSourcesTest {
    private fun result(body: String) = McpToolResult.Json(Json.parseToJsonElement(body))

    @Test fun `search links preserve returned provenance without claiming a page fetch`() {
        val sources = chatSourcesFromTool("web_search", result("""{
            "status":"ok","provider":"Brave Search","fetchedAtMs":1234,
            "results":[{"title":"<b>Real title</b>","url":"https://example.com/article?q=one","snippet":"snippet"},
            {"title":"Duplicate","url":"https://example.com/article?q=one"},
            {"title":"HTTP source","url":"http://example.org/page"}]}"""))
        assertEquals(2, sources.size)
        assertEquals(ChatSourceKind.SEARCH_RESULT, sources.first().kind)
        assertEquals("Brave Search", sources.first().provider)
        assertEquals(1234L, sources.first().fetchedAtMs)
        assertNull(sources.first().httpStatus)
        assertEquals("example.com", chatSourceHost(sources.first().url))
    }

    @Test fun `crawler uses actual final URL HTTP state and truncation`() {
        val source = chatSourcesFromTool("crawl_url", result("""{
            "state":"ok","status":200,"url":"https://example.com/old",
            "final_url":"https://example.org/actual","title":"Fetched title",
            "retrieved_at":"2026-10-31T08:00:00+00:00","truncated":true,"markdown":"text"
        }""")).single()
        assertEquals("https://example.org/actual", source.url)
        assertEquals(ChatSourceKind.PAGE, source.kind)
        assertEquals(200, source.httpStatus)
        assertEquals(true, source.truncated)
        assertEquals("2026-10-31T08:00:00+00:00", source.retrievedAt)
    }

    @Test fun `failed tools model text and unrelated tool fields never become sources`() {
        assertTrue(chatSourcesFromTool("crawl_url", result("""{"state":"blocked","status":403,"url":"https://example.org"}""")).isEmpty())
        assertTrue(chatSourcesFromTool("web_search", result("""{"status":"error","results":[{"url":"https://example.org"}]}""")).isEmpty())
        assertTrue(chatSourcesFromTool("web_search", McpToolResult.Text("https://invented.example")).isEmpty())
        assertTrue(chatSourcesFromTool("unrelated_tool", result("""{"status":"ok","results":[{"url":"https://example.org"}]}""")).isEmpty())
        assertTrue(chatSourcesFromTool("web_search", result("""{"status":[],"results":{}}""")).isEmpty())
    }

    @Test fun `unsafe navigation schemes credentials controls and overlong URLs are rejected`() {
        listOf("javascript:alert(1)", "file:///private/key", "data:text/html,hello", "//example.org/path",
            "https://user:password@example.org/path", "https://example.org/\n", "https://example.org/" + "x".repeat(4096)).forEach {
            assertFalse(it, safeChatSourceUrl(it))
        }
        val bad = ChatWebSource("javascript:alert(1)", "title", ChatSourceKind.SEARCH_RESULT)
        assertTrue(mergeChatSources(listOf(bad), emptyList()).isEmpty())
    }

    @Test fun `sources persist with messages and old history remains compatible`() {
        val source = ChatWebSource("https://example.org/page", "Title", ChatSourceKind.PAGE, httpStatus = 200, truncated = false)
        val message = ChatMessage(role = "assistant", text = "Answer", webSources = listOf(source))
        assertEquals(message, Json.decodeFromString<ChatMessage>(Json.encodeToString(message)))
        assertTrue(Json.decodeFromString<ChatMessage>("""{"role":"assistant","text":"Old answer"}""").webSources.isEmpty())
    }

    @Test fun `bounded merge upgrades a search hit only after actual retrieval`() {
        val hit = ChatWebSource("https://example.org/page", "Search title", ChatSourceKind.SEARCH_RESULT)
        val page = hit.copy(kind = ChatSourceKind.PAGE, httpStatus = 200)
        assertEquals(page, mergeChatSources(listOf(hit), listOf(page)).single())
        assertEquals(page, mergeChatSources(listOf(page), listOf(hit)).single())
        val first = (1..10).map { hit.copy(url = "https://example.org/$it") }
        val next = (11..20).map { hit.copy(url = "https://example.org/$it") }
        assertEquals(12, mergeChatSources(first, next).size)
    }

    @Test fun `activity names distinguish web reads from local search`() {
        assertEquals("Searching the web", chatToolActivity("web_search"))
        assertEquals("Reading a web page", chatToolActivity("crawl_url"))
        assertEquals("Searching local content", chatToolActivity("app_search"))
        assertNull(chatToolActivity(null))
        assertNull(chatToolActivity("search_access"))
    }
}
