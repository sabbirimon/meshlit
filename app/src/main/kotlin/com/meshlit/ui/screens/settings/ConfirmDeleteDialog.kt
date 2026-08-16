package com.meshlit.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshlit.R

/**
 * Confirmation dialog for model deletion. Mirrors upstream
 * `ModelSelectionSheet.pendingDelete` — the user has to confirm
 * before `RunAnywhere.deleteModel(id)` actually runs. Without this
 * a stray tap on the row's trash icon would silently drop a
 * multi-GB download.
 *
 * Visual contract:
 *  - title: "Delete model?"
 *  - body: "<displayName> and its <size> MB will be removed…"
 *  - confirm: "Delete" (destructive tone — UI surfaces red)
 *  - dismiss: "Cancel"
 */
@Composable
fun ConfirmDeleteDialog(
    displayName: String,
    approxSizeMb: Long,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ra_dialog_delete_title)) },
        text = {
            Text(
                stringResource(
                    R.string.ra_dialog_delete_body,
                    displayName,
                    approxSizeMb,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.ra_dialog_delete_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_dialog_delete_cancel))
            }
        },
    )
}