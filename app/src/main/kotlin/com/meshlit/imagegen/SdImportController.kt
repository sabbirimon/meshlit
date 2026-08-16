package com.meshlit.imagegen

import android.content.Context
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.importers.DownloadProgress
import com.meshlit.core.inference.importers.HttpStreamDownloader
import com.meshlit.loader.DownloadHandle
import com.meshlit.loader.DownloadProgressBus
import com.meshlit.models.BundleEntryView
import com.meshlit.models.BundleMember
import com.meshlit.models.SdModelBundles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Phase 4.x — Bundled Stable Diffusion downloader.
 *
 * Pulls a named [SdModelBundles] entry from the catalog and
 * writes its members to `context.filesDir/imported-models/<bundleId>/`.
 * Each member is downloaded serially in declared order; the global
 * [DownloadProgressBus] gets one tick per file so the banner shows
 * "SDXL Base 1.0 Q4_0 — UNet (1/3) 47%".
 *
 * Failure handling:
 *  - Required member fails → entire bundle fails. The caller
 *    surfaces the error and the partially-downloaded directory
 *    is left intact (the user can retry; partial files are
 *    overwritten by `HttpStreamDownloader`'s atomic rename).
 *  - Optional member fails → we log and skip; the bundle
 *    succeeds with a note in [FileSet.skippedMembers].
 *
 * Why serial?
 *  - One progress bar in the UI. Parallel downloads would force a
 *    multi-progress banner (Phase 2).
 *  - Hugging Face rate limits unauthenticated downloads to 3
 *    concurrent connections per IP; serial keeps us well under.
 *
 * Threading:
 *  - `bundle(...)` is `suspend` and runs the I/O on
 *    `Dispatchers.IO`. The caller can `viewModelScope.launch`
 *    it from Compose.
 *
 * Companion:
 *  - The settings writeback (`setImageGenSdXxxPath`) happens in
 *    [ImageGenScreen] after a successful bundle import — the
 *    controller only writes files, not preferences. Keeps the
 *    controller reusable from a future "import from SAF" flow.
 */
class SdImportController(
    private val context: Context,
    private val bus: DownloadProgressBus,
    private val downloader: SdBundleDownloader = HttpStreamDownloader().asBundleDownloader(),
    private val headersFor: (String) -> Map<String, String> = { emptyMap() },
) {    /**
     * The minimal downloader contract the controller needs.
     * Defaults to the real `HttpStreamDownloader`; tests inject a
     * stub that resolves immediately with a fake file.
     */
    fun interface SdBundleDownloader {
        suspend fun download(
            url: String,
            outFile: File,
            headers: Map<String, String>,
            onProgress: (DownloadProgress) -> Unit,
        ): MeshlitResult<File>
    }

    /**
     * Download every member of [bundleId] in order. Returns a
     * [FileSet] with the absolute paths on success. On a required
     * member failure the bus tick is marked Failed and a
     * [MeshlitResult.Failure] is returned; partial files are
     * left on disk so a retry resumes cleanly.
     *
     * @param onProgress per-file tick callback for the in-app UI
     *   (the global bus is updated automatically in parallel).
     */
    suspend fun bundle(
        bundleId: String,
        onProgress: (BundleTick) -> Unit = {},
    ): MeshlitResult<FileSet> = withContext(Dispatchers.IO) {
        val members = SdModelBundles.all[bundleId]
        if (members.isNullOrEmpty()) {
            return@withContext MeshlitResult.Failure(
                MeshlitError.Invalid("sd.bundle_unknown", IllegalStateException("Bundle $bundleId is not registered.")),
            )
        }
        val targetDir = File(context.filesDir, "imported-models/$bundleId").apply {
            if (!exists() && !mkdirs()) {
                return@withContext MeshlitResult.Failure(
                    MeshlitError.Resource("sd.mkdir_failed", IllegalStateException("Could not create $absolutePath")),
                )
            }
        }
        val completed = mutableMapOf<String, File>()
        val skipped = mutableListOf<String>()

        for ((index, member) in members.withIndex()) {
            val entry = resolveEntry(member.entryId)
                ?: return@withContext MeshlitResult.Failure(
                    MeshlitError.Invalid(
                        "sd.entry_missing",
                        IllegalStateException("Catalog entry ${member.entryId} not found in ModelCatalog."),
                    ),
                )
            val outFile = File(targetDir, File(entry.url).name)
            val displayName = "${entry.displayName.ifBlank { entry.id }} (${index + 1}/${members.size})"
            val handle = bus.start(displayName, totalBytes = entry.approxSizeBytes)
            val memberResult = runCatching {
                downloader.download(
                    url = entry.url,
                    outFile = outFile,
                    headers = headersFor(entry.url),
                    onProgress = { p: DownloadProgress ->
                        // Throttle by virtue of the downloader's
                        // 250 ms cadence; the bus re-emits for the
                        // banner. The per-file callback is fired
                        // *inside* the download lambda — we can't
                        // await a suspend from a non-suspend lambda,
                        // so this is fire-and-forget best-effort via
                        // runBlocking (one-frame cost).
                        runBlocking { bus.update(handle, p.receivedBytes, p.totalBytes) }
                    },
                )
            }.getOrElse { MeshlitResult.Failure(MeshlitError.Network("sd.member_threw", it)) }

            when (memberResult) {
                is MeshlitResult.Success -> {
                    bus.complete(handle)
                    completed[member.role] = memberResult.value
                    onProgress(
                        BundleTick(
                            bundleId = bundleId,
                            memberId = member.entryId,
                            role = member.role,
                            index = index,
                            total = members.size,
                            fraction = (index + 1).toFloat() / members.size,
                            file = memberResult.value,
                        ),
                    )
                }
                is MeshlitResult.Failure -> {
                    if (member.required) {
                        bus.fail(handle, memberResult.error.tag)
                        return@withContext MeshlitResult.Failure(memberResult.error)
                    } else {
                        // Non-required: best-effort skip. Clear the
                        // bus so the next member's start() isn't
                        // ignored.
                        bus.complete(handle)
                        skipped += member.entryId
                    }
                }
            }
        }

        MeshlitResult.Success(
            FileSet(
                bundleId = bundleId,
                targetDir = targetDir,
                byRole = completed.toMap(),
                skippedMembers = skipped.toList(),
            ),
        )
    }

    /**
     * Look up a bundle member in the in-memory catalog. We don't
     * depend on a specific ModelCatalog entry shape — just need
     * a URL, an approximate size, and a display name. The
     * resolution is a small adapter so this controller doesn't
     * have to import the full ModelCatalog.kt surface.
     */
    private fun resolveEntry(entryId: String): BundleEntryView? {
        val all = SdModelBundles.allCatalog
        return all[entryId]
    }
}

/**
 * Per-file progress callback payload. The UI uses this to update
 * its own per-member progress bar (in addition to the global
 * bus that drives the bottom-banner).
 */
data class BundleTick(
    val bundleId: String,
    val memberId: String,
    val role: String,
    val index: Int,
    val total: Int,
    val fraction: Float,
    val file: File,
)

/**
 * Filesystem layout returned from a successful bundle import.
 * `byRole` maps the [BundleMember.role] string ("unet",
 * "text_encoder", "vae", "taesd") to the absolute path. The
 * ImageGenScreen reads this and writes the four settings slots.
 */
data class FileSet(
    val bundleId: String,
    val targetDir: File,
    val byRole: Map<String, File>,
    val skippedMembers: List<String>,
)

/** Convenience to close the Handle without leaking. */
internal suspend fun DownloadHandle.closeQuietly(bus: DownloadProgressBus) {
    runCatching { bus.complete(this) }
}

/** Adapt the concrete `HttpStreamDownloader` to the functional
 *  controller-side interface so tests can inject a stub.
 */
internal fun HttpStreamDownloader.asBundleDownloader(): SdImportController.SdBundleDownloader =
    SdImportController.SdBundleDownloader { url, outFile, headers, onProgress ->
        download(url, outFile, headers, onProgress)
    }