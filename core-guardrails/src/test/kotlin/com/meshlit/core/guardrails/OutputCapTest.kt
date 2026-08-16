package com.meshlit.core.guardrails

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OutputCapTest {

    private val cap = OutputCap()

    @Test
    fun below_cap_returns_input_unchanged() {
        val text = "Short sentence."
        val out = cap.cap(text, maxTokens = 100)
        assertEquals(text, out)
    }

    @Test
    fun exact_cap_returns_input_unchanged() {
        // 16 chars / 4 = 4 tokens under the default tokenizer.
        val text = "0123456789abcdef"
        val out = cap.cap(text, maxTokens = 4)
        assertEquals(text, out)
    }

    @Test
    fun over_cap_truncates_at_last_whitespace() {
        val text = "alpha beta gamma delta epsilon zeta eta theta"
        val out = cap.cap(text, maxTokens = 4)
        // 4 tokens ≈ 16 chars under the default tokenizer.
        assertTrue("output should be truncated: <$out>", out.length < text.length)
        // No half-word at the end.
        assertTrue("must not end mid-word", out.last() == ' ' || !out.last().isLowerCase() || out.endsWith("alpha") || out.endsWith("beta") || out.endsWith("gamma"))
    }

    @Test
    fun zero_or_negative_cap_returns_empty() {
        assertEquals("", cap.cap("anything", maxTokens = 0))
        assertEquals("", cap.cap("anything", maxTokens = -3))
    }

    @Test
    fun custom_tokenizer_is_used_when_provided() {
        // Custom tokenizer that counts words.
        val text = "one two three four five"
        val wordCounter: (String) -> Int = { it.split(" ").size }
        val out = cap.cap(text, maxTokens = 3, tokenizer = wordCounter)
        // Binary search finds the longest prefix whose word count ≤ 3.
        // With the whitespace-boundary rule, the last space inside the
        // budget falls back to a hard cut when no complete word fits
        // beyond the last space — result is "one two".
        assertEquals("one two", out)
    }

    @Test
    fun custom_tokenizer_with_byte_counting() {
        val text = "abcdefghij" // 10 bytes
        val byteCounter: (String) -> Int = { it.length }
        assertEquals(text, cap.cap(text, maxTokens = 10, tokenizer = byteCounter))
        assertEquals("abcde", cap.cap(text, maxTokens = 5, tokenizer = byteCounter))
    }

    @Test
    fun hard_cut_when_no_whitespace_in_window() {
        // Token budget so small no whitespace fits — binary search
        // picks the longest prefix and the whitespace rule falls back
        // to a hard cut (lastSpace <= truncated.length / 2).
        val text = "abcdefghijklmnopqrstuvwxyz"
        val out = cap.cap(text, maxTokens = 2)
        // 2 tokens under default ≈ 8 chars.
        assertTrue("should be ≤8 chars: <$out>", out.length <= 8)
    }
}