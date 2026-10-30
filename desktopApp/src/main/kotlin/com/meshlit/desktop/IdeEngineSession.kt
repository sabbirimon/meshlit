package com.meshlit.desktop

/** Workbench and Compose consume the same saved options; an active model is never silently retuned. */
internal class IdeEngineSession(private val options: () -> EngineOptions,
    private val running: () -> Boolean, private val start: (EngineOptions) -> LocalSession) {
    private var session: LocalSession? = null
    @Synchronized fun load(): LocalSession {
        session?.takeIf { running() }?.let { return it }
        return start(options().also { it.validate() }).also { session = it }
    }
}

internal fun ideOutputBudget(local: LocalSession?): Int = local?.let { minOf(1024, it.context / 2) } ?: 1024
