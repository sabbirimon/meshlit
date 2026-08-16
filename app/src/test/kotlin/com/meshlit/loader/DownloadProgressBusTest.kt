package com.meshlit.loader

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the global DownloadProgressBus — the single-slot
 * download aggregator that the GlobalLoaderBanner observes.
 */
class DownloadProgressBusTest {

    @Test
    fun start_emits_tick_with_zero_fraction() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start(displayName = "Qwen3.5 0.8B", totalBytes = 500_000_000L)
        val tick = bus.tick.value
        assertNotNull(tick)
        assertEquals("Qwen3.5 0.8B", tick!!.displayName)
        assertEquals(0f, tick.fraction, 0.001f)
        assertEquals(0L, tick.bytesDownloaded)
        assertEquals(500_000_000L, tick.totalBytes)
        assertEquals(DownloadStage.Downloading, tick.stage)
        handle.complete()
    }

    @Test
    fun update_advances_fraction() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start("Qwen3.5", totalBytes = 100L)
        handle.update(bytesDownloaded = 25L)
        assertEquals(0.25f, bus.tick.value!!.fraction, 0.001f)
        handle.update(bytesDownloaded = 50L)
        assertEquals(0.5f, bus.tick.value!!.fraction, 0.001f)
        handle.update(bytesDownloaded = 100L)
        assertEquals(1.0f, bus.tick.value!!.fraction, 0.001f)
        handle.complete()
    }

    @Test
    fun complete_sets_stage_to_done() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start("X", totalBytes = 100L)
        handle.complete()
        val tick = bus.tick.value
        assertNotNull(tick)
        assertEquals(DownloadStage.Done, tick!!.stage)
        assertEquals(1f, tick.fraction, 0.001f)
        bus.clear()
        assertNull(bus.tick.value)
    }

    @Test
    fun fail_sets_stage_to_failed_with_message() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start("X", totalBytes = 100L)
        handle.fail(reason = "404 not_found")
        val tick = bus.tick.value
        assertEquals(DownloadStage.Failed, tick!!.stage)
        assertEquals("404 not_found", tick.errorMessage)
    }

    @Test
    fun late_updates_from_superseded_handle_are_ignored() = runBlocking {
        val bus = DownloadProgressBus()
        val first = bus.start("First", totalBytes = 100L)
        first.update(bytesDownloaded = 50L)
        // A new download starts and supersedes `first`.
        val second = bus.start("Second", totalBytes = 200L)
        second.update(bytesDownloaded = 100L)
        // Now `first` sends a stale update — it must be ignored.
        first.update(bytesDownloaded = 99L)
        val tick = bus.tick.value!!
        assertEquals("Second", tick.displayName)
        assertEquals(0.5f, tick.fraction, 0.001f)
        first.complete() // also ignored
        assertEquals(DownloadStage.Downloading, bus.tick.value!!.stage)
    }

    @Test
    fun clear_resets_to_null() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start("X", totalBytes = 100L)
        handle.complete()
        assertNotNull(bus.tick.value)
        bus.clear()
        assertNull(bus.tick.value)
    }

    @Test
    fun unknown_total_bytes_does_not_invert_fraction() = runBlocking {
        val bus = DownloadProgressBus()
        val handle = bus.start("X", totalBytes = null)
        handle.update(bytesDownloaded = 5_000_000L, totalBytes = null)
        // Fraction stays at 0 because total is unknown — we
        // can't divide by null. The bar will use bytesDownloaded
        // alone as the visual cue (an indeterminate-with-bytes
        // strip).
        val tick = bus.tick.value!!
        assertEquals(0f, tick.fraction, 0.001f)
        assertEquals(5_000_000L, tick.bytesDownloaded)
    }

    @Test
    fun snapshot_returns_current_set() = runBlocking {
        val bus = DownloadProgressBus()
        assertNull("empty bus is null", bus.tick.value)
        val handle = bus.start("X", totalBytes = 100L)
        val snap = bus.tick.value
        assertNotNull(snap)
        assertTrue(snap!!.id.isNotBlank())
        handle.complete()
    }
}