package com.meshlit.ui.screens.mcp

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.MeshlitApplication
import com.meshlit.core.mcp.BundledMcpServer
import com.meshlit.core.mcp.InAppResource
import com.meshlit.core.mcp.McpPermissionGate
import kotlinx.coroutines.launch

/**
 * Phase 4.x — `Commit 34b: Pending-OAuth Approval Card`.
 *
 * Renders a small inline card on top of the chat list whenever
 * the agent invoked an InApp MCP tool whose resource is not
 * granted. The card lists every pending resource with a `Grant`
 * button. Clicking `Grant` adds the resource to the persisted
 * set, which immediately flips the gate's StateFlow so the next
 * tool call runs; clicking `Dismiss for now` keeps the chip off.
 *
 * The card is itself **non-blocking** — it does not pause the
 * chat. It surfaces the pending resource and lets the user
 * decide. Hiding the card only stops the visual reminder; the
 * tool's earlier `permission_denied` response is already on the
 * wire.
 *
 * Visibility rule: at least one [InAppResource] from
 * [BundledMcpServer.InApp] is ungranted AND the in-app bundle
 * master toggle is on. Otherwise the composable returns
 * immediately so there's no overhead.
 */
@Composable
fun McpOAuthApprovalCard(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val gate = rememberGate() ?: return
    val scope = rememberCoroutineScope()

    val inAppEnabled by settings.inAppMcpEnabledFlow.collectAsState(initial = false)
    val granted by gate.granted.collectAsState()
    if (!inAppEnabled) return
    val pending = InAppResource.values().filter { it.id !in granted }
    if (pending.isEmpty()) return

    val border = BorderStroke(
        width = 1.dp,
        color = MaterialTheme.colorScheme.primary,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
        border = border,
        tonalElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Outlined.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .weight(1f),
                ) {
                    Text(
                        text = "InApp MCP needs permission",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = "Grant access so the agent can use these tools in future turns.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f),
                    )
                }
            }
            pending.forEach { resource ->
                PendingResourceRow(
                    resource = resource,
                    onGrant = {
                        scope.launch {
                            settings.grantInAppResource(resource.id)
                        }
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { /* dismiss handled by visibility */ }) {
                    Text(
                        text = "Dismiss",
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingResourceRow(
    resource: InAppResource,
    onGrant: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.LockOpen,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f),
        ) {
            Text(
                text = resource.displayName,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = resource.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onGrant) {
            Text(text = "Grant")
        }
    }
}

/**
 * Resolve the [McpPermissionGate] for the bundled in-app server.
 * Returned @Composable cannot call non-@Composable members from
 * inside the @Composable remember scope on its own — pull this
 * helper out so the calling site stays a simple `val gate = … ?:
 * return`.
 */
@Composable
private fun rememberGate(): McpPermissionGate? {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    return androidx.compose.runtime.remember(app) {
        app.mcpPermissionGateFor(BundledMcpServer.InApp.id)
    }
}
