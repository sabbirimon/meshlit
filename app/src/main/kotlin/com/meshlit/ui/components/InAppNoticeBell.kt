package com.meshlit.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.notifications.InAppNoticeCenter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * In-app warning bell. Renders an icon + unread count badge in the
 * trailing slot of the host header (Jobs, Models, etc.) and opens
 * a [ModalBottomSheet] when tapped.
 *
 * Distinct from the OS notification tray:
 *  - **Always visible** — doesn't depend on POST_NOTIFICATIONS, DND,
 *    or the user opening the system shade.
 *  - **In-app only** — when the app is backgrounded, the bell is
 *    gone; users have to come back to see pending notices. The OS
 *    notification tray is the durable surface for backgrounded
 *    events.
 *
 * Visible severity:
 *  - 0 unread: outlined bell, muted gray.
 *  - 1-9 unread: filled bell + red badge with the count.
 *  - 10+ unread: filled bell + "9+" badge.
 *  - At least one [Severity.Error]: red bell instead of gray.
 *
 * See also:
 *  - [com.meshlit.notifications.InAppNoticeCenter] — the underlying store.
 *  - [com.meshlit.notifications.NotificationCenter] — the OS tray version.
 */
@Composable
fun InAppNoticeBell(
    app: MeshlitApplication,
    modifier: Modifier = Modifier,
) {
    val unread by app.inAppNoticeCenter.unreadCountFlow.collectAsState()
    val notices by app.inAppNoticeCenter.noticesFlow.collectAsState()
    val hasError = notices.any {
        it.severity == InAppNoticeCenter.Severity.Error
    }

    var sheetOpen by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(
            onClick = { sheetOpen = true },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = if (unread > 0 || hasError) {
                    Icons.Filled.NotificationsActive
                } else {
                    Icons.Filled.NotificationsNone
                },
                contentDescription = if (unread > 0) {
                    "$unread unread in-app notices"
                } else {
                    "In-app notices"
                },
                tint = when {
                    hasError -> MaterialTheme.colorScheme.error
                    unread > 0 -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(20.dp),
            )
            if (unread > 0) {
                UnreadBadge(count = unread, modifier = Modifier.align(Alignment.TopEnd))
            }
        }
    }

    if (sheetOpen) {
        InAppNoticeSheet(
            app = app,
            onDismiss = {
                sheetOpen = false
                app.inAppNoticeCenter.markAllRead()
            },
        )
    }
}

@Composable
private fun UnreadBadge(count: Int, modifier: Modifier = Modifier) {
    val text = if (count > 9) "9+" else count.toString()
    Box(
        modifier = modifier
            .padding(top = 4.dp, end = 4.dp)
            .size(16.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.error),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
            ),
            color = MaterialTheme.colorScheme.onError,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InAppNoticeSheet(
    app: MeshlitApplication,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val notices by app.inAppNoticeCenter.noticesFlow.collectAsState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "In-app notices",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                if (notices.isNotEmpty()) {
                    Button(
                        onClick = { app.inAppNoticeCenter.clearAll() },
                    ) {
                        Text("Clear all")
                    }
                }
            }
            Spacer(Modifier.size(8.dp))
            if (notices.isEmpty()) {
                Text(
                    text = "Nothing to report. The OS notification tray will " +
                        "still receive inference / model-import events.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 480.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(notices, key = { it.id }) { notice ->
                        NoticeRow(
                            notice = notice,
                            onDismiss = { app.inAppNoticeCenter.dismiss(notice.id) },
                            onAction = {
                                notice.onAction?.invoke()
                                app.inAppNoticeCenter.dismiss(notice.id)
                            },
                        )
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
        }
    }
}

@Composable
private fun NoticeRow(
    notice: InAppNoticeCenter.Notice,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
) {
    val (dotColor, dotIcon) = when (notice.severity) {
        InAppNoticeCenter.Severity.Info -> MaterialTheme.colorScheme.primary to null
        InAppNoticeCenter.Severity.Warning -> Color(0xFFE0A300) to Icons.Outlined.WarningAmber
        InAppNoticeCenter.Severity.Error -> MaterialTheme.colorScheme.error to Icons.Outlined.WarningAmber
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (dotIcon != null) {
                    Icon(
                        imageVector = dotIcon,
                        contentDescription = notice.severity.name,
                        tint = dotColor,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(dotColor),
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = notice.title,
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatRelative(notice.atMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Text(
                text = notice.body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (!notice.actionLabel.isNullOrBlank()) {
                Spacer(Modifier.size(8.dp))
                Button(
                    onClick = onAction,
                ) {
                    Text(notice.actionLabel)
                }
            }
        }
    }
}

private val RELATIVE_FMT = SimpleDateFormat("HH:mm:ss", Locale.US)
private fun formatRelative(atMs: Long): String = RELATIVE_FMT.format(Date(atMs))