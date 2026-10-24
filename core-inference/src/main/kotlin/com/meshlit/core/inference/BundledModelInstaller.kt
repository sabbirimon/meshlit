package com.meshlit.core.inference

import android.content.Context
import com.meshlit.core.inference.models.ModelFiles
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream

@Serializable data class BundledModelManifest(val id:String,val name:String,val filename:String,
    val sizeBytes:Long,val sha256:String,val revision:String,val url:String,val license:String)

/** Required release asset: verify the pinned manifest and actual installed bytes.
 * Installation is serialized, cancellable, and finalized only after verification. */
class BundledModelInstaller(private val assetsDir:String=DEFAULT_ASSETS_DIR) {
    fun manifest(context:Context):BundledModelManifest = context.assets.open("$assetsDir/bundled-model.json").bufferedReader().use {
        Json.decodeFromString<BundledModelManifest>(it.readText())
    }.also {
        require(it.filename.matches(Regex("[A-Za-z0-9_.-]+\\.gguf"))) { "Invalid bundled model filename" }
        require(it.sizeBytes>24 && it.sha256.matches(Regex("[0-9a-f]{64}"))) { "Invalid bundled model manifest" }
    }
    suspend fun ensureInstalled(context:Context,onProgress:((Long,Long)->Unit)?=null):File = withContext(Dispatchers.IO) {
        lock.withLock {
            val m=manifest(context)
            val dir=File(context.filesDir,TARGET_SUBDIR).apply { check(mkdirs() || isDirectory) }
            val target=File(dir,m.filename)
            if(target.isFile && target.length()==m.sizeBytes && ModelFiles.sha256(target)==m.sha256) {
                ModelFiles.validateGguf(target)
                return@withLock target
            }
            require(dir.usableSpace>m.sizeBytes+64L*1024*1024) { "Not enough storage to install the bundled model" }
            val part=File(dir,"${m.filename}.part")
            try {
                context.assets.open("$assetsDir/${m.filename}").use { input -> FileOutputStream(part).use { output ->
                    val buffer=ByteArray(64*1024);var bytes=0L
                    while(true) {
                        currentCoroutineContext().ensureActive()
                        val n=input.read(buffer);if(n<0) break
                        bytes+=n;require(bytes<=m.sizeBytes) { "Bundled model exceeds manifest size" }
                        output.write(buffer,0,n);onProgress?.invoke(bytes,m.sizeBytes)
                    }
                    output.fd.sync()
                } }
                require(part.length()==m.sizeBytes && ModelFiles.sha256(part)==m.sha256) { "Bundled model checksum mismatch" }
                ModelFiles.validateGguf(part)
                check(part.renameTo(target)) { "Cannot finalize bundled model installation" }
                File(dir,SENTINEL_NAME).writeText(m.sha256)
                target
            } finally { part.delete() }
        }
    }
    fun installedFile(context:Context):File?=File(File(context.filesDir,TARGET_SUBDIR),manifest(context).filename).takeIf{it.isFile}
    companion object {
        const val DEFAULT_ASSETS_DIR="models"
        const val TARGET_SUBDIR="bundled-models"
        const val SENTINEL_NAME=".extracted.sha256"
        private val lock=Mutex()
    }
}
