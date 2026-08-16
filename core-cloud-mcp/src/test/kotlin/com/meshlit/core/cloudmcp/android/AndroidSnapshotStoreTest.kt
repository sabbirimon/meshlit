package com.meshlit.core.cloudmcp.android

import android.graphics.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Tests for [AndroidSnapshotStore] — verifies last-snapshot-per-
 * package semantics, foreground resolution, and clear.
 */
class AndroidSnapshotStoreTest {

    @Test
    fun put_and_get_per_package() {
        val store = AndroidSnapshotStore()
        val snap = makeSnapshot("com.example.app", "Activity1")
        store.put(snap)
        assertEquals(snap, store.get("com.example.app"))
    }

    @Test
    fun foreground_returns_most_recent() {
        val store = AndroidSnapshotStore()
        val old = makeSnapshot("com.example.app", "Activity1", capturedAtMs = 1_000L)
        val new = makeSnapshot("com.example.other", "Activity2", capturedAtMs = 2_000L)
        store.put(old)
        store.put(new)
        assertEquals(new, store.foreground())
    }

    @Test
    fun empty_store_returns_null() {
        val store = AndroidSnapshotStore()
        assertNull(store.get("nope"))
        assertNull(store.foreground())
    }

    @Test
    fun clear_drops_all() {
        val store = AndroidSnapshotStore()
        store.put(makeSnapshot("com.example.app", "X"))
        store.clear()
        assertNull(store.get("com.example.app"))
    }

    @Test
    fun find_walks_tree_depth_first() {
        val parent = AndroidNode(
            className = "android.widget.FrameLayout",
            text = null,
            contentDescription = null,
            resourceId = null,
            bounds = Rect(0, 0, 100, 100),
            isClickable = false,
            isEditable = false,
            children = listOf(
                AndroidNode(
                    className = "android.widget.Button",
                    text = "Submit",
                    contentDescription = null,
                    resourceId = null,
                    bounds = Rect(0, 0, 50, 50),
                    isClickable = true,
                    isEditable = false,
                ),
                AndroidNode(
                    className = "android.widget.EditText",
                    text = "hello",
                    contentDescription = null,
                    resourceId = null,
                    bounds = Rect(0, 0, 100, 50),
                    isClickable = false,
                    isEditable = true,
                ),
            ),
        )
        val snap = AndroidSnapshot(
            packageName = "com.example.app",
            windowClass = "Activity",
            capturedAtMs = System.currentTimeMillis(),
            nodes = listOf(parent),
        )
        val submit = snap.find { it.text == "Submit" }
        assertNotNull(submit)
        assertEquals("android.widget.Button", submit!!.className)
    }

    @Test
    fun is_stale_after_threshold() {
        val now = System.currentTimeMillis()
        val snap = makeSnapshot("com.example.app", "X", capturedAtMs = now - 5_000L)
        assert(snap.isStale(now))
        val fresh = makeSnapshot("com.example.app", "X", capturedAtMs = now)
        assert(!fresh.isStale(now))
    }

    private fun makeSnapshot(
        pkg: String,
        cls: String,
        capturedAtMs: Long = System.currentTimeMillis(),
    ): AndroidSnapshot {
        val node = AndroidNode(
            className = "android.widget.FrameLayout",
            text = null,
            contentDescription = null,
            resourceId = null,
            bounds = Rect(0, 0, 100, 100),
            isClickable = false,
            isEditable = false,
        )
        return AndroidSnapshot(
            packageName = pkg,
            windowClass = cls,
            capturedAtMs = capturedAtMs,
            nodes = listOf(node),
        )
    }
}