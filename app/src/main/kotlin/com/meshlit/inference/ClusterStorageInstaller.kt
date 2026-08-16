package com.meshlit.inference

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.logger
import com.meshlit.core.inference.cluster.ClusterStorageIncubator
import com.meshlit.core.inference.cluster.PeerCapabilities
import com.meshlit.core.inference.cluster.ShardTransport
import com.meshlit.models.ModelCatalog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * App-side glue that wires the cluster-shard incubator into the
 * existing model catalog and peer registry.
 *
 * Resolver strategy:
 *  - Look up the curated `ModelCatalog.Entry` first; if present, use
 *    it as the source-of-truth (URL, size, bundled flag).
 *  - Else fall back to `RunAnywhereCatalog.Entry` (sdk-cdn models
 *    are not currently sharded — they go through the SDK's own
 *    downloader; this is reserved for a future plan).
 *  - SHA-256 is the canonical SHA published alongside the model on
 *    Hugging Face; we accept zero SHA for SDK-cdn models and let
 *    the assembler skip verification when it is unknown.
 *
 * Why model-id → URL isn't computed here: the URL is curated by
 * `ModelCatalog.Entry.url`, and the codebase's policy is that the
 * catalog is the only place that hard-codes HTTPS endpoints. The
 * incubator takes a generic `ModelSource` so the catalog can grow
 * without code changes.
 */
object ClusterStorageInstaller {

    /** Cache TTL for fetched /v1/capabilities documents. 60 s is a
     *  good balance — long enough to avoid hammering peers when the
     *  planner rebursts, short enough that peer state changes show
     *  up within a minute. Tuned to match the `PeerHealthCache` refresh
     *  cadence so the two layers stay in step. */
    private const val CAPABILITIES_CACHE_TTL_MS: Long = 60_000L

    fun install(app: MeshlitApplication) {
        ClusterStorageIncubator.install {
            ClusterStorageIncubator(
                context = app,
                modelResolver = { modelId -> resolveModelSource(app, modelId) },
                bundledResolver = { modelId ->
                    // Only the bundled Qwen2.5-1.5B starter is installable
                    // via the bundled path today. Other bundled assets
                    // would be added here when shipped.
                    if (modelId == "qwen2.5-1.5b-instruct-q4_k_m") {
                        app.bundledModelPath() ?: runCatching {
                            app.bundledModelInstaller.ensureInstalled(app)
                        }.getOrNull()
                    } else null
                },
                wholeModelDownloader = { source ->
                    val url = source.downloadUrl
                        ?: error("model ${source.modelId} has no download URL")
                    val outcome = withContext(Dispatchers.IO) {
                        ModelCatalog.downloadFromUrl(
                            context = app.applicationContext,
                            url = url,
                            approxSizeMb = source.sizeBytes / (1024L * 1024L),
                        )
                    }
                    outcome.file ?: error("download failed: ${outcome.errorMessage}")
                },
                peerProvider = { peerCapabilities(app) },
                selfProvider = { selfCapabilities(app) },
                peerBaseUrl = { peerId ->
                    if (peerId == "self") null
                    else "http://$peerId:${com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT}"
                },
            )
        }
    }

    private suspend fun resolveModelSource(
        app: Context,
        modelId: String,
    ): ClusterStorageIncubator.ModelSource? {
        val curated = ModelCatalog.find(modelId)
        if (curated != null) {
            return ClusterStorageIncubator.ModelSource(
                modelId = curated.id,
                sizeBytes = curated.approxSizeMb * 1024L * 1024L,
                totalLayers = 32,
                sha256 = "",
                bundled = false,
                downloadUrl = curated.url,
            )
        }
        // Bundled check — only the starter ships in the APK.
        val bundledIds = com.meshlit.core.inference.RunAnywhereCatalogEngine.BUNDLED_IDS
        if (modelId in bundledIds) {
            return ClusterStorageIncubator.ModelSource(
                modelId = modelId,
                sizeBytes = 1100L * 1024L * 1024L,
                totalLayers = 24,
                sha256 = "",
                bundled = true,
                downloadUrl = null,
            )
        }
        return null
    }

    private suspend fun peerCapabilities(app: MeshlitApplication): List<PeerCapabilities> {
        // Build a flat roster from PeerRegistry + /v1/capabilities snapshots.
        //
        // Until this commit, peer capabilities were stubbed with 0 / 0
        // for free disk and RAM, which made the planner filter every
        // peer out and always fall back to whole-model download. The
        // cluster surface here calls /v1/capabilities on each peer
        // (cached 60 s) so the planner sees the real numbers.
        val peers: List<String> = app.peerRegistry.snapshot()
        val cache = app.activePeerHealthCache()
        return peers.map { ip ->
            val baseUrl = "http://$ip:${com.meshlit.core.inference.net.InferenceHttpServer.DEFAULT_PORT}"
            val fetched = fetchPeerCapabilitiesCached(baseUrl)
            if (fetched != null) {
                fetched
            } else {
                // Fall back to a placeholder when the peer doesn't
                // expose /v1/capabilities yet (older build). The
                // planner filters this out on `freeDiskMb == 0L` so
                // the whole-model path still works.
                val ok = cache?.snapshot(ip)?.ok ?: false
                log.warn(
                    "cluster.peer.caps.fallback",
                    "peer capabilities fetch failed; using placeholder",
                    mapOf("peer" to ip),
                )
                PeerCapabilities(
                    peerId = ip,
                    capabilityTier = if (ok) CapabilityTier.MID else CapabilityTier.LITE,
                    freeRamMb = 0L,
                    freeDiskMb = 0L,
                    hostedShardIds = emptySet(),
                    lastSeenMs = System.currentTimeMillis(),
                )
            }
        }
    }

    private suspend fun selfCapabilities(app: MeshlitApplication): PeerCapabilities =
        app.selfCapabilities()

    /** Cache: baseUrl → (capabilities, fetchedAtMs). 60-s TTL — bounded
     *  by the size of the peer roster (typically ≤ 8 entries). Uses
     *  ConcurrentHashMap so the FGS thread and the planner thread can
     *  read/write without contention. */
    private data class CachedCaps(val caps: PeerCapabilities, val fetchedAtMs: Long)

    private val capabilitiesCache = ConcurrentHashMap<String, CachedCaps>()

    private val log = logger("ClusterStorageInstaller")

    private suspend fun fetchPeerCapabilitiesCached(baseUrl: String): PeerCapabilities? {
        val now = System.currentTimeMillis()
        val cached = capabilitiesCache[baseUrl]
        if (cached != null && now - cached.fetchedAtMs < CAPABILITIES_CACHE_TTL_MS) {
            return cached.caps
        }
        val fetched = ShardTransport().fetchCapabilities(baseUrl).getOrNull()
        if (fetched != null) {
            capabilitiesCache[baseUrl] = CachedCaps(fetched, now)
        }
        return fetched
    }

    /** Read what shards the local device already hosts so the planner
     *  can keep them sticky across restarts. The shard layout is
     *  documented in `ShardServer` (`<filesDir>/shards/<modelId>/...`). */
    private fun discoverLocalShards(app: Context): Set<String> {
        val root = File(app.filesDir, "shards")
        if (!root.exists()) return emptySet()
        val result = mutableSetOf<String>()
        root.listFiles()?.forEach { modelDir ->
            if (!modelDir.isDirectory) return@forEach
            val modelId = modelDir.name
            modelDir.listFiles { f -> f.isFile && f.extension == "shard" }?.forEach { shard ->
                val shardId = shard.nameWithoutExtension
                result += "$modelId/$shardId"
            }
        }
        return result
    }
}
