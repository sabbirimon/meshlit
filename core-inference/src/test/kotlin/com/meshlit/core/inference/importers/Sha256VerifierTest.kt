package com.meshlit.core.inference.importers

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256VerifierTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private val verifier = Sha256Verifier()

    @Test
    fun hashOf_returns_known_sha_for_hello() {
        val file = tempFolder.newFile("hello.txt").apply { writeText("hello") }
        // SHA-256("hello") = 2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824
        val expected = "2cf24dba5fb0a30e26e83b2ac5b9e29e1b161e5c1fa7425e73043362938b9824"
        assertEquals(expected, verifier.hashOf(file))
    }

    @Test
    fun hashOf_empty_file_returns_known_sha() {
        val file = tempFolder.newFile("empty.txt").apply { writeText("") }
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855
        val expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        assertEquals(expected, verifier.hashOf(file))
    }

    @Test
    fun hashOf_returns_lowercase_hex() {
        val file = tempFolder.newFile("x.txt").apply { writeText("x") }
        val hex = verifier.hashOf(file)
        assertEquals(hex.lowercase(), hex)
        assertEquals(64, hex.length)
    }

    @Test
    fun hashOf_large_file_matches_java_security() {
        val file = tempFolder.newFile("big.bin")
        val bytes = ByteArray(64 * 1024) { (it % 251).toByte() }
        file.writeBytes(bytes)
        val hex = verifier.hashOf(file)
        assertEquals(64, hex.length)
        val java = java.security.MessageDigest.getInstance("SHA-256").run {
            update(bytes)
            digest().joinToString("") { "%02x".format(it) }
        }
        assertEquals(java, hex)
    }

    @Test
    fun shaMismatchException_carries_expected_and_actual() {
        val ex = ShaMismatchException(expected = "aaa", actual = "bbb", sizeBytes = 42)
        assertEquals("aaa", ex.expected)
        assertEquals("bbb", ex.actual)
        assertEquals(42L, ex.sizeBytes)
        assertTrue("message should mention mismatch", (ex.message ?: "").contains("mismatch"))
    }
}
