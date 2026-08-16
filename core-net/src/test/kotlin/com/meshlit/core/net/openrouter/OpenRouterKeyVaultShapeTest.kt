package com.meshlit.core.net.openrouter

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-logic tests for [OpenRouterKeyVault.Companion.isValidKeyShape].
 *
 * The full vault (Keystore + EncryptedSharedPreferences) requires
 * an Android runtime with a Keystore, which the JVM unit-test
 * harness doesn't have. We isolate the pure logic into a
 * companion-object function so the shape rules are unit-testable
 * without an instrumented environment.
 */
class OpenRouterKeyVaultShapeTest {

    @Test
    fun keys_with_correct_prefix_and_charset_pass() {
        assertTrue(isValid("sk-or-v1-abcdef0123456789ABCDEF0123456789"))
        assertTrue(isValid("sk-or-abcdef0123456789ABCDEF0123456789"))
    }

    @Test
    fun keys_with_incorrect_prefix_fail() {
        assertFalse(isValid("sk-openai-1234567890"))
        assertFalse(isValid("pk-or-v1-1234567890"))
        assertFalse(isValid("1234567890"))
    }

    @Test
    fun keys_with_invalid_characters_fail() {
        assertFalse(isValid("sk-or-v1-hello world!"))
        assertFalse(isValid("sk-or-v1-a@b${'$'}c"))
    }

    @Test
    fun keys_shorter_than_20_chars_fail() {
        assertFalse(isValid("sk-or-v1-short"))
        assertFalse(isValid(""))
        assertFalse(isValid("sk-or-v1-19charslong!"))
    }

    @Test
    fun keys_with_dashes_and_underscores_pass() {
        // OpenRouter keys use URL-safe base64-like chars.
        assertTrue(isValid("sk-or-v1-aaaa_bbbb-cccc-DDDD_EEEE-ffff"))
    }

    @Test
    fun trimmed_keys_pass_after_normalize() {
        // The vault's save() trims before validation. The static
        // validator itself doesn't trim — the caller is responsible.
        val raw = "  sk-or-v1-abcdef0123456789ABCDEF0123456789  "
        assertTrue(isValid(raw.trim()))
    }

    private fun isValid(key: String): Boolean =
        OpenRouterKeyVault.isValidKeyShape(key)
}