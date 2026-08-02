package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * JVM-only stub inference engine. Echoes the prompt back as a
 * stream of deterministic tokens so the upper layers
 * (coordinator, foreground service, prompt UI, eGPU routing)
 * can be exercised end-to-end without the llama.cpp native
 * library.
 *
 * Token stream: takes the prompt's first ~30 words and replays
 * them token-by-token with a 12 ms delay. Realistic enough to
 * exercise streaming UI (progressive text reveal), the stop
 * button, max-tokens cutoff, and FinishReason reporting.
 *
 * Selection: the [com.meshlit.core.inference.InferenceCoordinator]
 * picks this engine when `System.getProperty("meshlit.inference.stub")`
 * is `"true"` or when the llama.cpp native library fails to load
 * (Phase 1 default).
 */
class JvmStubInferenceEngine(
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default,
) : InferenceEngine {

    override val engineTag: String = "stub"

    private val log = logger("JvmStubInferenceEngine")

    @Volatile private var modelInfo: ModelInfo? = null

    override fun isReady(): Boolean = modelInfo != null

    override fun loadedModel(): ModelInfo? = modelInfo

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        val path = request.modelPath
        if (path.isBlank()) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid("stub.inference.empty_path"),
            )
        }
        val file = File(path)
        if (!file.exists()) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid("stub.inference.missing_file:${path}"),
            )
        }
        val info = ModelInfo(
            modelPath = path,
            modelName = file.name,
            contextSize = request.contextSize,
            parameterCount = guessParameterCount(file.length()),
            quantization = guessQuantization(file.name),
            embeddingDim = 4096,
            sizeBytes = file.length(),
            loadedAtMs = System.currentTimeMillis(),
        )
        modelInfo = info
        log.info(
            "stub.loaded",
            "stub model loaded",
            mapOf(
                "model" to info.modelName,
                "sizeBytes" to info.sizeBytes,
                "params" to info.parameterCount,
                "quant" to info.quantization,
            ),
        )
        return MeshlitResult.Success(info)
    }

    override suspend fun unloadModel() {
        modelInfo = null
        log.info("stub.unloaded", "stub model unloaded")
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> =
        withContext(dispatcher) {
            val info = modelInfo ?: return@withContext MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid("stub.inference.not_loaded"),
            )
            val started = System.currentTimeMillis()
            val prompt = request.prompt.trim()
            val tokens = synthesizeDemoResponse(prompt)
            val toEmit = tokens.take(request.maxTokens.coerceAtLeast(1))
            val finalText = StringBuilder()
            var stopReason = FinishReason.NATURAL_STOP
            try {
                for ((idx, token) in toEmit.withIndex()) {
                    coroutineContext.ensureActive()
                    delay(12L)
                    request.onToken(token)
                    finalText.append(token)
                    if (request.stopSequences.isNotEmpty() && finalText.toString()
                            .contains(request.stopSequences.first())) {
                        stopReason = FinishReason.STOP_SEQUENCE
                        break
                    }
                    if (idx + 1 >= request.maxTokens) {
                        stopReason = FinishReason.MAX_TOKENS
                        break
                    }
                }
            } catch (t: Throwable) {
                stopReason = FinishReason.CANCELLED
                throw t
            }
            val durationMs = System.currentTimeMillis() - started
            val tps = if (durationMs > 0)
                finalText.length.toFloat() * 1000f / durationMs
            else 0f
            val result = InferenceResult(
                promptTokens = toEmit.size,
                generatedTokens = finalText.length,
                totalDurationMs = durationMs,
                tokensPerSecond = tps,
                finishReason = stopReason,
                finalText = finalText.toString(),
            )
            request.onComplete(result)
            log.info(
                "stub.infer.done",
                "stub inference complete",
                mapOf(
                    "model" to info.modelName,
                    "tokens" to result.generatedTokens,
                    "durationMs" to durationMs,
                    "reason" to result.finishReason.tag,
                ),
            )
            MeshlitResult.Success(result)
        }

    /**
     * Build a non-trivial demo reply from the prompt. Reads as a
     * reply, not a build-state apology: it acknowledges the prompt
     * in plain English and tells the user to load a real model.
     *
     * Why the simpler shape: the previous four-branch `closing`
     * rotation produced textbook-style copy ("the llama.cpp native
     * runtime is not yet linked into this APK"), which reads as
     * "the APK is broken" rather than "demo engine". The Jobs /
     * Agent / Terminal screens all surface this string verbatim, so
     * the wording lands in front of every user. Cleaning it up
     * costs us nothing.
     *
     * Not a real model. Once llama.cpp ships, the JNI engine
     * becomes authoritative and this stub is only used in unit
     * tests and on devices that don't have the .so.
     */
    private fun synthesizeDemoResponse(prompt: String): List<String> {
        if (prompt.isEmpty()) {
            return listOf("(stub) ", "ready", " when", " you", " are", ".")
        }
        val words = prompt.split(Regex("\\s+"))
            .map { it.trim().filter { ch -> ch.isLetterOrDigit() } }
            .filter { it.isNotEmpty() }
        val topic = if (words.isNotEmpty()) words.take(3).joinToString(" ") else "input"
        val out = mutableListOf<String>()
        fun emit(chunk: String) { out.add(chunk) }
        emit("(stub) ")
        emit("Got it — ")
        emit("\"${topic}\"")
        emit(" — this is a placeholder reply. ")
        emit("Open the Models tab and load a real model to get an actual answer.")
        return out
    }

    /**
     * Old "tokenize by whitespace" helper kept around only because
     * some unit tests may still reference it. Internally the stub
     * now uses [synthesizeDemoResponse] instead of echoing input.
     */
    @Suppress("unused")
    private fun tokenize(prompt: String): List<String> {
        if (prompt.isEmpty()) return listOf("(empty prompt)")
        val raw = prompt.split(Regex("(?<=\\s)|(?=\\s)"))
        val filtered = raw.filter { it.isNotBlank() }
        return if (filtered.isEmpty()) listOf("(empty prompt)") else filtered
    }

    /**
     * Best-effort parameter count from file size, assuming a
     * specific quantization. A 4-GB Q4_K_M GGUF is roughly a 7B
     * model; a 2-GB file is roughly 3B. The mapping is loose —
     * this is just a stub.
     */
    private fun guessParameterCount(sizeBytes: Long): Long {
        val sizeMb = sizeBytes / 1024 / 1024
        return when {
            sizeMb >= 12000 -> 70_000_000_000L
            sizeMb >= 7000 -> 30_000_000_000L
            sizeMb >= 4000 -> 13_000_000_000L
            sizeMb >= 2000 -> 7_000_000_000L
            sizeMb >= 1000 -> 3_000_000_000L
            sizeMb >= 500 -> 1_000_000_000L
            else -> 500_000_000L
        }
    }

    /** Best-effort quantization from the model filename. GGUF
     *  filenames typically include the quant tag, e.g.
     *  `llama-3.1-8b-instruct.Q4_K_M.gguf`. */
    private fun guessQuantization(name: String): String {
        val regex = Regex("(?i)(Q\\d+_K_(?:S|M|L)|Q\\d+_0|F16|F32|BF16)")
        return regex.find(name)?.value ?: "unknown"
    }
}