package com.meshlit.core.cloudmcp.android

import android.graphics.Rect

/**
 * One node in the Android accessibility tree. The agent loop
 * flattens the foreground window's tree into a list of these
 * and uses each field to disambiguate clicks (text vs.
 * contentDescription vs. resourceId).
 *
 * @property className Resolved class name (e.g. `android.widget.Button`).
 * @property text Visible text content (or null for icon-only nodes).
 * @property contentDescription Accessibility description
 *   (or null when the node is unlabeled).
 * @property resourceId Fully-qualified resource id (e.g.
 *   `com.google.android.gm:id/subject`).
 * @property bounds Screen-space rectangle in pixels.
 * @property isClickable True when the node responds to taps.
 * @property isEditable True for EditText / focused input fields.
 * @property isPassword True when the node accepts a password
 *   (drives the `AndroidAutomationPermission.sensitive` policy).
 * @property children Nested child nodes. The tree is shallow
 *   (one level of nesting for typical apps) so the LLM can
 *   scan it in a single prompt.
 */
data class AndroidNode(
    val className: String?,
    val text: String?,
    val contentDescription: String?,
    val resourceId: String?,
    val bounds: Rect,
    val isClickable: Boolean,
    val isEditable: Boolean,
    val isPassword: Boolean = false,
    val children: List<AndroidNode> = emptyList(),
)

/**
 * One snapshot of the foreground window. Stores the active
 * package + window class + a flattened-but-nested node tree.
 *
 * The [capturedAtMs] drives the Agent Terminal's "stale" badge
 * — a snapshot older than ~3 seconds marks the Live device pane
 * as "Stale" so the user knows the agent is acting on a stale
 * view of the device.
 *
 * @property packageName Foreground app's package (e.g. `com.google.android.gm`).
 * @property windowClass Top-level window class (e.g.
 *   `com.android.internal.app.AlertActivity`).
 * @property capturedAtMs `System.currentTimeMillis()` at capture.
 * @property nodes Root nodes of the accessibility tree.
 */
data class AndroidSnapshot(
    val packageName: String,
    val windowClass: String,
    val capturedAtMs: Long,
    val nodes: List<AndroidNode>,
) {
    /** Flatten every node into a depth-first list for fast
     *  lookup by descriptor. */
    fun flatten(): List<AndroidNode> = buildList {
        fun visit(n: AndroidNode) {
            add(n)
            n.children.forEach(::visit)
        }
        nodes.forEach(::visit)
    }

    /**
     * Find the first node matching [predicate]. Walks the tree
     * depth-first.
     */
    fun find(predicate: (AndroidNode) -> Boolean): AndroidNode? {
        for (root in nodes) {
            val match = visit(root, predicate)
            if (match != null) return match
        }
        return null
    }

    private fun visit(node: AndroidNode, predicate: (AndroidNode) -> Boolean): AndroidNode? {
        if (predicate(node)) return node
        for (child in node.children) {
            val match = visit(child, predicate)
            if (match != null) return match
        }
        return null
    }

    fun isStale(nowMs: Long, maxAgeMs: Long = 3_000L): Boolean =
        (nowMs - capturedAtMs) > maxAgeMs
}
