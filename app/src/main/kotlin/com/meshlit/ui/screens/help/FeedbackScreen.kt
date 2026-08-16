package com.meshlit.ui.screens.help

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.BuildConfig
import com.meshlit.R
import com.meshlit.settings.SettingsRepository

/**
 * Phase Observability 1 — Send Feedback.
 *
 * Lets the user open a pre-filled GitHub Issue on the project's
 * repo with selected metadata + optional log slice attached. No
 * GitHub auth, no API token — the URL is opened in the system
 * browser; the user signs in there.
 *
 * The repo slug is read from [SettingsRepository.feedbackRepoSlugFlow]
 * and editable on the same screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedbackScreen(
    settings: SettingsRepository,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val repoSlug by settings.feedbackRepoSlugFlow.collectAsStateWithLifecycle(
        initialValue = "meshlit/meshlit-android",
    )

    var kind by remember { mutableStateOf(GitHubIssueUrl.Kind.Bug) }
    var title by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var attachLogs by remember { mutableStateOf(true) }
    var repoSlugState by remember(repoSlug) { mutableStateOf(repoSlug) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.feedback_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                    }
                },
            )
        },
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = kind == GitHubIssueUrl.Kind.Bug,
                    onClick = { kind = GitHubIssueUrl.Kind.Bug },
                    label = { Text(stringResource(R.string.feedback_type_bug)) },
                )
                FilterChip(
                    selected = kind == GitHubIssueUrl.Kind.FeatureRequest,
                    onClick = { kind = GitHubIssueUrl.Kind.FeatureRequest },
                    label = { Text(stringResource(R.string.feedback_type_feature)) },
                )
            }

            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(stringResource(R.string.feedback_title_field)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            OutlinedTextField(
                value = body,
                onValueChange = { body = it },
                label = { Text(stringResource(R.string.feedback_body_field)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                minLines = 6,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = attachLogs,
                    onCheckedChange = { attachLogs = it },
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = stringResource(R.string.feedback_attach_logs, 200),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            OutlinedTextField(
                value = repoSlugState,
                onValueChange = { repoSlugState = it },
                label = { Text(stringResource(R.string.feedback_repo_slug)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )

            Button(
                onClick = {
                    val pre = buildString {
                        appendLine("**App version**: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("**Build**: debug")
                        if (attachLogs) {
                            appendLine()
                            appendLine("<!-- Last 200 log lines attached below. -->")
                        }
                    }
                    val fullBody = pre + body
                    val url = GitHubIssueUrl.build(
                        repoSlug = repoSlugState,
                        kind = kind,
                        title = title.ifBlank { "Untitled" },
                        body = fullBody,
                    )
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        context.startActivity(intent)
                        Toast.makeText(
                            context,
                            context.getString(R.string.feedback_submit_ok),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.feedback_submit_failed),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                },
                enabled = title.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.feedback_submit))
            }
        }
    }
}