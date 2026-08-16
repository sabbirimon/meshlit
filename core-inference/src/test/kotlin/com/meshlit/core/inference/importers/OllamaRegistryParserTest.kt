package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OllamaRegistryParserTest {

    private class FakeFetcher(
        private val responses: Map<String, String> = emptyMap(),
        private val failures: Set<String> = emptySet(),
    ) : HttpFetcher {
        override suspend fun get(url: String): MeshlitResult<String> {
            if (url in failures) return MeshlitResult.Failure(MeshlitError.Network("test"))
            val body = responses[url] ?: return MeshlitResult.Failure(MeshlitError.Network("test.no_body:$url"))
            return MeshlitResult.Success(body)
        }
        override suspend fun head(url: String): HeadInfo = HeadInfo(null, null)
    }

    @Test
    fun parseCatalog_returns_namespaced_entries() {
        val body = """{"repositories":[
            {"name":"library/llama2"},
            {"name":"library/codellama"},
            {"name":"alice/mymodel"}
        ]}"""
        val entries = OllamaRegistryParser().parseCatalog(body)
        assertEquals(3, entries.size)
        assertEquals(OllamaRegistryParser.OllamaCatalogEntry("library", "llama2"), entries[0])
        assertEquals(OllamaRegistryParser.OllamaCatalogEntry("alice", "mymodel"), entries[2])
    }

    @Test
    fun parseCatalog_drops_malformed_names() {
        val body = """{"repositories":[
            {"name":"only-one-segment"},
            {"name":"good/one"}
        ]}"""
        val entries = OllamaRegistryParser().parseCatalog(body)
        assertEquals(1, entries.size)
        assertEquals("good", entries[0].namespace)
    }

    @Test
    fun parseCatalog_handles_invalid_json() {
        assertTrue(OllamaRegistryParser().parseCatalog("not json").isEmpty())
        assertTrue(OllamaRegistryParser().parseCatalog("""{"repositories":null}""").isEmpty())
    }

    @Test
    fun parseTagNames_extracts_names() {
        val body = """{"tags":[{"name":"latest"},{"name":"q4_0"},{"name":"q8_0"}]}"""
        assertEquals(listOf("latest", "q4_0", "q8_0"), OllamaRegistryParser().parseTagNames(body))
    }

    @Test
    fun listCatalog_succeeds_with_fake_fetcher() = runBlocking {
        val baseUrl = "https://registry.ollama.ai"
        val fetcher = FakeFetcher(
            responses = mapOf(
                "$baseUrl/v2/_catalog" to """{"repositories":[{"name":"library/llama2"}]}"""
            )
        )
        val r = OllamaRegistryParser(baseRegistryUrl = baseUrl, http = fetcher).listCatalog()
        assertTrue(r is MeshlitResult.Success)
        val entries = (r as MeshlitResult.Success).value
        assertEquals(1, entries.size)
        assertEquals("library", entries[0].namespace)
    }

    @Test
    fun listTags_returns_empty_when_no_tags() = runBlocking {
        val baseUrl = "https://registry.ollama.ai"
        val fetcher = FakeFetcher(
            responses = mapOf(
                "$baseUrl/v2/library/empty/tags/list" to """{"tags":[]}"""
            )
        )
        val r = OllamaRegistryParser(baseRegistryUrl = baseUrl, http = fetcher).listTags("library", "empty")
        assertTrue(r is MeshlitResult.Success)
        assertEquals(emptyList<String>(), (r as MeshlitResult.Success).value)
    }

    @Test
    fun catalogToSources_maps_each_entry_to_unknown_format() = runBlocking {
        val baseUrl = "https://registry.ollama.ai"
        val fetcher = FakeFetcher(
            responses = mapOf(
                "$baseUrl/v2/_catalog" to """{"repositories":[{"name":"library/llama2"},{"name":"library/codellama"}]}"""
            )
        )
        val r = OllamaRegistryParser(baseRegistryUrl = baseUrl, http = fetcher).catalogToSources()
        assertTrue(r is MeshlitResult.Success)
        val list = (r as MeshlitResult.Success).value
        assertEquals(2, list.size)
        assertEquals("library/llama2", list[0].displayName)
        assertEquals(ImportedModelFormat.UNKNOWN, list[0].format)
        assertTrue(list[0].url.startsWith(baseUrl))
    }
}
