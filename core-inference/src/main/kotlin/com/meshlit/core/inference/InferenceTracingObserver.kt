package com.meshlit.core.inference

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.observability.LogSource
import com.meshlit.core.observability.TracerHolder
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.trace.Span
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

/**
 * Decorator for [InferenceEngine] that emits stable spans around
 * model load, unload, and inference. It intentionally does not
 * change engine behaviour or buffering — callers can wrap the
 * concrete engine at composition time.
 */
class InferenceTracingObserver(
    private val delegate: InferenceEngine,
    private val logSink: InferenceLogSink = NoopInferenceLogSink,
    private val enabled: () -> Boolean = { true },
) : InferenceEngine {

    override val engineTag: String get() = delegate.engineTag
    override fun isReady(): Boolean = delegate.isReady()
    override fun loadedModel(): ModelInfo? = delegate.loadedModel()

    override suspend fun loadModel(request: ModelLoadRequest): MeshlitResult<ModelInfo> {
        if (!enabled()) return delegate.loadModel(request)
        return TracerHolder.span(
            "inference.model.load",
            mapOf("model.path" to request.modelPath, "engine" to engineTag),
        ) { span ->
            logSink.onInference("model.load", "Loading ${request.modelPath}")
            val result = delegate.loadModel(request)
            span.setAttribute(AttributeKey.stringKey("result"), result.javaClass.simpleName)
            logSink.onInference("model.load", "Loaded ${request.modelPath}")
            result
        }
    }

    override suspend fun unloadModel() {
        if (!enabled()) {
            delegate.unloadModel()
            return
        }
        TracerHolder.span("inference.model.unload", mapOf("engine" to engineTag)) {
            delegate.unloadModel()
            logSink.onInference("model.unload", "Model unloaded")
        }
    }

    override suspend fun infer(request: InferenceRequest): MeshlitResult<InferenceResult> {
        if (!enabled()) return delegate.infer(request)
        val model = delegate.loadedModel()?.modelName ?: delegate.loadedModel()?.modelPath ?: "unknown"
        return TracerHolder.span(
            "inference.run",
            mapOf("model" to model, "engine" to engineTag),
        ) { span ->
            val started = TimeSource.Monotonic.markNow()
            logSink.onInference("inference", "Inference started", mapOf("model" to model))
            try {
                val result = delegate.infer(request)
                span.setAttribute(AttributeKey.longKey("duration_ms"), started.elapsedNow().inWholeMilliseconds)
                logSink.onInference("inference", "Inference finished", mapOf("duration_ms" to started.elapsedNow().inWholeMilliseconds))
                result
            } catch (t: Throwable) {
                logSink.onInference("inference", "Inference failed: ${t.message}")
                throw t
            }
        }
    }
}

interface InferenceLogSink {
    fun onInference(tag: String, message: String, context: Map<String, Any?> = emptyMap())
}

object NoopInferenceLogSink : InferenceLogSink {
    override fun onInference(tag: String, message: String, context: Map<String, Any?>) = Unit
}
