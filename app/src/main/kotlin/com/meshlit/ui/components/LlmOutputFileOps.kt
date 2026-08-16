package com.meshlit.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import com.meshlit.R
import com.meshlit.core.common.logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared Save / Export / Share / Copy helpers used by both the
 * inline [LlmOutputActions] row and the long-press
 * [LlmOutputSideMenu] sheet. Before v4 each file had its own
 * private copy; the Save + Export move into the side menu
 * required a single implementation so both surfaces share the
 * exact same behaviour (same file path format, same toast copy,
 * same FileProvider authority).
 *
 * Keep these functions stateless and side-effect-free at the
 * module level — the toast strings come from [Context.getString]
 * so callers don't have to thread a [Context] through.
 */

/** Copy [text] into the system clipboard. */
fun copyToClipboard(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("meshlit", text))
}

/**
 * Persist [text] as a `.txt` under `{filesDir}/exports/`. The
 * directory is created on first use. Returns the file on success.
 */
fun saveToInternal(context: Context, text: String): kotlin.Result<File> {
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
fun exportToFile(context: Context, text: String): kotlin.Result<File> = saveToInternal(context, text)

/**
 * Launch the system share sheet with [text] as a plain-text
 * payload. Falls back to a copy-to-clipboard toast if no
 * `ACTION_SEND` handler is installed (rare).
 */
fun shareText(context: Context, text: String) {
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
fun shareFile(context: Context, file: File) {
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

/**
 * Save helper used by both surfaces. Toasts on success + failure
 * using the same strings the inline row used, so the UX is
 * identical whether the user invokes Save from the bubble row or
 * the long-press side menu.
 */
fun saveAndToast(context: Context, text: String) {
    val log = logger("LlmOutputFileOps")
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
}

/**
 * Export helper — saves the file then shares via FileProvider. Same
 * error semantics as [saveAndToast].
 */
fun exportAndShare(context: Context, text: String) {
    val log = logger("LlmOutputFileOps")
    val result = exportToFile(context, text)
    result.fold(
        onSuccess = { file -> shareFile(context, file) },
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
}