package com.meshlit.desktop

/** One owned model at a time. Every request binds its own catalog identity. */
internal class IdeEngineSession(private val options: () -> EngineOptions,
    private val running: () -> Boolean, private val start: (EngineOptions, StudioBundledModel) -> LocalSession) {
    constructor(options: () -> EngineOptions, running: () -> Boolean, start: (EngineOptions) -> LocalSession) :
        this(options, running, { settings, _ -> start(settings) })
    private var session: LocalSession? = null
    private var modelId: String? = null
    @Synchronized fun load(requestedModelId: String? = null): LocalSession {
        val model = StudioBundledModels.resolve(requestedModelId)
        session?.takeIf { modelId == model.id && running() }?.let { return it }
        // Clear identity first: failed switches cannot silently reuse the previous route.
        session = null; modelId = null
        return start(options().also { it.validate() }, model).also { session = it; modelId = model.id }
    }
}

internal fun ideOutputBudget(local: LocalSession?, thinking: Boolean = false): Int = local?.let { minOf(if (thinking) 2048 else 1024, it.context / 2) } ?: 1024
