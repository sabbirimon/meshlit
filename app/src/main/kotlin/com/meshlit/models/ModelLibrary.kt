package com.meshlit.models

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.inference.RunAnywhereCatalog
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import okhttp3.HttpUrl.Companion.toHttpUrl

enum class ModelDownloadBackend { RUNANYWHERE, VERIFIED_HTTP }

@Serializable
data class LibraryModel(
    val id: String, val name: String, val url: String = "", val path: String = "",
    val sizeBytes: Long = 0, val sha256: String? = null, val source: String = "Imported",
    val phase: String = "Not installed", val bytes: Long = 0, val total: Long = -1,
    val speed: Long = 0, val error: String? = null, val sdkManaged:Boolean=false,
    val bundled:Boolean=false, val downloadBackend:String="VERIFIED_HTTP",
    val metadata:GgufModelMetadata?=null,val runtimeOptions:ModelRuntimeOptions=ModelRuntimeOptions(),
) {
    val active get() = phase in setOf("Queued", "Downloading", "Importing", "Validating")
    val installed get() = path.isNotEmpty() && phase == "Installed"
}

/** One process-wide library shared by chat, model UI and notification service.
 * Interrupted transfers survive as resumable artifacts; launch never starts them silently. */
class ModelLibrary(private val context: Context, private val coordinator: InferenceCoordinator,
                   private val appScope: CoroutineScope,private val nativeHost:com.meshlit.pipeline.PipelineHost) {
    private val directory = File(context.filesDir, "imported-models").apply { mkdirs() }
    private val prefs = context.getSharedPreferences("model-library-v1", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val _models = MutableStateFlow<List<LibraryModel>>(emptyList())
    val models: StateFlow<List<LibraryModel>> = _models.asStateFlow()
    private val _startupEnabled=MutableStateFlow(prefs.getBoolean("startup-enabled",true))
    val startupEnabled=_startupEnabled.asStateFlow()
    private val _startupId=MutableStateFlow(prefs.getString("startup-model","").orEmpty())
    val startupId=_startupId.asStateFlow()
    private val _startupStatus=MutableStateFlow("Waiting for model library")
    val startupStatus=_startupStatus.asStateFlow()
    fun setStartupEnabled(enabled:Boolean){prefs.edit().putBoolean("startup-enabled",enabled).commit();_startupEnabled.value=enabled}
    fun setStartupModel(id:String){require(models.value.any{it.id==id && it.installed});prefs.edit().putString("startup-model",id).commit();_startupId.value=id;setStartupEnabled(true)}
    suspend fun loadAtBoot(){
        ready.await()
        if(!_startupEnabled.value){_startupStatus.value="Startup loading is off";return}
        val selected=_startupId.value.ifBlank{prefs.getString("last-loaded","").orEmpty()}
        val entry=if(selected.isNotBlank()) models.value.firstOrNull{it.id==selected && it.installed}
            else models.value.firstOrNull{it.bundled && it.installed} ?: models.value.firstOrNull{it.installed}
        if(entry==null){_startupStatus.value=if(selected.isNotBlank()) "Startup model is missing. Download or select another model." else "Download or import a model to enable startup loading.";return}
        try{
            if(inferenceBusy()){_startupStatus.value="An inference session is already active";return}
            val estimated=(entry.sizeBytes*1.3).toLong()+256L*1024*1024
            require(estimated<availableMemory()){ "Startup model exceeds the current memory estimate. Load manually or choose a smaller model." }
            _startupStatus.value="Loading ${entry.name}"
            withTimeout(180_000){load(entry.id,onlyIfIdle=true)}
            _startupStatus.value=if(coordinator.loadedModel()?.modelPath==entry.path) "Startup model ready: ${entry.name}" else "An inference session is already active"
        }catch(cancelled:CancellationException){_startupStatus.value="Startup loading interrupted";throw cancelled}
        catch(error:Exception){_startupStatus.value="Startup model failed: ${error.message ?: "Open diagnostics"}"}
    }
    private fun inferenceBusy()=coordinator.loadedModel()!=null || coordinator.state.value is CoordinatorState.Loading || coordinator.state.value is CoordinatorState.Generating
    private val jobs = ConcurrentHashMap<String, Job>()
    private val persistLock = Any()
    private val importLock = Mutex()
    private val downloadSlots=Semaphore(2)
    private val loadLock = Mutex()
    val ready = appScope.async(Dispatchers.IO) { reconcile() }

    private suspend fun reconcile() {
        val saved = runCatching { json.decodeFromString<List<LibraryModel>>(prefs.getString("models", "[]")!!) }.getOrDefault(emptyList())
        val catalog = RunAnywhereCatalog.all.flatMap { e -> e.sources.filter{it.url.startsWith("https://")}.mapIndexed { index,source ->
            LibraryModel(if(index==0) e.id else ModelCatalog.idFromUrl(source.url),
                "${e.displayName} · ${source.quant.name.takeUnless{it=="UNKNOWN"} ?: source.url.substringAfterLast('/').substringBeforeLast(".gguf")} · ${source.org}",
                if (index==0 && e.id == RunAnywhereInferenceEngine.DEFAULT_MODEL_ID) STARTER_URL else source.url,
                sizeBytes = source.approxSizeBytes, source = "Hugging Face · size estimate",
                sha256 = if (index==0 && e.id == RunAnywhereInferenceEngine.DEFAULT_MODEL_ID) STARTER_SHA else null)
        } }
        val merged = catalog.map { e -> saved.firstOrNull { it.id == e.id }?.copy(url=e.url, sha256=e.sha256) ?: e } +
            saved.filter { s -> catalog.none { it.id == s.id } }
        val paths = merged.map { it.path }.toSet()
        val discovered = directory.listFiles()?.filter { it.isFile && it.extension.equals("gguf", true) && it.absolutePath !in paths }
            ?.map { f -> LibraryModel("disk-${ModelCatalog.idFromUrl(f.absolutePath)}", f.nameWithoutExtension,
                path=f.absolutePath, sizeBytes=f.length(), source="On device", phase="Installed") }.orEmpty()
        _models.value = (merged + discovered).map { e ->
            val local = e.path.takeIf { it.isNotBlank() }?.let(::File) ?: File(directory, "${e.id}.gguf")
            if (local.isFile) {
                try { ModelFiles.validateGguf(local); e.sha256?.let { require(ModelFiles.sha256(local)==it) { "Model checksum mismatch" } }; e.copy(path=local.absolutePath, sizeBytes=local.length(), phase="Installed", error=null,metadata=runCatching{GgufMetadata.read(local)}.getOrNull()) }
                catch (_: Exception) { e.copy(path="", phase="Failed", error="Invalid model file; import or download again") }
            } else if (e.active) e.copy(phase="Paused", path="", error="Transfer interrupted; tap Resume")
            else e.copy(path="", phase=if(e.installed) "Not installed" else e.phase)
        }
        // Install the actual APK asset before selecting the first-boot model.
        // A failure is a visible library entry, never a fabricated Installed state.
        val installer=BundledModelInstaller()
        val manifest=installer.manifest(context)
        val bundled=try {
            val file=installer.ensureInstalled(context)
            (context.applicationContext as? com.meshlit.MeshlitApplication)?.setBundledModelPath(file)
            LibraryModel(manifest.id,manifest.name,manifest.url,file.absolutePath,manifest.sizeBytes,manifest.sha256,
                source="Bundled · ${manifest.license}",phase="Installed",bundled=true,metadata=GgufMetadata.read(file),
                runtimeOptions=saved.firstOrNull{it.id==manifest.id}?.runtimeOptions ?: ModelRuntimeOptions())
        } catch(cancelled:CancellationException) {throw cancelled}
        catch(error:Exception) { LibraryModel(manifest.id,manifest.name,manifest.url,sizeBytes=manifest.sizeBytes,
            sha256=manifest.sha256,source="Bundled · ${manifest.license}",phase="Failed",error=error.message,bundled=true) }
        _models.update { list -> listOf(bundled)+list.filter{it.id!=manifest.id} }
        // Retire only the previous app-owned 135M starter after the replacement
        // passed its checksum. Preserve user imports and other model choices.
        val legacy=saved.firstOrNull { it.id=="smollm2-135m-instruct-q4_k_m" && it.bundled }
        val legacyFile=legacy?.path?.let(::File)
        val ownedDir=File(context.filesDir,BundledModelInstaller.TARGET_SUBDIR)
        if(bundled.installed && manifest.id!=legacy?.id && legacyFile?.name=="SmolLM2-135M-Instruct-Q4_K_M.gguf" &&
            legacyFile.parentFile?.canonicalFile==ownedDir.canonicalFile && (!legacyFile.exists() || legacyFile.delete())) {
            _models.update { list -> list.filterNot { it.id==legacy.id } }
            val edit=prefs.edit()
            if(prefs.getString("last-loaded","")==legacy.id) edit.remove("last-loaded")
            if(_startupId.value==legacy.id) { edit.remove("startup-model");_startupId.value="" }
            edit.commit()
        }
        persist()
    }

    fun deviceSnapshot()=DeviceRuntimeProbe.read(context)
    fun devicePlan()=DeviceRuntimePolicy.plan(deviceSnapshot())
    fun recommend(entry:LibraryModel):Boolean {
        val plan=devicePlan()
        return DeviceRuntimePolicy.fits(plan,entry.sizeBytes,entry.metadata?.kvBytes(plan.recommendedContext,"f16"))
    }
    fun downloadRecommendation(id:String):String {
        val advice=CompactModelRecommendations.assess(deviceSnapshot()).firstOrNull{it.model.id==id}
            ?: error("Unknown model recommendation")
        require(advice.hardwareFit){advice.reason}
        require(freeStorage()>=advice.storageNeeded){"Free space for the model, runtime copy and storage reserve before downloading"}
        return addHubArtifact(advice.model.artifact,ModelDownloadBackend.VERIFIED_HTTP)
    }
    fun addHubArtifact(artifact:HubArtifact,backend:ModelDownloadBackend):String {
        val id=addUrl(artifact.url,artifact.fileName.removeSuffix(".gguf"),startDownload=false)
        change(id){it.copy(sizeBytes=artifact.sizeBytes ?: 0,sha256=artifact.sha256,source="Hugging Face · ${artifact.repo} @ ${artifact.revision.take(8)}")}
        persist();download(id,backend);return id
    }
    fun nativeRuntimeAvailable()=nativeHost.available()
    suspend fun setRuntimeOptions(id:String,options:ModelRuntimeOptions) {
        ready.await()
        val entry=models.value.first{it.id==id}
        options.validate(entry.metadata?.maxContext)
        require(options.backend!=LocalModelBackend.NATIVE_LOCAL || nativeHost.available()) { "Native local runtime is unavailable for this ABI" }
        change(id){it.copy(runtimeOptions=options)};persist()
    }

    private fun change(id: String, edit: (LibraryModel) -> LibraryModel) {
        _models.update { list -> list.map { if(it.id == id) edit(it) else it } }
    }
    private fun persist() = synchronized(persistLock) {
        prefs.edit().putString("models", json.encodeToString(_models.value.map { it.copy(speed=0) })).apply()
    }

    fun addUrl(url: String, name: String = "",startDownload:Boolean=true):String {
        require(ready.isCompleted) { "Model library is still starting" }
        val parsed = url.toHttpUrl()
        require(parsed.isHttps && parsed.username.isEmpty() && parsed.password.isEmpty()) { "Enter a direct HTTPS GGUF URL" }
        require(parsed.encodedPath.endsWith(".gguf", true)) { "This loader supports GGUF files; choose a direct file URL" }
        val id = ModelCatalog.idFromUrl(url)
        _models.update { list -> if (list.any { it.id == id }) list else list + LibraryModel(id,
            name.ifBlank { parsed.pathSegments.last().removeSuffix(".gguf") }, url, source=parsed.host,runtimeOptions=AccelerationPreferences(context).newModelOptions()) }
        persist()
        if(startDownload) download(id)
        return id
    }

    fun download(id: String,backend:ModelDownloadBackend=ModelDownloadBackend.VERIFIED_HTTP):Job? {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.MODEL_TRANSFERS)
        if(jobs.containsKey(id)) return null
        val job = appScope.launch(start=CoroutineStart.LAZY) {
            ready.await()
            val entry = _models.value.firstOrNull { it.id == id } ?: return@launch
            require(entry.url.startsWith("https://")) { "This model has no download source" }
            change(id) { it.copy(phase="Queued", error=null,downloadBackend=backend.name) }; persist()
            try {
                com.meshlit.power.PowerRepository(context).downloadBlockReason()?.let{error(it)}
                downloadSlots.withPermit {
                val file:File
                var actualHash=entry.sha256
                if(backend==ModelDownloadBackend.RUNANYWHERE) {
                    val engine=coordinator.runAnywhereEngine()
                    engine.initialize(context)
                    require(engine.isInitialized()) { "RunAnywhere backend failed to initialize" }
                    // Private/gated repositories use the verified HTTPS path with the saved token.
                    engine.downloadModelById(entry.id,entry.url,entry.name).collect { progress ->
                        progress.error?.let { throw java.io.IOException(it) }
                        if(progress.state.contains("FAILED") || progress.state.contains("CANCELLED"))
                            throw java.io.IOException("RunAnywhere download: ${progress.state}")
                        change(id) { it.copy(phase="Downloading",bytes=progress.bytesDownloaded,total=progress.totalBytes,speed=0) }
                    }
                    change(id) { it.copy(phase="Validating") }
                    file=engine.downloadedModelFile(entry.id)
                    ModelFiles.validateGguf(file)
                    actualHash=ModelFiles.sha256(file)
                    require(entry.sha256==null || actualHash==entry.sha256) { "Downloaded model checksum mismatch" }
                } else {
                val result = ModelCatalog.downloadFromUrl(context, entry.url, id,
                    expectedSha256=entry.sha256, onProgressWithPhase = { progress ->
                        change(id) { it.copy(phase=progress.phase, bytes=progress.bytes, total=progress.total,
                            speed=progress.bytesPerSecond) }
                    })
                file = result.file ?: throw java.io.IOException(result.errorMessage ?: "Download failed")
                actualHash=ModelFiles.sha256(file)
                }
                change(id) { it.copy(path=file.absolutePath, sizeBytes=file.length(), phase="Installed", bytes=file.length(),
                    total=file.length(), speed=0, error=null,metadata=runCatching{GgufMetadata.read(file)}.getOrNull(),sha256=actualHash,sdkManaged=backend==ModelDownloadBackend.RUNANYWHERE) }
                }
            } catch(cancelled: CancellationException) {
                change(id) { it.copy(phase="Paused", speed=0, error=null) }; throw cancelled
            } catch(error: Exception) {
                change(id) { it.copy(phase="Failed", speed=0, error=error.message ?: "Download failed") }
            } finally { jobs.remove(id); persist() }
        }
        if(jobs.putIfAbsent(id, job) == null) {
            job.invokeOnCompletion { jobs.remove(id, job) }
            try { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java)); job.start() }
            catch(error: Exception) { jobs.remove(id); job.cancel(); change(id) { it.copy(phase="Failed", error="Cannot start download service: ${error.javaClass.simpleName}") }; persist() }
        } else {job.cancel();return null}
        return job
    }
    private val powerPolicyMonitor=appScope.launch {
        ready.await()
        while(isActive) {
            if(com.meshlit.power.PowerRepository(context).downloadBlockReason()!=null) pauseDownloads()
            delay(5000)
        }
    }
    fun hasActiveTransfers() = jobs.isNotEmpty()
    fun pause(id: String) { jobs[id]?.cancel() }
    fun pauseAll() { jobs.values.forEach { it.cancel() } }
    fun pauseDownloads() { _models.value.filter{it.url.startsWith("https://")}.forEach{jobs[it.id]?.cancel()} }

    fun importFiles(uris: List<Uri>):List<String> {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.MODEL_TRANSFERS)
        val imported=mutableListOf<String>()
        require(uris.size <= 32) { "Import at most 32 files at a time" }
        for(uri in uris) {
            val id = "import-${java.util.UUID.randomUUID()}"
            imported.add(id)

            val job = appScope.launch(start=CoroutineStart.LAZY) {
                ready.await()
                _models.update { it + LibraryModel(id, "Importing model", phase="Queued",runtimeOptions=AccelerationPreferences(context).newModelOptions()) }
                try {
                    importLock.withLock { withContext(Dispatchers.IO) {
                        val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null,null,null)?.use { cursor ->
                            if(cursor.moveToFirst()) cursor.getString(0) else null } ?: "Model.gguf"
                        require(name.endsWith(".gguf", true)) { "Only single-file GGUF models are supported by this loader" }
                        change(id) { it.copy(name=name.removeSuffix(".gguf"), phase="Importing") }; persist()
                        val part = File(directory, "$id.gguf.part")
                        val dest = File(directory, "$id.gguf")
                        try {
                            context.contentResolver.openInputStream(uri)?.use { input -> FileOutputStream(part).use { output ->
                                val buffer=ByteArray(64*1024); var bytes=0L; var last=0L
                                while(true) {
                                    currentCoroutineContext().ensureActive()
                                    val n=input.read(buffer); if(n<0) break
                                    bytes+=n; require(bytes<=com.meshlit.core.inference.models.ModelDownload.MAX_BYTES) { "Model is too large" }
                                    output.write(buffer,0,n)
                                    if(System.nanoTime()-last>200_000_000) {
                                        require(directory.usableSpace>com.meshlit.core.inference.models.ModelDownload.RESERVE) { "Insufficient storage" }
                                        change(id) { it.copy(bytes=bytes) }; last=System.nanoTime()
                                    }
                                }
                                output.fd.sync()
                            } } ?: error("Cannot read the selected file")
                            change(id) { it.copy(phase="Validating") }
                            ModelFiles.validateGguf(part)
                            val hash=ModelFiles.sha256(part)
                            require(part.renameTo(dest)) { "Cannot finalize model import" }
                            change(id) { it.copy(path=dest.absolutePath, sizeBytes=dest.length(), bytes=dest.length(),total=dest.length(),
                                sha256=hash,source="Imported",phase="Installed",metadata=runCatching{GgufMetadata.read(dest)}.getOrNull()) }
                        } finally { part.delete() }
                    } }
                } catch(cancelled: CancellationException) { change(id) { it.copy(phase="Failed",error="Import cancelled; select the file again") }; throw cancelled }
                catch(error: Exception) { change(id) { it.copy(phase="Failed",error=error.message ?: "Import failed") } }
                finally { jobs.remove(id); persist() }
            }
            jobs[id]=job
            job.invokeOnCompletion { jobs.remove(id, job) }
            try { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, ModelDownloadService::class.java));job.start() }
            catch(error: Exception) { jobs.remove(id);job.cancel();change(id) { it.copy(phase="Failed", error="Cannot start import service") };persist() }
        }
        return imported
    }

    suspend fun awaitTransfer(id:String,cancelOwned:Job?=null,cancelImport:Boolean=false):LibraryModel {
        ready.await()
        val task=jobs[id]
        try{task?.join()}catch(cancelled:CancellationException){cancelOwned?.cancel();if(cancelImport) task?.cancel();throw cancelled}
        return models.value.firstOrNull{it.id==id && it.installed} ?: error("Model transfer did not install a valid file")
    }

    suspend fun load(id: String,onlyIfIdle:Boolean=false) = loadLock.withLock {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.INFERENCE)
        com.meshlit.power.PowerRepository(context).loadBlockReason()?.let{error(it)}
        if(onlyIfIdle && inferenceBusy()) return@withLock
        ready.await()
        val model = _models.value.first { it.id == id }
        require(model.installed) { "Install the model before loading" }
        val plan=devicePlan()
        require(plan.localInferenceSupported) { "This OS/API/ABI has no installed supported inference engine" }
        require(plan.allowHeavyWork) { plan.reasons.joinToString("; ") }
        require(model.runtimeOptions.backend!=LocalModelBackend.NATIVE_LOCAL || model.runtimeOptions.contextSize<=plan.maxContext) { "Context exceeds this device's safe policy cap (${plan.maxContext})" }
        model.runtimeOptions.validate(model.metadata?.maxContext)
        if(model.runtimeOptions.backend==LocalModelBackend.NATIVE_LOCAL) {
            require(DeviceRuntimePolicy.fits(plan,model.sizeBytes,model.metadata?.kvBytes(model.runtimeOptions.contextSize,model.runtimeOptions.keyCacheType))) { "Model/context exceeds current memory budget; select fewer tokens or a smaller model" }
            nativeHost.startLocal(model.path,model.runtimeOptions)
            prefs.edit().putString("last-loaded",id).commit()
            return@withLock
        }
        coordinator.runAnywhereEngine().initialize(context)
        require(coordinator.runAnywhereEngine().isInitialized()) { "Local inference backend failed to initialize; retry or open diagnostics" }
        val result = coordinator.loadModel(model.path, contextSize=2048)
        if(result is MeshlitResult.Failure) error(result.error.tag)
        prefs.edit().putString("last-loaded",id).commit()
    }
    suspend fun delete(id: String) = loadLock.withLock {
        require(jobs[id] == null) { "Pause the transfer before deleting" }
        val entry=_models.value.first { it.id==id }
        require(!entry.bundled) { "The starter is part of the APK. Unload it or switch off startup loading." }
        val loaded=coordinator.loadedModel()?.modelPath
        if(loaded==entry.path) coordinator.unloadModel()
        withContext(Dispatchers.IO) {
            if(entry.sdkManaged) {
                val engine=coordinator.runAnywhereEngine();engine.initialize(context)
                val result=engine.deleteDownloadedModel(entry.id)
                if(result is MeshlitResult.Failure) error(result.error.tag)
                change(id){it.copy(path="",phase="Not installed",bytes=0,total=-1,error=null,sdkManaged=false)};persist()
                return@withContext
            }
            val file=File(entry.path.ifBlank { File(directory,"$id.gguf").absolutePath })
            require(file.parentFile?.canonicalFile == directory.canonicalFile) { "This file is not managed by the model library" }
            if(file.exists()) check(file.delete()) { "Cannot delete model file" }
            File(directory,"${id}.gguf.part").delete();File(directory,"${id}.gguf.resume").delete()
            change(id) { it.copy(path="",phase="Not installed",bytes=0,total=-1,error=null) };persist()
        }
    }
    fun freeStorage(): Long = directory.usableSpace
    fun availableMemory(): Long = ActivityManager.MemoryInfo().also {
        (context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)
    }.availMem

    companion object {
        const val STARTER_URL="https://huggingface.co/HuggingFaceTB/SmolLM2-360M-Instruct-GGUF/resolve/593b5a2e04c8f3e4ee880263f93e0bd2901ad47f/smollm2-360m-instruct-q8_0.gguf"
        const val STARTER_SHA="48ab3034d0dd401fbc721eb1df3217902fee7dab9078992d66431f09b7750201"
    }
}
