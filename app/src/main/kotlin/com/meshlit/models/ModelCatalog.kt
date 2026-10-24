package com.meshlit.models

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import com.meshlit.core.common.logger
import com.meshlit.core.inference.FileFormat
import com.meshlit.core.inference.RuntimeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

/**
 * Catalog of open-weight GGUF models Meshlit can import. The list is
 * intentionally small: 4 hand-picked models under 2 GB that fit a
 * modern Android phone and work with the bundled llama.cpp runtime.
 *
 * Each entry carries:
 *  - [id]: stable key for DataStore / logs
 *  - [displayName]: shown in the UI
 *  - [origin]: USA / China / EU so the user sees where the model
 *    came from
 *  - [license]: short license tag (Apache 2.0, MIT, Llama community)
 *  - [family]: which model family the user is picking from
 *  - [url]: direct HTTPS URL to a Q4_K_M GGUF on Hugging Face
 *  - [approxSizeMb]: rough download size, used for UI hints
 *  - [strengths]: short tag list shown in the picker
 *  - [language]: primary language coverage
 *
 * The repository is read-only here — the user downloads a model
 * with one tap through `Import`, the file lands in
 * `filesDir/imported-models/` and shows up in the Agent dropdown.
 *
 * Today we offer:
 *  - **Qwen2.5-1.5B-Instruct** (China, Apache 2.0) — bundled, multilingual
 *  - **SmolLM2-1.7B-Instruct** (USA, Apache 2.0) — small general model
 *  - **Llama-3.2-1B-Instruct** (USA, Llama community) — multilingual
 *  - **DeepSeek-R1-Distill-Qwen-1.5B** (China, MIT) — reasoning-tuned
 */
object ModelCatalog {

    private val log = logger("ModelCatalog")

    data class Entry(
        val id: String,
        val displayName: String,
        val origin: String,
        val license: String,
        val family: String,
        val url: String,
        val approxSizeMb: Long,
        val strengths: List<String>,
        val language: String,
        /**
         * Phase 2 — file format the model is distributed in. Today every
         * shipped model is GGUF; the field exists so the registry can
         * surface non-GGUF candidates and the UI can route them to the
         * correct runtime before the .so / .aar is linked.
         */
        val fileFormat: FileFormat = FileFormat.Gguf,
        /** High-level classification shown in the picker. Lets the user
         *  filter by *what the model is good at* instead of reading the
         *  size + strengths list. */
        val category: ModelCategory = ModelCategory.General,
        /** Lowest device tier the model is expected to run on. Tier
         *  definitions live on [DeviceTier]. The picker hides models
         *  above the user's device tier with a "needs more RAM" hint. */
        val minDeviceTier: DeviceTier = DeviceTier.Mid,
        /** Effort level for first-time users — Easy / Medium / Hard.
         *  Easy models load in one tap on a Pixel 6, fit in 4 GB of
         *  RAM, and chat in English. Hard models expect a Pro device,
         *  an HF token, or both. */
        val ease: Ease = Ease.Medium,
    ) {
        /** The runtime that would carry this model. Resolved via the
         *  [RuntimeRegistry] so the source of truth stays in one place. */
        val runtimeDisplayName: String
            get() = when (val r = RuntimeRegistry.pickForFormat(fileFormat)) {
                is com.meshlit.core.inference.RuntimeResolution.Found -> r.runtime.displayName
                is com.meshlit.core.inference.RuntimeResolution.NotShipped -> r.runtime.displayName + " (Phase 2)"
                is com.meshlit.core.inference.RuntimeResolution.Unsupported -> "Unsupported format"
                is com.meshlit.core.inference.RuntimeResolution.UnknownFormat -> "Unknown format"
            }
    }

    /** What a model is good at — what the picker shows as the chip colour. */
    enum class ModelCategory(val displayName: String, val tag: String) {
        General("General", "general"),
        Multilingual("Multilingual", "multilingual"),
        Coding("Coding", "coding"),
        Reasoning("Reasoning", "reasoning"),
        Chat("Chat", "chat"),
        Small("Tiny / edge", "small"),
        Enterprise("Enterprise / Pro", "enterprise"),
    }

    /** Lowest device tier on which the model runs acceptably. Aligns
     *  with [com.meshlit.core.common.CapabilityTier] in core-common. */
    enum class DeviceTier(val displayName: String, val minRamGb: Int) {
        Low("Low-end (≤ 4 GB)", 3),
        Mid("Mid (4–6 GB)", 4),
        High("High (≥ 8 GB)", 8),
        Pro("Pro (≥ 12 GB)", 12),
    }

    /** Effort signal for new users. Picker uses this to gate the
     *  download button (Easy = one-tap; Hard = confirm dialog + token). */
    enum class Ease(val displayName: String, val tag: String) {
        Easy("Easy", "easy"),
        Medium("Medium", "medium"),
        Hard("Hard", "hard"),
    }

    val all: List<Entry> = listOf(
        Entry(
            id = "qwen2.5-1.5b-instruct-q4_k_m",
            displayName = "Qwen2.5-1.5B-Instruct · Q4_K_M",
            origin = "China",
            license = "Apache 2.0",
            family = "Qwen 2.5",
            url = "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf",
            approxSizeMb = 1100L,
            strengths = listOf("multilingual", "general"),
            language = "EN/ZH/ES/FR/DE/…",
            fileFormat = FileFormat.Gguf,
            category = ModelCategory.Multilingual,
            minDeviceTier = DeviceTier.Mid,
            ease = Ease.Easy,
        ),
        Entry(
            id = "smollm2-1.7b-instruct-q4_k_m",
            displayName = "SmolLM2-1.7B-Instruct · Q4_K_M",
            origin = "USA",
            license = "Apache 2.0",
            family = "SmolLM2",
            url = "https://huggingface.co/HuggingFaceTB/SmolLM2-1.7B-Instruct-GGUF/resolve/main/smollm2-1.7b-instruct-q4_k_m.gguf",
            approxSizeMb = 1100L,
            strengths = listOf("small", "chat"),
            language = "English-first",
            fileFormat = FileFormat.Gguf,
            category = ModelCategory.Chat,
            minDeviceTier = DeviceTier.Low,
            ease = Ease.Easy,
        ),
        Entry(
            id = "llama-3.2-1b-instruct-q4_k_m",
            displayName = "Llama-3.2-1B-Instruct · Q4_K_M",
            origin = "USA",
            license = "Llama community",
            family = "Llama 3.2",
            url = "https://huggingface.co/bartowski/Llama-3.2-1B-Instruct-GGUF/resolve/main/Llama-3.2-1B-Instruct-Q4_K_M.gguf",
            approxSizeMb = 900L,
            strengths = listOf("multilingual", "fast"),
            language = "EN/ES/FR/DE/IT/PT/…",
            fileFormat = FileFormat.Gguf,
            category = ModelCategory.Multilingual,
            minDeviceTier = DeviceTier.Low,
            ease = Ease.Easy,
        ),
        Entry(
            id = "deepseek-r1-distill-qwen-1.5b-q4_k_m",
            displayName = "DeepSeek-R1-Distill-Qwen-1.5B · Q4_K_M",
            origin = "China",
            license = "MIT",
            family = "DeepSeek R1 distill",
            url = "https://huggingface.co/bartowski/DeepSeek-R1-Distill-Qwen-1.5B-GGUF/resolve/main/DeepSeek-R1-Distill-Qwen-1.5B-Q4_K_M.gguf",
            approxSizeMb = 1100L,
            strengths = listOf("reasoning", "chain-of-thought"),
            language = "EN/ZH",
            fileFormat = FileFormat.Gguf,
            category = ModelCategory.Reasoning,
            minDeviceTier = DeviceTier.Mid,
            ease = Ease.Medium,
        ),
        // Phase 2 candidate — ONNX-distributed Phi-3.5-mini. Listed so
        // the user sees what an ONNX row looks like and what runtime
        // would carry it. The download is gated by the runtime being
        // shipped (today: no). Surfacing it in the catalog means the
        // format/runtime link is visible end-to-end.
        Entry(
            id = "phi-3.5-mini-onnx",
            displayName = "Phi-3.5-mini-instruct · ONNX",
            origin = "USA",
            license = "MIT",
            family = "Phi 3.5",
            url = "https://huggingface.co/microsoft/Phi-3.5-mini-instruct-onnx/resolve/main/cpu_and_mobile/cpu-int4-rtn-block-32-acc-level-4/model.onnx",
            approxSizeMb = 2400L,
            strengths = listOf("reasoning", "general"),
            language = "EN",
            fileFormat = FileFormat.Onnx,
            category = ModelCategory.Reasoning,
            minDeviceTier = DeviceTier.High,
            ease = Ease.Hard,
        ),
    )

    fun find(id: String): Entry? = all.firstOrNull { it.id == id }

    /**
     * Download a model to `<filesDir>/imported-models/<id>.gguf`. The
     * function returns a [DownloadOutcome] describing either the
     * produced file or a structured error. Cancellation is
     * cooperative — the caller can cancel the coroutine.
     *
     * Errors are surfaced distinctly so the UI can show a real reason
     * instead of the generic "download failed":
     *  - `UnknownHostException` → "no internet"
     *  - `SocketTimeoutException` → "server timed out"
     *  - `SSLException` → "Hugging Face certificate rejected"
     *  - HTTP non-2xx → "<code> from server"
     *  - body too small → "incomplete download"
     *  - anything else → "<class>: <message>"
     */
    suspend fun download(
        context: Context,
        entry: Entry,
        onProgress: (Long) -> Unit = {},
    ): DownloadOutcome = downloadFromUrl(
        context = context,
        id = entry.id,
        url = entry.url,
        approxSizeMb = entry.approxSizeMb,
        onProgress = onProgress,
    )

    /**
     * Same as [download] but the progress callback receives a richer
     * payload so the UI can render a percent bar AND a bytes-per-second
     * rate from the same stream. `bytesDownloaded` is the cumulative
     * bytes read so far; `totalBytes` may be zero when the server
     * didn't supply `Content-Length`.
     */
    suspend fun download(
        context: Context,
        entry: Entry,
        onProgress: (percent: Long, bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): DownloadOutcome = downloadFromUrl(
        context = context,
        id = entry.id,
        url = entry.url,
        approxSizeMb = entry.approxSizeMb,
        onProgressWithBytes = onProgress,
    )

    /**
     * Generic URL download used by the **Paste a URL** and **Import
     * from Git** entry points on the Models screen.
     *
     * The `id` is derived from the URL — sha1 of `url + filename` —
     * so re-importing the same URL overwrites the previous file.
     * Filenames are sanitized; `.gguf` / `.onnx` / `.safetensors`
     * extensions are detected and routed via the file-format hint.
     *
     * The `approxSizeMb` argument is a soft hint used only as a
     * fallback when the server omits `Content-Length`. It does
     * NOT gate the download — unknown sizes still proceed and
     * the caller surfaces a progress bar with indeterminate
     * percentage.
     */
    suspend fun downloadFromUrl(
        context: Context,
        url: String,
        id: String = idFromUrl(url),
        approxSizeMb: Long = 0L,
        onProgress: (Long) -> Unit = {},
        onProgressWithBytes: ((percent: Long, bytesDownloaded: Long, totalBytes: Long) -> Unit)? = null,
        expectedSha256: String? = null,
        onProgressWithPhase: ((com.meshlit.core.inference.models.ModelDownload.Progress) -> Unit)? = null,
    ): DownloadOutcome = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "imported-models").apply { mkdirs() }
        val ext = when {
            url.contains(".gguf", ignoreCase = true) -> ".gguf"
            url.contains(".onnx", ignoreCase = true) -> ".onnx"
            url.contains(".safetensors", ignoreCase = true) -> ".safetensors"
            else -> ".gguf"
        }
        val dest = File(dir, "${id}$ext")
        val tmp = File(dir, "${id}$ext.part")
        require(id.matches(Regex("[A-Za-z0-9._-]{1,180}"))) { "Invalid model identifier" }
        try {
            val token = ModelCredentials(context).token()
            val installed = artifactDownloader.download(url, dest, token, expectedSha256) { progress ->
                onProgressWithPhase?.invoke(progress)
                val percent = if (progress.phase == "Installed") 100L else (progress.percent ?: 0).toLong()
                onProgress(percent)
                onProgressWithBytes?.invoke(percent, progress.bytes, progress.total)
            }
            DownloadOutcome(installed, null, null)
        } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
        catch (error: Exception) {
            DownloadOutcome(null, "download_failed", error.message ?: "Model download failed")
        }
    }

    private val artifactDownloader = com.meshlit.core.inference.models.ModelDownload()

    /**
     * Stable id derivation for an arbitrary URL. We hash the URL so
     * re-importing the same link overwrites the previous file instead
     * of creating duplicates. The hash is the first 16 hex chars of
     * the SHA-1 of the URL — collision probability is negligible for
     * a phone-bound local cache.
     */
    fun idFromUrl(url: String): String {
        val md = java.security.MessageDigest.getInstance("SHA-1")
        val bytes = md.digest(url.toByteArray(Charsets.UTF_8))
        val hex = StringBuilder(16)
        for (i in 0 until 8) {
            hex.append(String.format("%02x", bytes[i].toInt() and 0xFF))
        }
        return "url-$hex"
    }

    /**
     * Result of a model download. Exactly one of [file] / [errorTag]
     * is non-null on a returned outcome.
     */
    data class DownloadOutcome(
        val file: File?,
        val errorTag: String?,
        val errorMessage: String?,
    ) {
        val isSuccess: Boolean get() = file != null
        val isFailure: Boolean get() = file == null
    }

    /**
     * Open the Hugging Face model card in the system browser. Used by
     * the "View on Hugging Face" link in the picker when the user
     * wants to read the model card themselves before downloading.
     */
    fun hfCardIntent(entry: Entry): Intent {
        val cardUrl = entry.url.substringBefore("/resolve/")
        return Intent(Intent.ACTION_VIEW, Uri.parse(cardUrl))
    }

    /** Return a SAF-readable content URI for the given file. */
    fun uriFor(context: Context, file: File): Uri {
        val authority = context.packageName + ".fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }

    /** Imported model files in the import directory. */
    fun importedFiles(context: Context): List<File> {
        val dir = File(context.filesDir, "imported-models")
        val supported = setOf("gguf", "onnx", "safetensors")
        return dir.listFiles { f ->
            f.isFile && f.extension.lowercase() in supported
        }?.sortedBy { it.name.lowercase() }.orEmpty()
    }

    private suspend fun ensureActive() {
        currentCoroutineContext().ensureActive()
    }
}
