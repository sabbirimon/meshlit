package com.meshlit.core.inference

import android.content.Context
import android.content.res.AssetManager
import com.meshlit.core.common.logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest

/**
 * One-shot installer that streams a bundled GGUF out of the APK's
 * `assets/models/` directory into the app's internal storage and
 * verifies the install via a SHA-256 sentinel.
 *
 * Design:
 *  - The extractor is idempotent. On each call it SHA-256s the
 *    source asset and compares against `<filesDir>/bundled-models/.extracted.sha256`.
 *    If the sentinel matches, the existing file is returned as-is.
 *    If the sentinel is missing, the file is missing, or the asset
 *    bytes differ (re-install with a newer model), the file is
 *    re-extracted.
 *  - The bundled asset is registered with `noCompress` so it can be
 *    `mmap`'d directly inside the APK if a future engine wants to
 *    skip extraction. We still extract to a real path because the
 *    current `InferenceCoordinator.loadModel(modelPath: String)`
 *    takes a `File` path, not an `AssetFileDescriptor`.
 *  - The installer never deletes other files in the bundled-models
 *    directory; it's safe to leave user-added GGUFs alongside.
 *
 * Concurrency:
 *  - Multiple callers in the same process (e.g. FGS `onCreate` and
 *    Jobs screen retry) may race. The sentinel check + atomic write
 *    pattern keeps the on-disk file consistent, but the duplicate
 *    work is harmless. We mark the call as best-effort with a
 *    `synchronized` block on the application context.
 *
 * Errors:
 *  - Asset missing → returns `null` (caller decides what to do).
 *  - IO failure → throws `IOException`. The caller should log and
 *    skip the auto-load.
 */
class BundledModelInstaller(
    private val assetsDir: String = DEFAULT_ASSETS_DIR,
) {

    private val log = logger("BundledModelInstaller")

    /**
     * Ensure the bundled GGUF is extracted into the app's internal
     * storage. Returns the extracted file if successful, or `null`
     * if no `.gguf` asset is present.
     *
     * @param onProgress optional progress callback (bytesCopied, totalBytes).
     */
    suspend fun ensureInstalled(
        context: Context,
        onProgress: ((Long, Long) -> Unit)? = null,
    ): File? {
        synchronized(LOCK) {
            val targetDir = File(context.filesDir, TARGET_SUBDIR).apply { mkdirs() }
            val sentinel = File(targetDir, SENTINEL_NAME)

            val list = listBundledAssets(context.assets)
            if (list.isEmpty()) {
                log.info("bundled_model.no_asset", "no .gguf in assets/$assetsDir")
                return null
            }
            // Single-file case for now. Skip if a future build ships
            // multiple; the user can pick a custom path in Models.
            val assetName = list.first()
            val target = File(targetDir, assetName)

            val sourceHash = sha256OfAsset(context.assets, "$assetsDir/$assetName")
            val existingHash = sentinel.takeIf { it.exists() }?.readText()?.trim()

            if (existingHash == sourceHash && target.exists() && target.length() > 0L) {
                log.info(
                    "bundled_model.up_to_date",
                    "sentinel matches, skipping extract",
                    mapOf("name" to assetName, "bytes" to target.length()),
                )
                return target
            }

            log.info(
                "bundled_model.extract.start",
                "extracting bundled model",
                mapOf(
                    "name" to assetName,
                    "sizeBytes" to sourceHash.length, // overwritten below; just a marker
                ),
            )

            val totalBytes = assetSize(context.assets, "$assetsDir/$assetName")
            extractAsset(
                context.assets,
                "$assetsDir/$assetName",
                target,
                onProgress,
            )

            val extractedHash = sha256OfFile(target)
            if (extractedHash != sourceHash) {
                // Best-effort: delete the corrupt file so the next
                // attempt re-extracts cleanly.
                runCatching { target.delete() }
                throw IOException(
                    "SHA-256 mismatch after extract: asset=$sourceHash file=$extractedHash"
                )
            }
            sentinel.writeText(sourceHash)
            log.info(
                "bundled_model.extract.done",
                "bundled model extracted",
                mapOf(
                    "name" to assetName,
                    "sizeBytes" to target.length(),
                    "sha256" to sourceHash.take(12),
                ),
            )
            return target
        }
    }

    /**
     * Returns the path of the currently-installed bundled model, or
     * `null` if nothing has been extracted yet. Cheap; no IO other
     * than a `File.exists()`.
     */
    fun installedFile(context: Context): File? {
        val targetDir = File(context.filesDir, TARGET_SUBDIR)
        val sentinel = File(targetDir, SENTINEL_NAME)
        if (!sentinel.exists()) return null
        val name = sentinel.readText().trim().let { _ ->
            // Re-discover the installed file by listing the directory.
            targetDir.listFiles { f -> f.isFile && f.name.endsWith(".gguf") }
                ?.firstOrNull()
        }
        return name
    }

    private fun listBundledAssets(am: AssetManager): List<String> {
        val prefix = if (assetsDir.endsWith("/")) assetsDir else "$assetsDir/"
        return runCatching { am.list(assetsDir) ?: emptyArray() }
            .getOrDefault(emptyArray())
            .filter { it.endsWith(".gguf") }
            .map { prefix + it.substringBeforeLast(".") + ".gguf" }
            .map { it.removePrefix(prefix) }
            .distinct()
    }

    private fun assetSize(am: AssetManager, path: String): Long {
        return runCatching {
            am.openFd(path).use { it.length }
        }.getOrDefault(-1L)
    }

    private fun extractAsset(
        am: AssetManager,
        assetPath: String,
        target: File,
        onProgress: ((Long, Long) -> Unit)?,
    ) {
        val tmp = File(target.parentFile, "${target.name}.tmp")
        runCatching { tmp.delete() }
        var copied = 0L
        am.open(assetPath).use { input ->
            FileOutputStream(tmp).use { output ->
                val buf = ByteArray(BUFFER_BYTES)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    output.write(buf, 0, n)
                    copied += n
                    onProgress?.invoke(copied, -1L)
                }
                output.flush()
            }
        }
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }

    private fun sha256OfAsset(am: AssetManager, path: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        am.open(path).use { input ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun sha256OfFile(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(BUFFER_BYTES)
            while (true) {
                val n = input.read(buf)
                if (n <= 0) break
                digest.update(buf, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DEFAULT_ASSETS_DIR = "models"
        const val TARGET_SUBDIR = "bundled-models"
        const val SENTINEL_NAME = ".extracted.sha256"
        private const val BUFFER_BYTES = 1 shl 16 // 64 KiB
        private val LOCK = Any()
    }
}
