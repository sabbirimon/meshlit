package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred

internal data class PendingStudioReview(val review: StudioReview, val answer: CompletableDeferred<Boolean>)

@Composable internal fun StudioModePicker(mode: StudioMode, busy: Boolean, onMode: (StudioMode) -> Unit, onControls: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton({ expanded = true }, enabled = !busy) { Text("${mode.label} ▾") }
        DropdownMenu(expanded, { expanded = false }) {
            StudioMode.entries.forEach { item -> DropdownMenuItem({ expanded = false; onMode(item) }) {
                Column(Modifier.widthIn(max = 330.dp).padding(vertical = 5.dp)) {
                    Text((if(mode == item) "✓ " else "") + item.label)
                    Text(item.description, style = MaterialTheme.typography.caption)
                }
            } }
            Divider()
            DropdownMenuItem({ expanded = false; onControls() }) { Text("Approvals & workspace…") }
        }
    }
}

@Composable internal fun StudioControlDialog(mode: StudioMode, approval: StudioApproval, workspace: String?, busy: Boolean,
    onMode: (StudioMode) -> Unit, onApproval: (StudioApproval) -> Unit, onFolder: () -> Unit, onClose: () -> Unit) {
    var confirmEdits by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = onClose, title = { Text("Assistant controls") }, text = {
        StudioControlContent(mode, approval, workspace, busy, onMode,
            { if(it == StudioApproval.FULL) confirmEdits = true else onApproval(it) }, onFolder)
    }, confirmButton = { TextButton(onClose) { Text("Done") } })
    if(confirmEdits) AlertDialog(onDismissRequest = { confirmEdits = false }, title = { Text("Allow assistant file edits?") },
        text = { Text("Edit and Autopilot may create or modify text files inside the chosen workspace without asking again this session. Plan still cannot write. This does not grant unrestricted computer access. Keep important work in version control.") },
        confirmButton = { TextButton({ onApproval(StudioApproval.FULL); confirmEdits = false }) { Text("Allow workspace edits") } },
        dismissButton = { TextButton({ confirmEdits = false }) { Text("Cancel") } })
}

@Composable internal fun StudioControlContent(mode: StudioMode, approval: StudioApproval, workspace: String?, busy: Boolean,
    onMode: (StudioMode) -> Unit, onApproval: (StudioApproval) -> Unit, onFolder: () -> Unit) {
        Column(Modifier.width(480.dp).heightIn(max = 600.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Shared by Studio chat and coding workspace. Permissions reset when Meshlit closes.")
            StudioMode.entries.forEach { item -> Row {
                RadioButton(mode == item, { onMode(item) }, enabled = !busy)
                Column { Text(item.label); Text(item.description, style = MaterialTheme.typography.caption) }
            } }
            Divider(); Text("How should assistant actions be approved?", style = MaterialTheme.typography.subtitle1)
            StudioApproval.entries.forEach { item -> Row {
                RadioButton(approval == item, { onApproval(item) }, enabled = !busy)
                Column { Text(item.label); Text(item.description, style = MaterialTheme.typography.caption) }
            } }
            Divider()
            Text(workspace ?: "No assistant workspace selected", style = MaterialTheme.typography.caption)
            TextButton(onFolder, enabled = !busy) { Text("Choose assistant workspace…") }
            Text("File reads and status results go to your selected model. Each model step uses the existing Usage & budgets checks. Autopilot can use up to 8 model requests per run.", style = MaterialTheme.typography.caption)
            Text("Available: list/read text, create files, exact text replacement, system status and Meshlit navigation. No shell, administrator access, credential editing, browser clicking or payments. Human-operated controls are separate from assistant permissions.", style = MaterialTheme.typography.caption)
        }
}

@Composable internal fun StudioActionDialog(pending: PendingStudioReview, destination: String, onAnswer: (Boolean) -> Unit) {
    AlertDialog(onDismissRequest = { onAnswer(false) }, title = { Text("Approve ${pending.review.title}?") },
        text = { Column(Modifier.width(620.dp).heightIn(max = 520.dp).verticalScroll(rememberScrollState())) {
            Text("Selected model: $destination", style = MaterialTheme.typography.caption)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(pending.review.detail, {}, readOnly = true, modifier = Modifier.fillMaxWidth(), label = { Text(if(pending.review.writes) "Proposed change" else "Action details") })
        } },
        confirmButton = { TextButton({ onAnswer(true) }) { Text("Approve once") } },
        dismissButton = { TextButton({ onAnswer(false) }) { Text("Decline and stop") } })
}
