package com.meshlit.inference

/**
 * How the Jobs screen sends a prompt.
 *
 *  - [LOCAL]   → fire `ACTION_INFER` against the in-process
 *                `InferenceForegroundService`. Tokens arrive
 *                through `coordinator.events`. This is the
 *                original Phase 1 path; works without any
 *                networking.
 *  - [REMOTE]  → open a `RemoteInferenceClient` to a peer IP and
 *                stream the SSE reply. The user supplies the
 *                IP via the inline remote-IP field on the
 *                input row.
 *  - [CLUSTER] → resolve the first reachable cluster peer via
 *                `MeshlitApplication.clusterDispatch.firstPeer()`
 *                and stream through the same `RemoteEvent`
 *                pipeline as [REMOTE]. When no peer is
 *                reachable the UI surfaces a synthetic
 *                "[no cluster peers reachable]" bubble.
 *
 * The picker on the Jobs screen top bar switches between them.
 * [REMOTE] reveals an IP field; [CLUSTER] hides it (the peer
 * is discovered, not typed).
 */
enum class InferenceDispatchMode {
    LOCAL,
    REMOTE,
    CLUSTER,
}