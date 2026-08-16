package com.meshlit.notifications

import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * Evaluates whether a given timestamp falls inside the user's
 * Do-Not-Disturb window. The settings live as three DataStore
 * keys (`notif.quiet_hours_start`, `notif.quiet_hours_end`,
 * `notif.quiet_hours_days`); this class turns them into a
 * pure-logic predicate the `NotificationCenter` calls before
 * posting.
 *
 * Three edge cases the test suite locks in:
 *  - **Cross-midnight windows** (e.g. 22 → 7) — handled.
 *  - **Empty days CSV** — never quiet (the user opted out).
 *  - **Days outside the configured set** — not quiet even if
 *    the clock is inside the window.
 *
 * Why pure logic and not a Calendar-wrapped helper: the
 * scheduler is unit-tested with `ZonedDateTime` injection so
 * CI doesn't depend on the device's wall clock.
 */
class DndSchedule(
    val startHour: Int,
    val endHour: Int,
    /** CSV of `1..7` (Mon..Sun). Empty == never quiet. */
    val daysCsv: String,
) {
    init {
        require(startHour in 0..23) { "startHour $startHour not in 0..23" }
        require(endHour in 0..23) { "endHour $endHour not in 0..23" }
    }

    /** The parsed set of days the schedule is active. */
    val activeDays: Set<Int> = daysCsv
        .split(',')
        .mapNotNull { it.trim().toIntOrNull() }
        .filter { it in 1..7 }
        .toSet()

    /**
     * Returns true if [now] is inside the configured quiet window.
     * Pass the user's wall clock (`ZonedDateTime.now(zone)`);
     * tests pass a fixed value.
     */
    fun isQuietNow(now: ZonedDateTime): Boolean {
        if (activeDays.isEmpty()) return false
        val dayOfWeek = now.dayOfWeek.isoDayNumber() // 1=Mon..7=Sun
        if (dayOfWeek !in activeDays) return false
        val hour = now.hour
        return if (startHour == endHour) {
            // Degenerate window: zero-length, treat as never-quiet.
            false
        } else if (startHour < endHour) {
            // Same-day window (e.g. 13 → 17)
            hour in startHour until endHour
        } else {
            // Cross-midnight window (e.g. 22 → 7)
            hour >= startHour || hour < endHour
        }
    }

    private fun DayOfWeek.isoDayNumber(): Int = when (this) {
        DayOfWeek.MONDAY -> 1
        DayOfWeek.TUESDAY -> 2
        DayOfWeek.WEDNESDAY -> 3
        DayOfWeek.THURSDAY -> 4
        DayOfWeek.FRIDAY -> 5
        DayOfWeek.SATURDAY -> 6
        DayOfWeek.SUNDAY -> 7
    }

    companion object {
        /** Default schedule: 22:00 → 07:00 every day of the week. */
        val DEFAULT = DndSchedule(
            startHour = 22,
            endHour = 7,
            daysCsv = "1,2,3,4,5,6,7",
        )
    }
}