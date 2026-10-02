package com.meshlit.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Toolbar rendered under a finished LLM exchange. Five actions:
 *
 *  - **Copy** — sets the Android clipboard via [ClipboardManager]
 *    so the paste surface is system-managed and survives process death.
 *  - **Save** — writes the exchange to `filesDir/conversations/<timestamp>.txt`
 *    and reports the path via [onSaved] so the screen can show a
 *    confirmation. The file is real and re-openable; not a no-op toast.
 *  - **Export** — opens the system file picker
 *    ([Intent.ACTION_CREATE_DOCUMENT]) so the user picks where to
 *    write. We write through `ContentResolver.openOutputStream(uri)`
 *    so the write succeeds on scoped-storage devices.
 *  - **Share** — fires [Intent.ACTION_SEND] with `text/plain` so any
 *    installed share target (mail, Slack, etc.) picks up the reply.
 *  - **Regenerate** — caller-supplied; null in screens that don't
 *    support it.
 *
 * Errors surface via [onError] (logs and continues). The
 * `rememberLauncherForActivityResult` for `CreateDocument` is
 * scoped to this composable, which is fine because we're invoking
 * it from a chat-bubble toolbar that's stable across recompositions.
 */
@Composable
fun LlmOutputActions(
    text: String,
    onRegenerate: (() -> Unit)? = null,
    prompt: String? = null,
    onSaved: ((path: String) -> Unit)? = null,
    onError: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val log = remember { logger("LlmOutputActions") }
    var lastError by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                writeExchangeToUri(context, uri, text, prompt)
            }
            if (!ok) {
                val msg = "Export failed: could not write to $uri"
                log.warn("actions.export.fail", msg)
                lastError = msg
            } else {
                log.info("actions.export.ok", "exported", mapOf("uri" to uri.toString()))
            }
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = {
            copyToClipboard(context, text)
            log.info("actions.copy.ok", "copied reply", mapOf("bytes" to text.length.toString()))
        }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = "Copy reply",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = {
            scope.launch {
                val outcome = withContext(Dispatchers.IO) {
                    runCatching { saveExchangeToFilesDir(context, text, prompt) }
                }
                outcome.onSuccess { path ->
                    log.info("actions.save.ok", "saved exchange", mapOf("path" to path))
                    onSaved?.invoke(path)
                }.onFailure { t ->
                    val msg = "Save failed: ${t.message ?: t::class.simpleName}"
                    log.warn("actions.save.fail", msg)
                    lastError = msg
                }
            }
        }) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = "Save reply to app storage",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = {
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            exportLauncher.launch("meshlit-reply-$stamp.txt")
        }) {
            Icon(
                imageVector = Icons.Filled.IosShare,
                contentDescription = "Export reply to file",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = { shareText(context, text) }) {
            Icon(
                imageVector = Icons.Filled.Share,
                contentDescription = "Share reply",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        if (onRegenerate != null) {
            IconButton(onClick = onRegenerate) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Regenerate",
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        if (lastError != null) {
            // Surface the error to the screen if it didn't already
            // dismiss via onError. The toolbar itself can't render
            // a snackbar — it bubbles up via onError.
            onError?.invoke(lastError!!)
            lastError = null
        }
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Meshlit reply", text))
}

/**
 * Writes the exchange to `filesDir/conversations/<timestamp>.txt`
 * and returns the absolute path. Plain UTF-8, markdown-flavoured.
 */
private fun saveExchangeToFilesDir(
    context: Context,
    reply: String,
    prompt: String?,
): String {
    val dir = File(context.filesDir, "conversations").apply { mkdirs() }
    val stamp = SimpleDateFormat("yyyyMMdd-HHmmss-SSS", Locale.US).format(Date())
    val out = File(dir, "exchange-$stamp.txt")
    val header = "Meshlit exchange\n" +
        "Saved at: ${Date()}\n" +
        "========================================\n\n"
    val promptBlock = prompt?.let { "USER:\n$it\n\n" } ?: ""
    val body = "$header$promptBlock" + "ASSISTANT:\n$reply\n"
    out.writeText(body, Charsets.UTF_8)
    return out.absolutePath
}

/**
 * Writes the exchange text into a user-chosen content URI via
 * `ContentResolver`. Used by the Export action so the result lands
 * wherever the user picked (Downloads, Drive, etc.) rather than
 * only in our sandbox.
 */
private fun writeExchangeToUri(
    context: Context,
    uri: Uri,
    reply: String,
    prompt: String?,
): Boolean {
    return runCatching {
        val resolver = context.contentResolver
        val opened = resolver.openOutputStream(uri, "wt") ?: return false
        opened.use { os ->
            val header = "Meshlit export\n${Date()}\n\n"
            val promptBlock = prompt?.let { "USER:\n$it\n\n" } ?: ""
            os.write(header.toByteArray(Charsets.UTF_8))
            os.write(promptBlock.toByteArray(Charsets.UTF_8))
            os.write("ASSISTANT:\n$reply\n".toByteArray(Charsets.UTF_8))
            os.flush()
        }
        true
    }.getOrDefault(false)
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val chooser = Intent.createChooser(intent, "Share Meshlit reply").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(chooser) }
}
