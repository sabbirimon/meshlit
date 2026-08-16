package com.meshlit.notifications

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Lightweight in-app notification channel that lives entirely inside
 * the app's UI. Distinct from [NotificationCenter] (which posts OS
 * notifications and requires POST_NOTIFICATIONS + per-channel opt-in):
 *
 *  - **No system permission** — always works, even on Android 13+
 *    where the user denied POST_NOTIFICATIONS.
 *  - **Always visible** — doesn't get silenced by DND / system focus
 *    modes that would mute OS notifications.
 *  - **Tied to a visible bell** — the user can always see the count
 *    of unread notices in the app bar and tap to expand.
 *
 * Backs the in-app "warning bell" UI on the Jobs header. The OS-level
 * [NotificationCenter] still fires in parallel — important events go
 * to both surfaces.
 *
 * Persistence: ring buffer in memory only (last [DEFAULT_CAP] notices).
 * Survives configuration changes via [MeshlitApplication.inAppNoticeCenter]
 * (singleton on Application), but is wiped on process death. The OS
 * notification tray is the durable surface; this bell is for "right now".
 *
 * Severity:
 *  - [Severity.Info]   — informational, blue dot.
 *  - [Severity.Warning] — yellow dot, e.g. transient boot crash recovered.
 *  - [Severity.Error]   — red dot, e.g. something the user must look at.
 */
class InAppNoticeCenter(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private val log = logger("InAppNoticeCenter")

    /** Ring buffer of all currently visible notices (oldest first). */
    private val notices = ConcurrentLinkedDeque<Notice>()

    private val _state = MutableStateFlow<List<Notice>>(emptyList())
    val noticesFlow: StateFlow<List<Notice>> = _state.asStateFlow()

    private val _unreadCount = MutableStateFlow(0)
    val unreadCountFlow: StateFlow<Int> = _unreadCount.asStateFlow()

    /**
     * Push a notice onto the in-app stack. Re-emits the StateFlow
     * so any subscribed bell updates immediately.
     *
     * The [actionLabel] / [onAction] pair lets the caller attach a
     * primary action button on the notice (e.g. "View" / "Open
     * settings"). The user dismisses with the close (✕) icon.
     */
    fun push(
        severity: Severity,
        title: String,
        body: String,
        actionLabel: String? = null,
        onAction: (() -> Unit)? = null,
    ) {
        scope.launch {
            val notice = Notice(
                id = nextId(),
                atMs = System.currentTimeMillis(),
                severity = severity,
                title = title,
                body = body,
                actionLabel = actionLabel,
                onAction = onAction,
                dismissed = false,
            )
            notices.addFirst(notice)
            // Trim to ring buffer.
            while (notices.size > DEFAULT_CAP) notices.pollLast()
            _state.value = notices.toList()
            _unreadCount.update { it + 1 }
            log.info(
                "in_app_notice.push",
                "pushed",
                mapOf(
                    "severity" to severity.name,
                    "title" to title,
                ),
            )
        }
    }

    /** Mark all current notices as read. Does not dismiss them —
     *  the user still sees them in the bell sheet. */
    fun markAllRead() {
        _unreadCount.value = 0
    }

    /** Dismiss a notice by id. Removes it from the stack. */
    fun dismiss(id: Long) {
        val removed = notices.removeAll { it.id == id }
        if (removed) {
            _state.value = notices.toList()
        }
    }

    /** Wipe all notices. Used by Settings → Notifications → "Clear all". */
    fun clearAll() {
        notices.clear()
        _state.value = emptyList()
        _unreadCount.value = 0
    }

    private fun nextId(): Long = counter.incrementAndGet()

    private companion object {
        const val DEFAULT_CAP = 32
        val counter = java.util.concurrent.atomic.AtomicLong(0L)
    }

    /** Severity badge on the bell + sheet. */
    enum class Severity { Info, Warning, Error }

    /** Immutable snapshot of a notice as displayed in the bell sheet. */
    data class Notice(
        val id: Long,
        val atMs: Long,
        val severity: Severity,
        val title: String,
        val body: String,
        val actionLabel: String?,
        val onAction: (() -> Unit)?,
        val dismissed: Boolean,
    )
}

/**
 * Composable helper: subscribe to the in-app notice center and read
 * the current list / unread count.
 */
@Composable
fun rememberInAppNotices(app: MeshlitApplication): Pair<List<InAppNoticeCenter.Notice>, Int> {
    val notices by app.inAppNoticeCenter.noticesFlow.collectAsState()
    val unread by app.inAppNoticeCenter.unreadCountFlow.collectAsState()
    return notices to unread
}

/** Convenience wrapper around [InAppNoticeCenter.push] that callers
 *  can call from non-Composable contexts. */
fun MeshlitApplication.pushInAppNotice(
    severity: InAppNoticeCenter.Severity,
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    inAppNoticeCenter.push(severity, title, body, actionLabel, onAction)
}
