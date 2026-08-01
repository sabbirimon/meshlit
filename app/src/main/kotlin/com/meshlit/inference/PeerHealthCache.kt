package com.meshlit.inference

import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory cache of last-known per-peer health. Updated by the FGS
 * once every [REFRESH_INTERVAL_MS]; entries go stale after
 * [STALE_AFTER_MS] and are reported as `ok=false` by [snapshot].
 *
 * Threading:
 *  - Internal map is [ConcurrentHashMap] — safe under the FGS
 *    coroutine that calls [refreshLoop] and the inference-handler
 *    coroutine that calls [snapshot].
 *  - Exposed [state] is a [StateFlow] so observers (the router,
 *    future UI) can react to changes.
 *
 * Why in-memory and not DataStore:
 *  - Health is ephemeral. A stale value from yesterday is wrong
 *    today; persisting it would mislead the router on cold start.
 *  - The first request after a process restart refreshes the cache
 *    on demand.
 */
class PeerHealthCache(
    private val factory: RemoteInferenceClientFactory,
) {

    private val log = logger("PeerHealthCache")

    private val map = ConcurrentHashMap<String, PeerHealth>()

    private val _state = MutableStateFlow<Map<String, PeerHealth>>(emptyMap())
    val state: StateFlow<Map<String, PeerHealth>> = _state.asStateFlow()

    /** Snapshot a single peer's last-known health, or null if unknown. */
    fun snapshot(peer: String): PeerHealth? = map[peer]

    /** Snapshot the whole map, marking entries older than [STALE_AFTER_MS] as ok=false. */
    fun snapshotAll(): Map<String, PeerHealth> {
        val now = System.currentTimeMillis()
        return map.mapValues { (_, h) ->
            if (now - h.asOfMs > STALE_AFTER_MS) h.copy(ok = false) else h
        }
    }

    /**
     * Refresh the cache for every peer in [peers]. Probes both
     * `/v1/health` and `/v1/model` (in parallel per peer; sequentially
     * across peers to avoid stampedes). Updates [state].
     *
     * Safe to call concurrently — duplicate work is harmless because
     * [MutableStateFlow] emits idempotently.
     */
    suspend fun refresh(scope: CoroutineScope, peers: List<String>) {
        val now = System.currentTimeMillis()
        peers.forEach { ip ->
            scope.launch {
                val client = factory.build("http://$ip:${com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT}")
                val health = client.health()
                val model = if (health is com.meshlit.core.common.MeshlitResult.Success) {
                    client.modelState()
                } else null
                val ok = health is com.meshlit.core.common.MeshlitResult.Success
                val modelLoaded = (model as? com.meshlit.core.common.MeshlitResult.Success)
                    ?.value?.loaded == true
                val entry = PeerHealth(ok = ok, modelLoaded = modelLoaded, asOfMs = System.currentTimeMillis())
                map[ip] = entry
                _state.value = map.toMap()
                log.info(
                    "refresh.one",
                    "peer probed",
                    mapOf(
                        "ip" to ip,
                        "ok" to ok,
                        "modelLoaded" to modelLoaded,
                        "ms" to (entry.asOfMs - now),
                    ),
                )
            }
        }
    }

    /**
     * Long-running loop. Reads the current peer list from
     * [registry] once, then probes them every [REFRESH_INTERVAL_MS].
     * Reacts to changes in [registry] by re-reading the list.
     *
     * Stops cleanly when [scope] is cancelled.
     */
    suspend fun refreshLoop(
        scope: CoroutineScope,
        registry: PeerRegistry,
    ) {
        log.info("loop.start", "PeerHealthCache loop started")
        try {
            while (scope.isActive) {
                val peers = registry.peers.first()
                if (peers.isNotEmpty()) {
                    refresh(scope, peers)
                } else {
                    // Empty list — clear cache.
                    map.clear()
                    _state.value = emptyMap()
                }
                delay(REFRESH_INTERVAL_MS)
            }
        } finally {
            log.info("loop.stop", "PeerHealthCache loop stopped")
        }
    }

    /** Snapshot type used by the router. */
    data class PeerHealth(
        val ok: Boolean,
        val modelLoaded: Boolean,
        val asOfMs: Long,
    )

    companion object {
        /** 30s polling. Long enough to avoid chatty traffic; short enough to recover fast. */
        const val REFRESH_INTERVAL_MS = 30_000L

        /** After 60s without a refresh, treat the entry as unhealthy. */
        const val STALE_AFTER_MS = 60_000L
    }
}

/** The HTTP port used by peers. Mirrors [com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT]. */
private const val PEER_HTTP_PORT = com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT