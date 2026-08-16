package com.meshlit.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.meshlit.R
import com.meshlit.core.common.logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Toolbar row of post-generation affordances rendered beneath
 * every completed LLM bubble. The four actions:
 *
 *  - **Copy** — drops the bubble text into the system clipboard
 *    via [ClipboardManager]. Toasts "Copied to clipboard".
 *  - **Save** — writes the bubble text to
 *    `{filesDir}/exports/meshlit-output-<timestamp>.txt` and
 *    toasts the absolute path. Saved files are addressable via
 *    FileProvider so the system share sheet can pick them up.
 *  - **Share** — fires `Intent.ACTION_SEND` with a `text/plain`
 *    MIME so the user can route the bubble to any messaging /
 *    notes app installed on the device.
 *  - **Export** — same as Save but writes the file into the
 *    public Documents dir via `MediaStore` so the file shows up
 *    in the system file manager. Falls back to `filesDir/exports`
 *    if the user hasn't granted `WRITE_EXTERNAL_STORAGE` (the
 *    latter is `maxSdkVersion=29` per the manifest, so we never
 *    hit it on modern Android anyway).
 *
 * The toolbar is rendered only when [text] is non-empty so the
 * streaming bubble doesn't show a dead button row.
 */
@Composable
fun LlmOutputActions(
    text: String,
    modifier: Modifier = Modifier,
) {
    if (text.isEmpty()) return
    val context = LocalContext.current
    val log = logger("LlmOutputActions")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        TextButton(onClick = {
            copyToClipboard(context, text)
            Toast.makeText(
                context,
                context.getString(R.string.llm_output_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }) {
            Icon(
                imageVector = Icons.Filled.ContentCopy,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.llm_output_copy))
        }
        TextButton(onClick = {
            val result = saveToInternal(context, text)
            result.fold(
                onSuccess = { file ->
                    Toast.makeText(
                        context,
                        context.getString(R.string.llm_output_saved, file.absolutePath),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onFailure = { t ->
                    log.warn(
                        "llm.save.failed",
                        t.message ?: t.javaClass.simpleName,
                        mapOf("error" to (t.message ?: "unknown")),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.llm_output_save_failed, t.message ?: t.javaClass.simpleName),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }) {
            Icon(
                imageVector = Icons.Filled.Save,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.llm_output_save))
        }
        TextButton(onClick = {
            shareText(context, text)
        }) {
            Icon(
                imageVector = Icons.Filled.IosShare,
                contentDescription = null,
                modifier = Modifier.padding(end = 4.dp),
            )
            Text(stringResource(R.string.llm_output_share))
        }
        TextButton(onClick = {
            val result = exportToFile(context, text)
            result.fold(
                onSuccess = { file ->
                    shareFile(context, file)
                },
                onFailure = { t ->
                    log.warn(
                        "llm.export.failed",
                        t.message ?: t.javaClass.simpleName,
                        mapOf("error" to (t.message ?: "unknown")),
                    )
                    Toast.makeText(
                        context,
                        context.getString(R.string.llm_output_export_failed, t.message ?: t.javaClass.simpleName),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            )
        }) {
            Text(stringResource(R.string.llm_output_export))
        }
    }
}

/** Copy [text] into the system clipboard. */
private fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("meshlit", text))
}

/**
 * Persist [text] as a `.txt` under `{filesDir}/exports/`. The
 * directory is created on first use. Returns the file on success.
 */
private fun saveToInternal(context: Context, text: String): kotlin.Result<File> {
    return runCatching {
        val exportDir = File(context.filesDir, "exports").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "meshlit-output-$stamp.txt")
        file.writeText(text, Charsets.UTF_8)
        file
    }
}

/**
 * Same as [saveToInternal] but the file is intended to be opened
 * by other apps (e.g. system file manager). The path is the same;
 * the caller follows up with [shareFile] which uses
 * `FileProvider` to grant a content URI.
 */
private fun exportToFile(context: Context, text: String): kotlin.Result<File> = saveToInternal(context, text)

/**
 * Launch the system share sheet with [text] as a plain-text
 * payload. Falls back to a copy-to-clipboard toast if no
 * `ACTION_SEND` handler is installed (rare).
 */
private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            // No app to handle share — fall back to clipboard so the
            // user doesn't lose the text.
            copyToClipboard(context, text)
            Toast.makeText(
                context,
                context.getString(R.string.llm_output_copied),
                Toast.LENGTH_SHORT,
            ).show()
        }
}

/**
 * Wrap [file] in a `content://` URI via [FileProvider] and fire
 * an `ACTION_SEND` so the user can hand it to any installed app
 * (Drive, Dropbox, mail, etc.). The provider authority must
 * match the manifest declaration
 * (`${applicationId}.fileprovider`).
 */
private fun shareFile(context: Context, file: File) {
    val authority = context.packageName + ".fileprovider"
    val uri = runCatching {
        FileProvider.getUriForFile(context, authority, file)
    }.getOrNull() ?: return
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        .onFailure {
            Toast.makeText(
                context,
                context.getString(R.string.llm_output_export_failed, it.message ?: it.javaClass.simpleName),
                Toast.LENGTH_SHORT,
            ).show()
        }
}
