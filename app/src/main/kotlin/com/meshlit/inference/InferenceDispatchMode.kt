package com.meshlit.inference

/**
 * How the Jobs screen sends a prompt.
 *
 *  - [LOCAL]  → fire `ACTION_INFER` against the in-process
 *               `InferenceForegroundService`. Tokens arrive through
 *               `coordinator.events`. This is the original Phase 1
 *               path; works without any networking.
 *  - [REMOTE] → open a `RemoteInferenceClient` to a peer IP and
 *               stream the SSE reply. This is the path task #7 adds.
 *
 * The toggle on the Jobs screen switches between them. When [REMOTE]
 * is selected the IP field is enabled; when [LOCAL] is selected it
 * is greyed out (so users can't be confused about where prompts are
 * going).
 */
enum class InferenceDispatchMode {
    LOCAL,
    REMOTE,
}