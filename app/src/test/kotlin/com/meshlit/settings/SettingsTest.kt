package com.meshlit.settings

import com.meshlit.notifications.DndSchedule
import com.meshlit.notifications.NotificationHistoryLog
import com.meshlit.settings.visibility.RowDescriptor
import com.meshlit.settings.visibility.SettingsVisibility
import com.meshlit.settings.visibility.Visibility
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * Pure-logic tests for the Phase 4.x Settings menu rewrite.
 *
 * Locks in:
 *  - [NotificationHistoryLog] ring-buffer behaviour + JSON round-trip.
 *  - [DndSchedule] cross-midnight handling, day-of-week filter,
 *    empty-CSV semantics.
 *  - [SettingsVisibility] filtering semantics.
 *
 * No Android dependencies — runs on the host JVM.
 */
class SettingsTest {

    // ────────────────────────── NotificationHistoryLog ─────────────────

    @Test
    fun history_push25_keepsLast20_inFifoOrder() {
        val log = NotificationHistoryLog()
        var json = "[]"
        for (i in 1..25) {
            json = log.append(
                json,
                NotificationHistoryLog.Entry(
                    atMs = i.toLong(),
                    categoryId = "test",
                    title = "title-$i",
                    outcome = "ok-$i",
                ),
            )
        }
        val entries = log.recent(json, limit = 20)
        assertEquals(20, entries.size)
        // Oldest kept is #6 (1..5 evicted FIFO), newest is #25.
        assertEquals(6L, entries.first().atMs)
        assertEquals(25L, entries.last().atMs)
        // Order is oldest-first.
        assertEquals(
            (6..25).map { it.toLong() },
            entries.map { it.atMs },
        )
    }

    @Test
    fun history_clear_yieldsEmptyJson() {
        val log = NotificationHistoryLog()
        val json = log.append(
            "[]",
            NotificationHistoryLog.Entry(
                atMs = 1L,
                categoryId = "x",
                title = "t",
                outcome = "ok",
            ),
        )
        assertNotEquals("[]", json)
        assertEquals("[]", log.clear())
    }

    @Test
    fun history_recentWithZeroLimit_returnsAll() {
        val log = NotificationHistoryLog()
        var json = "[]"
        for (i in 1..3) {
            json = log.append(
                json,
                NotificationHistoryLog.Entry(
                    atMs = i.toLong(),
                    categoryId = "x",
                    title = "t$i",
                    outcome = "ok",
                ),
            )
        }
        val all = log.recent(json, limit = 0)
        assertEquals(3, all.size)
    }

    @Test
    fun history_corruptJson_fallsBackToEmpty() {
        val log = NotificationHistoryLog()
        // Bogus payload — must not throw.
        val entries = log.recent("not-json-at-all", limit = 5)
        assertEquals(0, entries.size)
    }

    // ────────────────────────── DndSchedule ────────────────────────────

    @Test
    fun dnd_defaultSchedule_22To7_quietAt2330Tuesday() {
        val schedule = DndSchedule.DEFAULT
        val tuesday2330 = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 23, 30),
            ZoneId.of("UTC"),
        )
        assertTrue(schedule.isQuietNow(tuesday2330))
    }

    @Test
    fun dnd_defaultSchedule_notQuietAtNoonSaturday() {
        val schedule = DndSchedule.DEFAULT
        val saturdayNoon = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 8, 12, 0),
            ZoneId.of("UTC"),
        )
        assertFalse(schedule.isQuietNow(saturdayNoon))
    }

    @Test
    fun dnd_crossMidnight_quietAt6AM() {
        val schedule = DndSchedule.DEFAULT
        val tuesday6am = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 6, 0),
            ZoneId.of("UTC"),
        )
        assertTrue(schedule.isQuietNow(tuesday6am))
    }

    @Test
    fun dnd_emptyDaysCsv_neverQuiet() {
        val schedule = DndSchedule(startHour = 22, endHour = 7, daysCsv = "")
        val anyMidnight = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 23, 30),
            ZoneId.of("UTC"),
        )
        assertFalse(schedule.isQuietNow(anyMidnight))
    }

    @Test
    fun dnd_weekendOnly_notQuietOnTuesday() {
        val schedule = DndSchedule(
            startHour = 22,
            endHour = 7,
            daysCsv = "6,7", // Sat, Sun
        )
        val tuesday2300 = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 23, 0),
            ZoneId.of("UTC"),
        )
        val saturday2300 = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 8, 23, 0),
            ZoneId.of("UTC"),
        )
        assertFalse(schedule.isQuietNow(tuesday2300))
        assertTrue(schedule.isQuietNow(saturday2300))
    }

    @Test
    fun dnd_sameDayWindow_quietInside() {
        // 13 → 17 same-day window.
        val schedule = DndSchedule(
            startHour = 13,
            endHour = 17,
            daysCsv = "1,2,3,4,5,6,7",
        )
        val inside = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 14, 0),
            ZoneId.of("UTC"),
        )
        val outside = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 19, 0),
            ZoneId.of("UTC"),
        )
        assertTrue(schedule.isQuietNow(inside))
        assertFalse(schedule.isQuietNow(outside))
    }

    @Test
    fun dnd_zeroLengthWindow_neverQuiet() {
        val schedule = DndSchedule(
            startHour = 14,
            endHour = 14,
            daysCsv = "1,2,3,4,5,6,7",
        )
        val inside = ZonedDateTime.of(
            LocalDateTime.of(2026, 8, 11, 14, 30),
            ZoneId.of("UTC"),
        )
        assertFalse(schedule.isQuietNow(inside))
    }

    @Test
    fun dnd_invalidHours_throws() {
        val result = runCatching {
            DndSchedule(startHour = 25, endHour = 0, daysCsv = "")
        }
        assertTrue(result.isFailure)
    }

    // ────────────────────────── SettingsVisibility ─────────────────────

    @Test
    fun visibility_simpleModeHidesAdvanced() {
        val rows = listOf(
            RowDescriptor(Visibility.SIMPLE) {},
            RowDescriptor(Visibility.ADVANCED) {},
            RowDescriptor(Visibility.SIMPLE) {},
        )
        val visible = SettingsVisibility.filter(rows, simpleMode = true)
        assertEquals(2, visible.size)
        assertTrue(visible.all { it.tier == Visibility.SIMPLE })
    }

    @Test
    fun visibility_advancedModeShowsEverything() {
        val rows = listOf(
            RowDescriptor(Visibility.SIMPLE) {},
            RowDescriptor(Visibility.ADVANCED) {},
            RowDescriptor(Visibility.ADVANCED) {},
            RowDescriptor(Visibility.SIMPLE) {},
        )
        val visible = SettingsVisibility.filter(rows, simpleMode = false)
        assertEquals(4, visible.size)
    }

    @Test
    fun visibility_emptyInput_returnsEmpty() {
        assertEquals(0, SettingsVisibility.filter(emptyList(), simpleMode = true).size)
        assertEquals(0, SettingsVisibility.filter(emptyList(), simpleMode = false).size)
    }
}