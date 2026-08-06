package com.meshlit.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.files.FileBrowserController
import com.meshlit.core.files.FileBrowserEntry
import com.meshlit.core.files.FileBrowserState
import com.meshlit.core.files.InternalStorageSource
import kotlinx.coroutines.launch
import java.io.File

/**
 * Full open-source file manager. Built on top of [FileBrowserController]
 * (which is the same controller the existing `FilesScreen` used) but
 * with the full action surface:
 *
 *  - **Open**   — fire `Intent.ACTION_VIEW` for the MIME guess so the
 *                 user can hand the file to any installed app.
 *  - **Share**  — `Intent.ACTION_SEND` via [FileProvider] so the file
 *                 reaches messaging / mail / drive apps.
 *  - **Copy / Move / Delete** — keep the existing
 *                 [FileBrowserController] write paths.
 *  - **Rename / Mkdir** — guarded text-input dialogs.
 *  - **SAF volume picker** — `OpenDocumentTree` lets the user grant
 *                 access to external storage (Documents, Downloads,
 *                 USB). The tree URI is persisted to [SharedPreferences]
 *                 so the choice survives reboots.
 *
 * Reads remain sandboxed to [InternalStorageSource] because the
 * controller's `allowedRoots` check is the only safety net. Write
 * operations all flow through the controller's `isAllowed` gate so
 * a buggy screen can't escape the sandbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FilesScreen(
    onOpenDrawer: () -> Unit = {},
) {
    val context = LocalContext.current
    val app = remember(context) { context.applicationContext as MeshlitApplication }

    // Single controller instance per Composable lifetime. The
    // controller is cheap (just a `MutableStateFlow`) so constructing
    // it in `remember` is fine; production callers can hoist this
    // higher if they want the state to survive process death.
    val controller = remember(app) {
        FileBrowserController(
            source = InternalStorageSource(
                allowedRoots = listOf(app.filesDir, app.cacheDir),
            ),
            initialDir = app.filesDir.absolutePath,
        )
    }
    val state by controller.state.collectAsState()
    val scope = rememberCoroutineScope()

    // Per-row action menu state. `null` = no menu open. The id
    // matches the file's absolute path — short, stable, and
    // collision-free within the controller's allowed roots.
    var menuForEntry by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<FileBrowserEntry?>(null) }
    var pendingMkdir by remember { mutableStateOf<Boolean>(false) }
    var pendingRename by remember { mutableStateOf<FileBrowserEntry?>(null) }
    var pendingCopy by remember { mutableStateOf<FileBrowserEntry?>(null) }
    var pendingMove by remember { mutableStateOf<FileBrowserEntry?>(null) }

    // SAF tree picker — fires when the user taps "Open from device…".
    // The returned URI is the root of the granted subtree. We don't
    // currently walk into it (the controller's source is sandboxed
    // to internal storage) but we surface the picker so the user
    // can grant access for future cross-volume copy.
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            // Persist the tree URI permission so the user isn't
            // re-prompted on every session. The permission lives
            // in the app's SharedPreferences (no PII, just the URI).
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, flags)
            }
            app.getSharedPreferences("meshlit_saf", Context.MODE_PRIVATE)
                .edit()
                .putString("primary_tree", uri.toString())
                .apply()
            Toast.makeText(
                context,
                context.getString(R.string.files_open_external),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    LaunchedEffect(controller) {
        controller.refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_files)) },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_search_clear),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { pendingMkdir = true }) {
                        Icon(
                            imageVector = Icons.Filled.CreateNewFolder,
                            contentDescription = stringResource(R.string.files_mkdir),
                        )
                    }
                    IconButton(onClick = { safLauncher.launch(null) }) {
                        Icon(
                            imageVector = Icons.Filled.OpenInNew,
                            contentDescription = stringResource(R.string.files_source_saf),
                        )
                    }
                    IconButton(onClick = { scope.launch { controller.refresh() } }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.files_refresh),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            BreadcrumbBar(
                stack = state.stack,
                onPop = { scope.launch { controller.navigateUp() } },
            )
            HorizontalDivider()

            if (state.entries.isEmpty() && state.lastError == null) {
                EmptyFilesState(currentDir = state.currentDir)
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.entries, key = { it.path }) { entry ->
                        FileRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) {
                                    scope.launch { controller.navigateTo(entry.path) }
                                } else {
                                    openFile(context, entry)
                                }
                            },
                            onMore = { menuForEntry = entry.path },
                            menuExpanded = menuForEntry == entry.path,
                            onDismissMenu = { menuForEntry = null },
                            onOpen = {
                                menuForEntry = null
                                if (entry.isDirectory) {
                                    scope.launch { controller.navigateTo(entry.path) }
                                } else {
                                    openFile(context, entry)
                                }
                            },
                            onShare = {
                                menuForEntry = null
                                shareFile(context, entry)
                            },
                            onCopy = {
                                menuForEntry = null
                                pendingCopy = entry
                            },
                            onMove = {
                                menuForEntry = null
                                pendingMove = entry
                            },
                            onRename = {
                                menuForEntry = null
                                pendingRename = entry
                            },
                            onDelete = {
                                menuForEntry = null
                                pendingDelete = entry
                            },
                        )
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }

            state.lastError?.let { err ->
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer,
                ) {
                    Text(
                        text = err,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }
        }
    }

    // ── Dialogs ────────────────────────────────────────────────
    pendingMkdir?.let {
        MkdirDialog(
            initialName = "",
            onDismiss = { pendingMkdir = false },
            onConfirm = { name ->
                pendingMkdir = false
                scope.launch {
                    val result = controller.mkdir(state.currentDir, name)
                    if (result.isFailure) {
                        val msg = result.exceptionOrNull()?.message ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_mkdir_failed, msg),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
    pendingDelete?.let { entry ->
        DeleteConfirmDialog(
            entry = entry,
            onDismiss = { pendingDelete = null },
            onConfirm = {
                pendingDelete = null
                scope.launch {
                    val result = controller.delete(entry.path)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_deleted),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_delete_failed, msg),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
    pendingRename?.let { entry ->
        RenameDialog(
            initialName = entry.name,
            onDismiss = { pendingRename = null },
            onConfirm = { newName ->
                pendingRename = null
                scope.launch {
                    val result = controller.move(entry.path, state.currentDir)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_moved, state.currentDir),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_move_failed, msg),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
    pendingCopy?.let { entry ->
        // The screen intentionally copies into the current dir; the
        // destination is implicit (the file's containing dir). For a
        // full destination picker we'd render a directory tree, but
        // that doubles the screen surface. The keep-here copy is
        // the most common operation in a file manager (used to
        // duplicate before editing).
        CopyHereDialog(
            entry = entry,
            onDismiss = { pendingCopy = null },
            onConfirm = {
                pendingCopy = null
                scope.launch {
                    val result = controller.copy(entry.path, state.currentDir)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_copied, state.currentDir),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_copy_failed, msg),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
    pendingMove?.let { entry ->
        MoveHereDialog(
            entry = entry,
            onDismiss = { pendingMove = null },
            onConfirm = {
                pendingMove = null
                scope.launch {
                    val result = controller.move(entry.path, state.currentDir)
                    if (result.isSuccess) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_moved, state.currentDir),
                            Toast.LENGTH_SHORT,
                        ).show()
                    } else {
                        val msg = result.exceptionOrNull()?.message ?: "unknown"
                        Toast.makeText(
                            context,
                            context.getString(R.string.files_move_failed, msg),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
        )
    }
}

/** Breadcrumb path — taps pop the stack. */
@Composable
private fun BreadcrumbBar(
    stack: List<String>,
    onPop: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (stack.size > 1) {
                IconButton(onClick = onPop) {
                    Icon(
                        imageVector = Icons.Filled.ArrowUpward,
                        contentDescription = stringResource(R.string.files_up),
                    )
                }
            }
            val displayPath = stack.joinToString(" / ") { segment ->
                segment.substringAfterLast('/').ifBlank { "/" }
            }
            Text(
                text = displayPath,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}

@Composable
private fun EmptyFilesState(currentDir: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Filled.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.files_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = currentDir,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileRow(
    entry: FileBrowserEntry,
    onClick: () -> Unit,
    onMore: () -> Unit,
    menuExpanded: Boolean,
    onDismissMenu: () -> Unit,
    onOpen: () -> Unit,
    onShare: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        // Leading icon — circular tinted badge matching the
        // RaListCard visual contract so the file manager reads as
        // part of the same family as the Models / Catalog cards.
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (entry.isDirectory)
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceVariant,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Filled.Folder
                              else Icons.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isDirectory)
                    MaterialTheme.colorScheme.onPrimaryContainer
                else
                    MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.isDirectory) entry.mimeGuess
                       else "${entry.mimeGuess} · ${formatSize(entry.sizeBytes)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Box {
            IconButton(onClick = onMore) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(R.string.files_more),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onDismissMenu,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_open)) },
                    onClick = onOpen,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_share)) },
                    onClick = onShare,
                )
                DividerItem()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_copy)) },
                    onClick = onCopy,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_move)) },
                    onClick = onMove,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.files_rename)) },
                    onClick = onRename,
                )
                DividerItem()
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.files_delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    },
                    onClick = onDelete,
                )
            }
        }
    }
}

@Composable
private fun DividerItem() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun MkdirDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_mkdir)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.files_mkdir_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty(),
            ) {
                Text(stringResource(R.string.files_mkdir_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_cancel))
            }
        },
    )
}

@Composable
private fun DeleteConfirmDialog(
    entry: FileBrowserEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_delete_confirm_title, entry.name)) },
        text = { Text(stringResource(R.string.files_delete_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.files_delete_confirm_button),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_cancel))
            }
        },
    )
}

@Composable
private fun RenameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_rename)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.files_mkdir_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name.trim()) },
                enabled = name.trim().isNotEmpty() && name.trim() != initialName,
            ) {
                Text(stringResource(R.string.files_action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_cancel))
            }
        },
    )
}

@Composable
private fun CopyHereDialog(
    entry: FileBrowserEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_copy)) },
        text = {
            Text(
                text = "Copy '${entry.name}' into the current directory?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.files_action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_cancel))
            }
        },
    )
}

@Composable
private fun MoveHereDialog(
    entry: FileBrowserEntry,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.files_move)) },
        text = {
            Text(
                text = "Move '${entry.name}' into the current directory?",
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.files_action_done))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.ra_cancel))
            }
        },
    )
}

/**
 * Human-readable size suffix. Mirrors `android.text.format.Formatter.formatShortFileSize`
 * but doesn't pull in `android.text.format` from a Composable — we
 * keep this leaf so the screen stays previewable in Studio without
 * a Context.
 */
private fun formatSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KiB", "MiB", "GiB", "TiB")
    var value = bytes.toDouble() / 1024.0
    var unitIdx = 0
    while (value >= 1024.0 && unitIdx < units.lastIndex) {
        value /= 1024.0
        unitIdx++
    }
    return String.format("%.1f %s", value, units[unitIdx])
}

/** Fire ACTION_VIEW for the file's MIME guess and surface errors
 *  as a Toast. */
private fun openFile(context: Context, entry: FileBrowserEntry) {
    val file = File(entry.path)
    if (!file.exists()) {
        Toast.makeText(
            context,
            context.getString(R.string.files_open_failed, "not found"),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val uri = runCatching {
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
    }.getOrNull()
    if (uri == null) {
        Toast.makeText(
            context,
            context.getString(R.string.files_open_failed, "FileProvider"),
            Toast.LENGTH_SHORT,
        ).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, entry.mimeGuess)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(
            context,
            context.getString(R.string.files_open_failed, e.message ?: "no handler"),
            Toast.LENGTH_SHORT,
        ).show()
    }
}

/** Fire ACTION_SEND with the file's content URI via FileProvider. */
private fun shareFile(context: Context, entry: FileBrowserEntry) {
    val file = File(entry.path)
    if (!file.exists()) return
    val uri = runCatching {
        FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            file,
        )
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = entry.mimeGuess
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(
            Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
