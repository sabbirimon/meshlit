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
internal enum class InferenceProfile(val label: String) {
    COMPACT("Low memory"), BALANCED("Balanced"), LONG("Long context");

    fun options(processors: Int, base: EngineOptions): EngineOptions {
        val available = processors.coerceIn(1, 128)
        val result = when (this) {
            COMPACT -> base.copy(context = 1024, threads = minOf(2, available), batchThreads = minOf(2, available),
                batch = 128, microBatch = 32, keyCache = "q8_0")
            BALANCED -> base.copy(context = 4096, threads = minOf(4, available), batchThreads = minOf(8, available),
                batch = 512, microBatch = 128, keyCache = "f16")
            LONG -> base.copy(context = 8192, threads = minOf(4, available), batchThreads = minOf(8, available),
                batch = 512, microBatch = 128, keyCache = "q8_0")
        }
        return result.also { it.validate(available) }
    }
}

@Composable private fun <T> EngineChoice(label: String, value: T, choices: List<T>, display: (T) -> String, select: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        OutlinedButton(onClick = { open = true }) { Text("$label · ${display(value)} ▾") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            choices.forEach { item ->
                DropdownMenuItem(onClick = { open = false; select(item) }) {
                    Text((if (item == value) "✓ " else "") + display(item))
                }
            }
        }
    }
}

@Composable internal fun EnginePanel(options: EngineOptions, session: LocalSession?, onChange: (EngineOptions) -> Unit) {
    val processors = Runtime.getRuntime().availableProcessors().coerceIn(1, 128)
    var draft by remember(options) { mutableStateOf(options) }
    var advanced by remember { mutableStateOf(false) }
    val profile = InferenceProfile.entries.firstOrNull { it.options(processors, draft) == draft }
    Text("Local inference", style = MaterialTheme.typography.h6)
    Text(session?.let { "${it.backend} · ${it.context} active tokens · estimated ${it.admission.estimatedBytes / 1048576} MiB" }
        ?: "No local model loaded")
    EngineChoice("Profile", profile?.label ?: "Custom", InferenceProfile.entries.map { it.label }, { it }) { label ->
        draft = InferenceProfile.entries.first { it.label == label }.options(processors, draft)
    }
    Text("${draft.context} context tokens · ${draft.threads} generation threads · ${draft.keyCache} key cache")
    EngineChoice("Unload after idle", draft.idleMinutes, listOf(0, 1, 5, 15, 30),
        { if (it == 0) "Keep loaded" else "$it min" }) { draft = draft.copy(idleMinutes = it) }
    TextButton({ advanced = !advanced }) { Text(if (advanced) "Hide advanced settings" else "Advanced settings") }
    if (advanced) {
        EngineChoice("CPU backend", draft.cpuMode, CpuMode.entries.toList(), { it.label }) { draft = draft.copy(cpuMode = it) }
        EngineChoice("Context", draft.context, (512..8192 step 256).toList(), { "$it tokens" }) { draft = draft.copy(context = it) }
        EngineChoice("Generation threads", draft.threads, (1..processors).toList(), Int::toString) { draft = draft.copy(threads = it) }
        EngineChoice("Prompt threads", draft.batchThreads, (1..processors).toList(), Int::toString) { draft = draft.copy(batchThreads = it) }
        EngineChoice("Prompt batch", draft.batch, listOf(128, 256, 512, 1024, 2048), Int::toString) {
            draft = draft.copy(batch = it, microBatch = minOf(draft.microBatch, it))
        }
        EngineChoice("Micro-batch", draft.microBatch, listOf(32, 64, 128, 256, 512).filter { it <= draft.batch }, Int::toString) {
            draft = draft.copy(microBatch = it)
        }
        EngineChoice("Key cache", draft.keyCache, listOf("f16", "q8_0"), { it }) { draft = draft.copy(keyCache = it) }
        Text("q8_0 reduces key-cache memory and can affect quality. Model loading checks context capacity and available RAM.",
            style = MaterialTheme.typography.caption)
    }
    if (draft != options) {
        Text("Applying changes ends the current chat and unloads the model.")
        Row {
            Button({ onChange(draft) }) { Text("Apply settings") }
            TextButton({ draft = options }) { Text("Discard changes") }
        }
    }
}
