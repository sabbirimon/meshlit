package com.meshlit.core.mcp.builtin

/**
 * Pure-Kotlin helpers for [InAppTools] that don't depend on
 * Android types. Split out so the unit tests can exercise the
 * PII-masking + structured-arg parsing logic on the JVM without
 * needing a Robolectric context.
 *
 * The class-level design intent: every Android-only call (Cursor
 * iteration, ContextCompat.checkSelfPermission, ContentResolver)
 * stays in [InAppTools]; every pure-function (string transforms,
 * argument parsing, validation) lives here.
 */
object InAppToolsSupport {

    /**
     * Mask a phone number to its last-4 digits. Strips any
     * non-digit prefix and keeps the trailing 4 digits so the
     * agent can still surface enough info to disambiguate a
     * contact (e.g. "...-1234") without leaking the full number.
     *
     * Rules:
     *  - blank input → blank output
     *  - ≤4 digits → return the digits as-is (no point masking)
     *  - >4 digits → `***-***-<last4>`
     */
    fun maskPhone(raw: String): String {
        if (raw.isBlank()) return ""
        val digits = raw.filter { it.isDigit() }
        if (digits.length <= 4) return digits
        return "***-***-${digits.takeLast(4)}"
    }

    /**
     * Map a [ContactsContract.CommonDataKinds.Phone.TYPE_*] integer
     * to its string name. The constant values are stable across
     * Android API levels (TYPE_HOME = 1, TYPE_MOBILE = 2, …).
     *
     * The constants are inlined as literals so this stays
     * testable on the JVM. The wrapper in [InAppTools] uses the
     * official `ContactsContract`-sourced constants — both
     * expect the same wire values.
     */
    @Suppress("MagicNumber")
    fun phoneTypeToName(type: Int): String = when (type) {
        1 -> "home"       // TYPE_HOME
        2 -> "mobile"     // TYPE_MOBILE
        3 -> "work"       // TYPE_WORK
        4 -> "work_fax"   // TYPE_FAX_WORK
        5 -> "home_fax"   // TYPE_FAX_HOME
        6 -> "pager"      // TYPE_PAGER
        7 -> "other"      // TYPE_OTHER
        8 -> "callback"   // TYPE_CALLBACK
        9 -> "car"        // TYPE_CAR
        10 -> "company_main" // TYPE_COMPANY_MAIN
        11 -> "isdn"      // TYPE_ISDN
        12 -> "main"      // TYPE_MAIN
        13 -> "other_fax" // TYPE_OTHER_FAX
        14 -> "radio"     // TYPE_RADIO
        15 -> "telex"     // TYPE_TELEX
        16 -> "tty_tdd"   // TYPE_TTY_TDD
        17 -> "work_mobile" // TYPE_WORK_MOBILE
        18 -> "work_pager" // TYPE_WORK_PAGER
        19 -> "assistant" // TYPE_ASSISTANT
        20 -> "mms"       // TYPE_MMS
        else -> "unknown"
    }

    /**
     * Coerce a `limit` argument value into the parser's
     * accepted range. Returns the caller-provided default when
     * the input is null or non-numeric.
     */
    fun clampLimit(raw: Int?, default: Int, min: Int = 1, max: Int = 500): Int =
        raw?.coerceIn(min, max) ?: default

    /**
     * Coerce a `hoursAhead` argument value into the parser's
     * accepted range. Wider than `limit` because calendar
     * queries can legitimately span a month at the upper end.
     */
    fun clampHoursAhead(raw: Int?, default: Int): Int =
        raw?.coerceIn(1, 720) ?: default
}