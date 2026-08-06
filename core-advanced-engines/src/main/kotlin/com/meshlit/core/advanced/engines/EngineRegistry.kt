package com.meshlit.core.advanced.engines

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import java.util.concurrent.ConcurrentHashMap

/**
 * Thread-safe registry of [BaseEngine] instances, indexed by category.
 *
 * Engines are lazy-registered via [register] and lazy-resolved via
 * [forCategory]. The registry itself is stateless w.r.t. model loading —
 * each engine handles its own load/unload lifecycle.
 *
 * Why a registry and not DI: the Advanced hub wants to enumerate every
 * available engine by category to render "Select a model" pickers, so
 * a single look-up-by-category is the right shape.
 */
class EngineRegistry {
    private val log = logger("EngineRegistry")
    private val byCategory = ConcurrentHashMap<EngineCategory, MutableList<BaseEngine<*, *>>>()

    /** Add an engine to the registry. Re-registering an engine with the
     *  same [BaseEngine.id] replaces the previous entry. */
    @Synchronized
    fun <Req, Resp> register(engine: BaseEngine<Req, Resp>) {
        val list = byCategory.getOrPut(engine.category) { mutableListOf() }
        // Replace existing entry with the same id.
        list.removeAll { it.id == engine.id }
        list += engine
        log.info(
            "engine.register",
            "registered",
            mapOf("id" to engine.id, "category" to engine.category.name),
        )
    }

    /** All engines for a category, in registration order. */
    fun forCategory(category: EngineCategory): List<BaseEngine<*, *>> =
        byCategory[category]?.toList().orEmpty()

    /** Convenience: find the first engine registered for [category]. */
    fun firstFor(category: EngineCategory): BaseEngine<*, *>? =
        byCategory[category]?.firstOrNull()

    /** All categories that have at least one engine registered. */
    fun categories(): Set<EngineCategory> = byCategory.keys.toSet()

    /** Engine count, summed across categories. Useful for tests. */
    fun size(): Int = byCategory.values.sumOf { it.size }

    /**
     * Unload every registered engine and clear the registry. Used by
     * the engine settings screen to force a clean state.
     */
    suspend fun unloadAll(): MeshlitResult<Unit> {
        val errors = mutableListOf<Throwable>()
        byCategory.values.flatten().forEach { engine ->
            runCatching { engine.unload() }
                .onFailure { errors += it }
        }
        byCategory.clear()
        return if (errors.isEmpty()) MeshlitResult.Success(Unit)
        else MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Unknown(errors.first()))
    }
}
