package com.meshlit.core.guardrails

/**
 * Truncate text at a token budget. The default tokenizer is the
 * standard 4-characters-per-token heuristic — cheap, conservative
 * (over-estimates), and good enough for an upper-bound cap. Callers
 * with a real tokenizer (tiktoken, llama.cpp's tokenizer, …) can
 * inject their own function.
 */
class OutputCap(
    private val defaultTokenizer: (String) -> Int = { text -> (text.length + 3) / 4 },
) {

    /**
     * Truncate [text] so its token count, as estimated by
     * [tokenizer], does not exceed [maxTokens]. Returns the original
     * input when the budget is sufficient.
     *
     * Truncation prefers the last whitespace boundary within the
     * window so we don't leave a half-word at the end. When no such
     * boundary exists (rare), a hard cut at the exact token window is
     * used.
     */
    fun cap(text: String, maxTokens: Int, tokenizer: (String) -> Int = defaultTokenizer): String {
        if (maxTokens <= 0) return ""
        if (tokenizer(text) <= maxTokens) return text

        // Binary-search the longest prefix whose token count is ≤ maxTokens.
        var lo = 0
        var hi = text.length
        while (lo < hi) {
            val mid = (lo + hi + 1) ushr 1
            if (tokenizer(text.substring(0, mid)) <= maxTokens) {
                lo = mid
            } else {
                hi = mid - 1
            }
        }
        val truncated = text.substring(0, lo)
        val lastSpace = truncated.lastIndexOf(' ')
        return if (lastSpace > truncated.length / 2) truncated.substring(0, lastSpace) else truncated
    }
}
