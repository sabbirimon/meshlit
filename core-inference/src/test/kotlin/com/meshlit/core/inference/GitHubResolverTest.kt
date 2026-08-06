package com.meshlit.core.inference

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class GitHubResolverTest {

    @Test fun resolves_blob_url() {
        val resolved = GitHubResolver.resolve(
            "https://github.com/QwenLM/Qwen2.5/blob/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
        )
        assertNotNull(resolved)
        assertEquals(
            "https://raw.githubusercontent.com/QwenLM/Qwen2.5/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            resolved!!.rawUrl,
        )
        assertEquals("QwenLM", resolved.owner)
        assertEquals("Qwen2.5", resolved.repository)
        assertEquals("main", resolved.reference)
        assertEquals("qwen2.5-1.5b-instruct-q4_k_m.gguf", resolved.path)
    }

    @Test fun resolves_raw_url() {
        val resolved = GitHubResolver.resolve(
            "https://github.com/owner/repo/raw/v1.0/sub/dir/model.gguf",
        )
        assertNotNull(resolved)
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/v1.0/sub/dir/model.gguf",
            resolved!!.rawUrl,
        )
        assertEquals("v1.0", resolved.reference)
        assertEquals("sub/dir/model.gguf", resolved.path)
    }

    @Test fun resolves_already_raw_url() {
        val resolved = GitHubResolver.resolve(
            "https://raw.githubusercontent.com/owner/repo/main/model.onnx",
        )
        assertNotNull(resolved)
        assertEquals(
            "https://raw.githubusercontent.com/owner/repo/main/model.onnx",
            resolved!!.rawUrl,
        )
    }

    @Test fun rejects_http_scheme() {
        assertNull(GitHubResolver.resolve("http://github.com/owner/repo/blob/main/m.gguf"))
    }

    @Test fun rejects_non_github_host() {
        assertNull(GitHubResolver.resolve("https://example.com/owner/repo/blob/main/m.gguf"))
    }

    @Test fun rejects_repo_root_url() {
        assertNull(GitHubResolver.resolve("https://github.com/owner/repo"))
    }

    @Test fun rejects_blob_url_without_path() {
        assertNull(GitHubResolver.resolve("https://github.com/owner/repo/blob/main"))
    }

    @Test fun preserves_commit_sha_reference() {
        val resolved = GitHubResolver.resolve(
            "https://github.com/owner/repo/blob/1a2b3c4d5e6f7890abcdef1234567890abcdef12/m.gguf",
        )
        assertNotNull(resolved)
        assertEquals("1a2b3c4d5e6f7890abcdef1234567890abcdef12", resolved!!.reference)
    }
}
