package com.meshlit.agent

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.meshlit.core.common.logger
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Renders an Agent conversation into portable files and shares them
 * via the system chooser / FileProvider.
 *
 * Three deliverables:
 *  - **Markdown transcript** — full chat, with code blocks fenced.
 *  - **Code-only bundle** — one `.txt` per `CodeBlock` so the user can
 *    hand each one to a separate file manager entry.
 *  - **Image attach** — copy a user-attached image into the agent
 *    exports folder so it can be referenced from outside the app.
 *
 * All paths live under `context.filesDir/exports/agent-...` so they
 * survive the SAF picker closing. The exported file is exposed via
 * `FileProvider` with the configured authority.
 */
object AgentExporter {
    private val log = logger("AgentExporter")

    private const val MIME_TEXT = "text/markdown"
    private const val MIME_CODE = "text/plain"
    private const val MIME_IMAGE = "image/*"

    /** Build a Markdown transcript of the conversation. */
    fun renderTranscript(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        sb.append("# Meshlit Agent — ")
            .append(SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date()))
            .append('\n')
        messages.forEach { msg ->
            when (msg) {
                is ChatMessage.UserMessage -> sb.append("## You\n\n").append(msg.text).append("\n\n")
                is ChatMessage.AgentMessage -> {
                    sb.append("## Agent\n\n")
                    sb.append(msg.finalText.ifEmpty { msg.streamingText }).append("\n\n")
                    msg.codeBlocks.forEach { block ->
                        sb.append("```").append(block.language).append('\n')
                        sb.append(block.code).append('\n')
                        sb.append("```\n\n")
                    }
                }
                is ChatMessage.SystemMessage -> sb.append("> ").append(msg.text).append("\n\n")
            }
        }
        return sb.toString()
    }

    /** Render only the code blocks across all AgentMessages. */
    fun renderCodeBlocks(messages: List<ChatMessage>): String {
        val sb = StringBuilder()
        messages.filterIsInstance<ChatMessage.AgentMessage>()
            .forEach { msg ->
                msg.codeBlocks.forEach { block ->
                    sb.append("// ---- ")
                        .append(block.language)
                        .append(" block #")
                        .append(block.index + 1)
                        .append(" ----\n")
                    sb.append(block.code).append("\n\n")
                }
            }
        return sb.toString()
    }

    /**
     * Write the rendered transcript to a Markdown file and return a
     * content:// URI for it. The caller can then hand the URI to the
     * share intent.
     */
    fun writeTranscript(context: Context, messages: List<ChatMessage>): Uri? {
        return writeFile(context, "agent-transcript.md", renderTranscript(messages), MIME_TEXT)
    }

    fun writeCodeBlocksFile(context: Context, messages: List<ChatMessage>): Uri? {
        return writeFile(context, "agent-code-blocks.txt", renderCodeBlocks(messages), MIME_CODE)
    }

    /**
     * Copy a user-picked image into the agent exports folder and
     * return its content URI. Returns null when the source cannot be
     * read.
     */
    fun attachImage(context: Context, source: Uri): Uri? {
        val resolver = context.contentResolver
        val name = runCatching {
            resolver.query(source, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else "attached.png"
            }
        }.getOrNull() ?: "attached.png"
        val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val destDir = File(context.filesDir, "exports").apply { mkdirs() }
        val dest = File(destDir, "img-${System.currentTimeMillis()}-$safeName")
        val ok = runCatching {
            resolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } != null
        }.getOrDefault(false)
        if (!ok) {
            log.warn("agent.export.image", "could not copy image", mapOf("name" to safeName))
            return null
        }
        return uriFor(context, dest, MIME_IMAGE)
    }

    /** Build a share intent for a single file URI. */
    fun shareIntent(uri: Uri, mime: String): Intent {
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun writeFile(
        context: Context,
        filename: String,
        body: String,
        mime: String,
    ): Uri? {
        val dir = File(context.filesDir, "exports").apply { mkdirs() }
        val dest = File(dir, filename)
        val ok = runCatching { dest.writeText(body) }.isSuccess
        if (!ok) {
            log.warn("agent.export.write", "could not write export", mapOf("name" to filename))
            return null
        }
        return uriFor(context, dest, mime)
    }

    private fun uriFor(context: Context, file: File, mime: String): Uri {
        val authority = context.packageName + ".fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
