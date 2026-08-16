package com.meshlit.ui.screens.settings

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.meshlit.R
import com.meshlit.core.common.MeshlitError

/**
 * Generic error dialog surfaced when
 * `ModelSelectionViewModel.state.error` flips non-null. Mirrors
 * upstream `ModelSelectionSheet.ErrorDialog` — single OK button,
 * dismissing clears the error via the VM.
 */
@Composable
fun ErrorDialog(
    error: MeshlitError,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ra_dialog_error_title)) },
        text = { Text(error.tag) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_dialog_error_ok))
            }
        },
    )
}