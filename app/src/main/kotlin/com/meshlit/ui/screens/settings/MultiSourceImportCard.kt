package com.meshlit.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.meshlit.R
import java.io.File

/** Transient progress state for URL and Git imports. */
data class ImportProgress(
    val label: String,
    val percent: Int,
)

/**
 * Import controls for models that aren't in the curated catalogs.
 *
 * The card deliberately uses public HTTPS links and Android's SAF
 * rather than shelling out to `git`: this is safe on all Android ABIs,
 * works without a native Git binary, and handles GitHub blob/raw links
 * through [com.meshlit.core.inference.GitHubResolver].
 */
@Composable
fun MultiSourceImportCard(
    importUrl: String,
    onImportUrlChange: (String) -> Unit,
    importGit: String,
    onImportGitChange: (String) -> Unit,
    onImportFromUrl: () -> Unit,
    onImportFromGit: () -> Unit,
    importHfRepo: String,
    onImportHfRepoChange: (String) -> Unit,
    importHfFile: String,
    onImportHfFileChange: (String) -> Unit,
    onImportFromHf: () -> Unit,
    onPickExternal: () -> Unit,
    onReloadInternal: () -> Unit,
    onActivateInternal: (File) -> Unit,
    onDeleteInternal: (File) -> Unit,
    importClusterId: String,
    onImportClusterIdChange: (String) -> Unit,
    onAcquireSharded: () -> Unit,
    clusterAvailable: Boolean,
    importedFiles: List<File>,
    progress: ImportProgress?,
    error: String?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.models_import_url_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = importUrl,
                onValueChange = onImportUrlChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.models_import_url_placeholder))
                },
                singleLine = true,
            )
            Button(
                onClick = onImportFromUrl,
                enabled = importUrl.isNotBlank() && progress == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_import_url_button))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // Cluster-shard acquisition. When `clusterAvailable` is
            // false (no peers reachable on the LAN) the button is
            // disabled and the placeholder footer explains why. The
            // string passed in is the same modelId the resolver
            // understands — see `ClusterStorageInstaller.resolveModelSource`.
            Text(
                text = stringResource(R.string.models_import_cluster_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = importClusterId,
                onValueChange = onImportClusterIdChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.models_import_cluster_placeholder))
                },
                singleLine = true,
            )
            OutlinedButton(
                onClick = onAcquireSharded,
                enabled = importClusterId.isNotBlank() && clusterAvailable && progress == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_import_cluster_button))
            }
            if (!clusterAvailable) {
                Text(
                    text = stringResource(R.string.models_import_cluster_disabled),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.models_import_git_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = importGit,
                onValueChange = onImportGitChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.models_import_git_placeholder))
                },
                singleLine = true,
            )
            OutlinedButton(
                onClick = onImportFromGit,
                enabled = importGit.isNotBlank() && progress == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_import_git_button))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            // HuggingFace repo + file picker. Two free-text fields
            // represent `owner/repo` and the file name (which is
            // resolved against `siblings[]` via the HuggingFace
            // models API). The resolver yields an `ImportedModelSource`
            // containing the raw download URL + SHA-256 + size; the
            // caller treats it like any other URL import.
            Text(
                text = stringResource(R.string.models_import_hf_title),
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedTextField(
                value = importHfRepo,
                onValueChange = onImportHfRepoChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.models_import_hf_repo_placeholder))
                },
                singleLine = true,
            )
            OutlinedTextField(
                value = importHfFile,
                onValueChange = onImportHfFileChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(stringResource(R.string.models_import_hf_file_placeholder))
                },
                singleLine = true,
            )
            OutlinedButton(
                onClick = onImportFromHf,
                enabled = importHfRepo.isNotBlank() && importHfFile.isNotBlank() && progress == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.models_import_hf_button))
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

            Text(
                text = stringResource(R.string.models_import_storage_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPickExternal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.models_import_external_button))
                }
                OutlinedButton(
                    onClick = onReloadInternal,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.models_import_internal_button))
                }
            }

            if (progress != null) {
                Text(
                    text = stringResource(
                        R.string.models_import_progress,
                        progress.label,
                        progress.percent,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
                LinearProgressIndicator(
                    progress = { progress.percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (!error.isNullOrBlank()) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (importedFiles.isEmpty()) {
                Text(
                    text = stringResource(R.string.models_import_internal_none),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                importedFiles.forEach { file ->
                    ImportedModelFileRow(
                        file = file,
                        onActivate = { onActivateInternal(file) },
                        onDelete = { onDeleteInternal(file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportedModelFileRow(
    file: File,
    onActivate: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.name,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
            Text(
                text = "${file.length() / (1024 * 1024)} MB · ${file.parentFile?.name ?: "internal"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        OutlinedButton(onClick = onActivate) {
            Text(stringResource(R.string.models_import_internal_open))
        }
        OutlinedButton(onClick = onDelete) {
            Text(stringResource(R.string.models_import_internal_delete))
        }
    }
}
