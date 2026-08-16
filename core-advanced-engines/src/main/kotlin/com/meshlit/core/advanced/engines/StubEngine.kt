package com.meshlit.core.advanced.engines

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import java.io.File

/**
 * Reusable base for engine stubs. Real engine implementations should
 * bypass this and implement [BaseEngine] directly.
 *
 * The stub:
 *   - returns [MeshlitResult.Success] with a deterministic placeholder
 *     so the UI flow is end-to-end testable.
 *   - tags every log line with `"stub"` so QA can grep for them.
 *   - never touches native code or model artifacts.
 *
 * Subclasses only need to override [placeholderFor].
 */
abstract class StubEngine<Req, Resp>(
    override val id: String,
    override val category: EngineCategory,
) : BaseEngine<Req, Resp> {

    private val log = logger("StubEngine:$id")
    @Volatile private var path: File? = null

    override val modelPath: File? get() = path
    override val isLoaded: Boolean get() = path != null

    override suspend fun load(path: File): MeshlitResult<Unit> {
        this.path = path
        log.info("engine.stub.load", "loaded stub", mapOf("path" to path.absolutePath))
        return MeshlitResult.Success(Unit)
    }

    override suspend fun run(request: Req): MeshlitResult<Resp> {
        if (!isLoaded) {
            return MeshlitResult.Failure(
                com.meshlit.core.common.MeshlitError.Invalid("engine_not_loaded:$id"),
            )
        }
        log.info("engine.stub.run", "stub run", mapOf("id" to id))
        return MeshlitResult.Success(placeholderFor(request))
    }

    override suspend fun unload(): MeshlitResult<Unit> {
        path = null
        return MeshlitResult.Success(Unit)
    }

    /** Build the deterministic placeholder response for a request. */
    protected abstract fun placeholderFor(request: Req): Resp
}
