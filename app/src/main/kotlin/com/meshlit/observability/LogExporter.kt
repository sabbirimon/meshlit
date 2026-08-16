package com.meshlit.observability

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.meshlit.core.observability.LogSource
import java.io.File

/**
 * Renders a slice of [LogBuffer] entries into a sharable file on
 * disk. Supports plain text and newline-delimited JSON formats.
 */
object LogExporter {

    enum class Format(val extension: String, val mimeType: String) {
        TXT("txt", "text/plain"),
        JSONL("jsonl", "application/x-ndjson"),
    }

    /** Build a timestamped file in [dir] for the chosen [format]. */
    fun newOutputFile(dir: File, format: Format): File =
        File(dir, "meshlit-${System.currentTimeMillis()}.${format.extension}")

    /** Write [entries] to [outFile] using [format]. Returns the same file for chaining. */
    fun export(
        entries: List<LogBuffer.Entry>,
        outFile: File,
        format: Format,
    ): File {
        outFile.parentFile?.mkdirs()
        outFile.bufferedWriter().use { writer ->
            when (format) {
                Format.TXT -> entries.forEach { writer.write(it.format()); writer.newLine() }
                Format.JSONL -> entries.forEach { writer.write(it.toJsonLine()); writer.newLine() }
            }
        }
        return outFile
    }

    fun export(
        context: Context,
        entries: List<LogBuffer.Entry>,
        format: Format,
        filter: LogSource? = null,
    ): Uri {
        val safe = if (filter == null) entries else entries.filter { it.source == filter }
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = newOutputFile(exportsDir, format)
        export(entries = safe, outFile = file, format = format)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }

    fun exportRecent(
        context: Context,
        buffer: LogBuffer,
        count: Int,
        format: Format,
        filter: LogSource? = null,
    ): Uri {
        val snapshot = buffer.entries.value
        val safeCount = count.coerceAtLeast(0)
        val tail = if (snapshot.size <= safeCount) snapshot else snapshot.subList(snapshot.size - safeCount, snapshot.size)
        return export(context, tail, format, filter)
    }

    fun exportText(context: Context, body: String, filename: String = "feedback.txt"): Uri {
        val exportsDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportsDir, filename)
        file.writeText(body)
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
    }
}
