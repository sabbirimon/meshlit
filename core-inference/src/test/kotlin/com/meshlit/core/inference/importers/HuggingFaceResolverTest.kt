package com.meshlit.core.inference.importers

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HuggingFaceResolverTest {

    private class FakeFetcher(
        private val getBody: String? = null,
        private val getError: Throwable? = null,
        private val headInfo: HeadInfo = HeadInfo(contentLength = 4096, linkedSha256 = null),
    ) : HttpFetcher {
        var lastGet: String? = null
        var lastHead: String? = null
        override suspend fun get(url: String): MeshlitResult<String> {
            lastGet = url
            getError?.let { return MeshlitResult.Failure(MeshlitError.Network("test", it)) }
            return if (getBody != null) MeshlitResult.Success(getBody) else MeshlitResult.Failure(
                MeshlitError.Network("test.no_body")
            )
        }
        override suspend fun head(url: String): HeadInfo {
            lastHead = url
            return headInfo
        }
    }

    @Test
    fun blank_repo_returns_invalid_failure() = runBlocking {
        val r = HuggingFaceResolver(FakeFetcher()).resolve("", "x.gguf")
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("hf.blank_repo", (r as MeshlitResult.Failure).error.tag)
    }

    @Test
    fun blank_file_returns_invalid_failure() = runBlocking {
        val r = HuggingFaceResolver(FakeFetcher()).resolve("TheBloke/Llama-2-7B-GGUF", "")
        assertTrue(r is MeshlitResult.Failure)
        assertEquals("hf.blank_file", (r as MeshlitResult.Failure).error.tag)
    }

    @Test
    fun missing_file_in_siblings_returns_invalid_failure() = runBlocking {
        val body = """{"siblings":[{"rfilename":"README.md"}]}"""
        val r = HuggingFaceResolver(FakeFetcher(getBody = body))
            .resolve("TheBloke/Llama-2-7B-GGUF", "no-such-file.gguf")
        assertTrue(r is MeshlitResult.Failure)
        val tag = (r as MeshlitResult.Failure).error.tag
        assertTrue("tag should mention not_found: $tag", tag.startsWith("hf.file_not_found"))
    }

    @Test
    fun successful_resolve_pulls_url_format_and_size() = runBlocking {
        val body = """{"siblings":[
            {"rfilename":"llama-2-7b.Q4_K_M.gguf","size":4080218936}
        ]}"""
        val fetcher = FakeFetcher(
            getBody = body,
            headInfo = HeadInfo(contentLength = 4080218936, linkedSha256 = "abc123")
        )
        val r = HuggingFaceResolver(fetcher).resolve(
            repoId = "TheBloke/Llama-2-7B-Chat-GGUF",
            fileName = "llama-2-7b.Q4_K_M.gguf",
            reference = "main",
        )
        assertTrue(r is MeshlitResult.Success)
        val src = (r as MeshlitResult.Success).value
        assertEquals("llama-2-7b.Q4_K_M.gguf", src.displayName)
        assertEquals(
            "https://huggingface.co/TheBloke/Llama-2-7B-Chat-GGUF/resolve/main/llama-2-7b.Q4_K_M.gguf",
            src.url,
        )
        assertEquals("abc123", src.sha256)
        assertEquals(4080218936L, src.sizeBytes)
        assertEquals(ImportedModelFormat.GGUF, src.format)
    }

    @Test
    fun sibling_size_is_used_when_head_omits_content_length() = runBlocking {
        val body = """{"siblings":[{"rfilename":"m.onnx","size":12345}]}"""
        val fetcher = FakeFetcher(
            getBody = body,
            headInfo = HeadInfo(contentLength = null, linkedSha256 = null),
        )
        val r = HuggingFaceResolver(fetcher).resolve("foo/bar", "m.onnx")
        assertTrue(r is MeshlitResult.Success)
        val src = (r as MeshlitResult.Success).value
        assertEquals(12345L, src.sizeBytes)
        assertNull(src.sha256)
        assertEquals(ImportedModelFormat.ONNX, src.format)
    }

    @Test
    fun head_sha_overrides_missing_sibling_size() = runBlocking {
        val body = """{"siblings":[{"rfilename":"m.gguf"}]}"""
        val fetcher = FakeFetcher(
            getBody = body,
            headInfo = HeadInfo(contentLength = 9999, linkedSha256 = "deadbeef"),
        )
        val r = HuggingFaceResolver(fetcher).resolve("foo/bar", "m.gguf")
        val src = (r as MeshlitResult.Success).value
        assertEquals("deadbeef", src.sha256)
        assertEquals(9999L, src.sizeBytes)
    }

    @Test
    fun nested_siblings_match_by_basename() = runBlocking {
        val body = """{"siblings":[{"rfilename":"subdir/m.gguf"}]}"""
        val fetcher = FakeFetcher(getBody = body)
        val r = HuggingFaceResolver(fetcher).resolve("foo/bar", "m.gguf")
        assertTrue(r is MeshlitResult.Success)
        assertEquals(
            "https://huggingface.co/foo/bar/resolve/main/m.gguf",
            (r as MeshlitResult.Success).value.url,
        )
    }
}

class ImportedModelFormatTest {
    @Test
    fun detection_from_filename() {
        assertEquals(ImportedModelFormat.GGUF, ImportedModelFormat.fromFileName("foo.gguf"))
        assertEquals(ImportedModelFormat.GGML, ImportedModelFormat.fromFileName("foo.ggml"))
        assertEquals(ImportedModelFormat.ONNX, ImportedModelFormat.fromFileName("foo.onnx"))
        assertEquals(ImportedModelFormat.UNKNOWN, ImportedModelFormat.fromFileName("foo.bin"))
        // Case-insensitive
        assertEquals(ImportedModelFormat.GGUF, ImportedModelFormat.fromFileName("FOO.GGUF"))
    }
}
