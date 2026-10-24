package com.meshlit.hyperl

import androidx.annotation.RequiresApi
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.common.control.OperationGate
import com.meshlit.core.hyperl.DatasetSummary
import com.meshlit.core.hyperl.LargeData
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.UUID

/** App-private, no-backup dataset workspace. No keys or plaintext enter model/agent results. */
@RequiresApi(26)
class HyperLDatasets(private val root: File, private val gate: OperationGate) {
    companion object {
        const val MAX_BYTES=64L*1024*1024
        const val MAX_DATASETS=4
        // Also prevents competing screen/controller instances exceeding the entry quota.
        private val capacity=Semaphore(1)
    }
    private val idPattern=Regex("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}")
    init {
        check(root.mkdirs() || root.isDirectory)
        check(Files.isDirectory(root.toPath(),LinkOption.NOFOLLOW_LINKS))
        if(capacity.tryAcquire())try {
            root.listFiles().orEmpty().filter {
                it.name.matches(Regex("\\.(pending|export)-[A-Za-z0-9_-]{1,100}"))
            }.forEach(::cleanup)
        }finally{capacity.release()}
    }
    private fun cleanup(file:File) {
        if(!Files.exists(file.toPath(),LinkOption.NOFOLLOW_LINKS))return
        Files.walk(file.toPath()).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }
    fun ids():List<String> = root.listFiles().orEmpty().filter { file ->
        file.name.matches(idPattern) && Files.isDirectory(file.toPath(),LinkOption.NOFOLLOW_LINKS) &&
            File(file,"data/manifest.aesgcm").isFile && File(file,"key").isFile
    }.map { it.name }.sorted()
    private fun entry(id:String):File {
        require(id.matches(idPattern) && id in ids()) { "Dataset no longer available" }
        return File(root,id)
    }
    private suspend fun <T> bounded(work:suspend()->T):T {
        check(capacity.tryAcquire()) { "Dataset workspace busy" }
        try { return gate.run(ManagedFeature.HYPERL) { withTimeout(60_000) { withContext(Dispatchers.IO) { work() } } } }
        finally { capacity.release() }
    }
    private suspend fun create(work:suspend(File)->DatasetSummary):Pair<String,DatasetSummary> {
        require(ids().size<MAX_DATASETS) { "Four dataset limit; remove a dataset before importing or rotating" }
        val stage=Files.createTempDirectory(root.toPath(),".pending-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toFile()
        try {
            LargeData.keygen(File(stage,"key").toPath())
            val summary=work(stage)
            currentCoroutineContext().ensureActive()
            val id=UUID.randomUUID().toString()
            val destination=File(root,id).toPath()
            check(!Files.exists(destination,LinkOption.NOFOLLOW_LINKS))
            Files.move(stage.toPath(),destination,StandardCopyOption.ATOMIC_MOVE)
            return id to summary
        } finally { cleanup(stage) }
    }
    suspend fun import(open:()->InputStream):Pair<String,DatasetSummary> = bounded {
        create { stage -> open().use { LargeData.import(it,File(stage,"data").toPath(),File(stage,"key").toPath(),MAX_BYTES) } }
    }
    suspend fun verify(id:String):DatasetSummary = bounded {
        val entry=entry(id)
        LargeData.verify(File(entry,"data").toPath(),File(entry,"key").toPath(),MAX_BYTES)
    }
    suspend fun rotate(id:String):Pair<String,DatasetSummary> = bounded {
        val original=entry(id)
        create { stage -> LargeData.rekey(File(original,"data").toPath(),File(stage,"data").toPath(),
            File(original,"key").toPath(),File(stage,"key").toPath(),MAX_BYTES) }
    }
    /** Whole dataset verifies before any bytes reach a selected document provider. */
    suspend fun export(id:String,write:suspend(File)->Unit):DatasetSummary = bounded {
        val entry=entry(id)
        val stage=Files.createTempDirectory(root.toPath(),".export-",PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toFile()
        try {
            val file=File(stage,"plaintext")
            val summary=LargeData.export(File(entry,"data").toPath(),file.toPath(),File(entry,"key").toPath(),MAX_BYTES)
            currentCoroutineContext().ensureActive();write(file);summary
        } finally { cleanup(stage) }
    }
    suspend fun delete(id:String)=bounded {
        val entry=entry(id)
        cleanup(entry)
    }
}
