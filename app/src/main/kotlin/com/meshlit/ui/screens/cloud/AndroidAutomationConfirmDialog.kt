package com.meshlit.ui.screens.cloud

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import com.meshlit.core.cloudmcp.android.AutomationRequest

/**
 * Per-action confirmation dialog shown by the Agent Terminal
 * before an `app_*` tool call. The dialog names the action
 * verb, the target app, and the parameters inline. The user
 * picks **Allow once / Allow for this app / Deny**.
 *
 * `Allow for this app` calls
 * `SettingsRepository.addAndroidAutomationAllowlistEntry(pkg)`
 * so future calls into the same package skip the dialog.
 *
 * Sensitive targets (high-risk packages, password fields) are
 * called out separately.
 */
@Composable
fun AndroidAutomationConfirmDialog(
    request: AutomationRequest,
    sensitive: Boolean,
    onAllowOnce: () -> Unit,
    onAllowForApp: () -> Unit,
    onDeny: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDeny,
        title = {
            Text(
                stringResource(R.string.cloud_android_action_confirm),
            )
        },
        text = {
            Text(
                buildString {
                    append("Action: ")
                    append(actionLabel(request))
                    append('\n')
                    append("Target: ")
                    append(request.targetPackage)
                    if (sensitive) {
                        append('\n')
                        append(stringResource(R.string.cloud_android_sensitive_target))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onAllowOnce) {
                Text(stringResource(R.string.cloud_android_allow_once))
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onAllowForApp) {
                    Text(stringResource(R.string.cloud_android_allow_for_app))
                }
                TextButton(onClick = onDeny) {
                    Text(stringResource(R.string.cloud_android_deny))
                }
            }
        },
    )
}

private fun actionLabel(request: AutomationRequest): String = when (request) {
    is AutomationRequest.ListApps -> "List apps (${request.query ?: "all"})"
    is AutomationRequest.OpenApp -> "Open ${request.packageName}"
    is AutomationRequest.Snapshot -> "Capture screen"
    is AutomationRequest.ClickRequest -> buildString {
        append("Click ")
        append(request.text ?: request.contentDescription ?: request.resourceId ?: "node")
    }
    is AutomationRequest.TypeRequest -> "Type \"${request.text.take(40)}\""
    is AutomationRequest.BackRequest -> "Back"
    is AutomationRequest.HomeRequest -> "Home"
    is AutomationRequest.WaitForRequest -> "Wait for ${request.text ?: request.resourceId}"
    is AutomationRequest.ScreenshotRequest -> "Screenshot"
}

@Composable
private fun Row(content: @Composable () -> Unit) {
    androidx.compose.foundation.layout.Row(
        horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp),
    ) { content() }
}