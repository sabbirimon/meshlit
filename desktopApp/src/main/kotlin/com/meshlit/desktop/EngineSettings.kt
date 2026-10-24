package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import com.meshlit.core.inference.models.GgufModelMetadata
import java.util.prefs.Preferences

/** Native CPU options only. No remote execution, accelerator claims or grant changes. */
internal data class EngineOptions(val context: Int = 4096, val threads: Int = minOf(4, Runtime.getRuntime().availableProcessors()),
                                  val batchThreads: Int = minOf(8, Runtime.getRuntime().availableProcessors()),
                                  val batch: Int = 512, val microBatch: Int = 128, val keyCache: String = "f16", val idleMinutes: Int = 5, val cpuMode: CpuMode = CpuMode.AUTO) {
    fun validate(processors: Int = Runtime.getRuntime().availableProcessors()) {
        require(context in 512..8192 && context % 256 == 0)
        require(threads in 1..minOf(128, processors) && batchThreads in 1..minOf(128, processors))
        require(batch in setOf(128,256,512,1024,2048) && microBatch in setOf(32,64,128,256,512) && microBatch <= batch)
        require(keyCache in setOf("f16", "q8_0", "q4_0") && idleMinutes in setOf(0,1,5,15,30))
    }
    fun nativeArgs() = listOf("--ctx-size", context.toString(), "--threads", threads.toString(), "--threads-batch", batchThreads.toString(),
        "--batch-size", batch.toString(), "--ubatch-size", microBatch.toString(), "--cache-type-k", keyCache, "--cache-type-v", "f16",
        "--fit", "off", "--device", "none", "--n-gpu-layers", "0", "--no-agent", "--no-webui", "--offline")
    fun admission(modelBytes: Long, metadata: GgufModelMetadata, availableBytes: Long): EngineAdmission {
        validate()
        require(modelBytes in 24..32L*1024*1024*1024 && availableBytes in 1..(1L shl 50))
        val maxContext = metadata.maxContext
        require(maxContext != null && context <= maxContext) { "GGUF context capacity is unknown or smaller than requested" }
        val kv = requireNotNull(metadata.kvBytes(context,keyCache)) { "Cannot estimate KV memory for this architecture" }
        val required = kotlin.math.ceil(modelBytes*1.15 + kv*1.25).toLong()+512L*1024*1024
        val budget = (availableBytes*.70).toLong().minus(256L*1024*1024).coerceAtLeast(0)
        require(required <= budget) { "Model weights, context/KV and graph estimate exceed available memory budget" }
        return EngineAdmission(required, budget, kv)
    }
}
internal data class EngineAdmission(val estimatedBytes: Long, val budgetBytes: Long, val kvBytes: Long)
internal class EnginePreferences(private val store: Preferences = Preferences.userRoot().node("com/meshlit/desktop-preview/engine")) {
    fun load(): EngineOptions = runCatching { EngineOptions(
        store.getInt("context",4096),store.getInt("threads",minOf(4,Runtime.getRuntime().availableProcessors())),
        store.getInt("batchThreads",minOf(8,Runtime.getRuntime().availableProcessors())),store.getInt("batch",512),store.getInt("microBatch",128),
        store.get("keyCache","f16"),store.getInt("idleMinutes",5),CpuMode.valueOf(store.get("cpuMode","AUTO"))).also {it.validate()}.let { if(it.keyCache=="q4_0") it.copy(keyCache="f16") else it } }.getOrDefault(EngineOptions())
    fun save(options: EngineOptions) {
        options.validate(); store.putInt("context",options.context);store.putInt("threads",options.threads);store.putInt("batchThreads",options.batchThreads)
        store.putInt("batch",options.batch);store.putInt("microBatch",options.microBatch);store.put("keyCache",options.keyCache);store.putInt("idleMinutes",options.idleMinutes);store.put("cpuMode",options.cpuMode.name);store.flush()
    }
}
@Composable internal fun EnginePanel(options: EngineOptions, session: LocalSession?, onChange: (EngineOptions) -> Unit) {
    Text("CPU inference engine", style = MaterialTheme.typography.h6)
    Text("Settings apply on the next load. A change unloads the old model and starts a fresh chat. Memory admission uses GGUF architecture/KV metadata and actual available RAM; estimates are not reserved memory.")
    Row { CpuMode.entries.forEach { mode -> TextButton({onChange(options.copy(cpuMode=mode))}) {Text((if(options.cpuMode==mode) "✓ " else "")+mode.label)} } }
    Text("Context: ${options.context} tokens")
    Row { listOf(512,1024,2048,4096,8192).forEach { n -> TextButton({onChange(options.copy(context=n))}) {Text(n.toString())} } }
    Text("Generation CPU threads: ${options.threads}")
    val processors = Runtime.getRuntime().availableProcessors().coerceIn(1,128)
    var threadDraft by remember(options.threads) { mutableStateOf(options.threads.toFloat()) }
    Slider(threadDraft,{threadDraft=it},valueRange=1f..processors.coerceAtLeast(2).toFloat(),
        steps=processors.coerceAtLeast(2)-2,enabled=processors>1,
        onValueChangeFinished={val chosen=threadDraft.toInt().coerceIn(1,processors);if(chosen!=options.threads) onChange(options.copy(threads=chosen))})
    Text("Prompt processing threads: ${options.batchThreads}")
    Row { listOf(1,2,4,8,16,32).filter {it<=Runtime.getRuntime().availableProcessors()}.forEach {n-> TextButton({onChange(options.copy(batchThreads=n))}) {Text(n.toString())} } }
    Text("Prompt batch / micro-batch: ${options.batch} / ${options.microBatch}")
    Row { listOf(128,256,512,1024,2048).forEach {n-> TextButton({onChange(options.copy(batch=n,microBatch=minOf(options.microBatch,n)))}) {Text(n.toString())} } }
    Row { listOf(32,64,128,256,512).filter {it<=options.batch}.forEach {n-> TextButton({onChange(options.copy(microBatch=n))}) {Text("μ$n")} } }
    Text("Key cache precision · value cache remains f16")
    Row { listOf("f16","q8_0").forEach {kind-> TextButton({onChange(options.copy(keyCache=kind))}) {Text((if(options.keyCache==kind) "✓ " else "")+kind)} } }
    Text("f16 is the default. q8_0 reduces KV memory and may affect answer quality.",style=MaterialTheme.typography.caption)
    Text("Unload after idle · ${if(options.idleMinutes==0) "keep loaded" else "${options.idleMinutes} minutes"}")
    Row { listOf(0,1,5,15,30).forEach {n-> TextButton({onChange(options.copy(idleMinutes=n))}) {Text(if(n==0) "Never" else "$n min")} } }
    if(session!=null) {
        Text("${session.backend} · native context verified: ${session.context} · admission estimate ${session.admission.estimatedBytes/1048576} MiB · KV estimate ${session.admission.kvBytes/1048576} MiB")
    }
    Text("One loaded model and one request at a time. mmap and prompt-prefix caching are managed by the native runtime. CPU/thread/batch settings need hardware-specific measurement; more threads do not guarantee higher speed. GPU/NPU and distributed RPC execution are separate qualification paths.",style=MaterialTheme.typography.caption)
}
