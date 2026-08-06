package com.meshlit.core.guardrails

/**
 * First-line defence against prompt injection. Looks for known
 * patterns that have appeared in published injection corpora and
 * strips or flags them.
 *
 * The matches are advisory — the [GuardrailsPipeline] decides
 * whether a hit rejects the prompt or just gets logged. Returning
 * [ScrubResult] keeps the policy decision at the pipeline layer.
 */
class PromptScrubber {
    data class ScrubResult(
        val cleaned: String,
        val hits: List<Hit>,
        val redacted: List<Redaction>,
    ) {
        val hasInjection: Boolean get() = hits.isNotEmpty()
        val hasPii: Boolean get() = redacted.isNotEmpty()
    }

    data class Hit(val tag: String, val snippet: String)
    data class Redaction(val tag: String, val original: String, val replacement: String)

    private val injectionPatterns: List<Pair<String, Regex>> = listOf(
        "ignore_previous" to Regex(
            "(?i)\\b(ignore|disregard|forget)\\s+(all\\s+)?(previous|prior|above)\\s+(instructions|prompts?|rules?)\\b"
        ),
        "system_override" to Regex(
            "(?i)\\b(system\\s*:\\s*you\\s+are|new\\s+instructions\\s*:|override\\s+system)\\b"
        ),
        "developer_mode" to Regex(
            "(?i)\\b(developer\\s+mode|jailbreak|DAN\\s+mode)\\b"
        ),
        "base64_payload" to Regex("\\b[A-Za-z0-9+/]{40,}={0,2}\\b"),
        "role_marker" to Regex("(?i)^\\s*(system|assistant|user)\\s*:\\s"),
    )

    private val piiPatterns: List<Pair<String, Regex>> = listOf(
        "email" to Regex("\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}\\b"),
        "phone_us" to Regex("\\b\\+?1?[\\s.-]?\\(?\\d{3}\\)?[\\s.-]?\\d{3}[\\s.-]?\\d{4}\\b"),
        "ssn" to Regex("\\b\\d{3}-\\d{2}-\\d{4}\\b"),
        "ipv4" to Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"),
        "credit_card" to Regex("\\b(?:\\d[ -]?){13,19}\\b"),
    )

    fun scrub(input: String): ScrubResult {
        val hits = mutableListOf<Hit>()
        var working = input
        for ((tag, pattern) in injectionPatterns) {
            val match = pattern.find(working)
            if (match != null) {
                hits += Hit(tag = tag, snippet = match.value.take(80))
                // Strip the matched region so downstream stages don't
                // see the same token twice.
                working = working.replace(match.value, " ")
            }
        }
        val redactions = mutableListOf<Redaction>()
        for ((tag, pattern) in piiPatterns) {
            working = pattern.replace(working) { mr ->
                val original = mr.value
                if (tag == "credit_card" && !luhnValid(original.filter { it.isDigit() })) {
                    mr.value
                } else {
                    val placeholder = "[REDACTED:$tag]"
                    redactions += Redaction(tag = tag, original = original, replacement = placeholder)
                    placeholder
                }
            }
        }
        return ScrubResult(cleaned = working.trim(), hits = hits, redacted = redactions)
    }

    private fun luhnValid(digits: String): Boolean {
        if (digits.length < 13) return false
        var sum = 0
        var alt = false
        for (i in digits.length - 1 downTo 0) {
            val n = digits[i].digitToInt()
            var v = n
            if (alt) {
                v *= 2
                if (v > 9) v -= 9
            }
            sum += v
            alt = !alt
        }
        return sum % 10 == 0
    }
}
