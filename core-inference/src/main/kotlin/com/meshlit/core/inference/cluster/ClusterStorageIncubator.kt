package com.meshlit.core.inference.cluster

import android.content.Context
import com.meshlit.core.common.CapabilityTier
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/**
 * Process-wide orchestrator for cluster-sharded model storage.
 *
 * The incubator owns **storage acquisition only** — not the actual
 * layer-by-layer inference pipeline. Its job is to return a local,
 * contiguous model file that the existing `InferenceCoordinator`
 * can load. The steps are:
 *
 *  1. Resolve the model id into [ModelSource] metadata.
 *  2. If bundled, delegate to the injected bundled resolver.
 *  3. Refresh peer capabilities.
 *  4. Ask [ClusterShardPlanner] for SingleShard / MultiShard /
 *     FallbackToWholeModel.
 *  5. For a cluster plan, [ShardAssembler] fetches and verifies
 *     every shard. For fallback, invoke the existing app-side URL
 *     downloader through [wholeModelDownloader].
 *
 * Why all source/download functions are injected: `:core-inference`
 * cannot depend on `:app`'s `ModelCatalog` without creating a module
 * cycle. Injection keeps the dependency direction correct while
 * still reusing the app's existing `downloadFromUrl` implementation.
 */
class ClusterStorageIncubator(
    context: Context,
    private val modelResolver: suspend (String) -> ModelSource?,
    private val bundledResolver: suspend (String) -> File?,
    private val wholeModelDownloader: suspend (ModelSource) -> File,
    private val peerProvider: suspend () -> List<PeerCapabilities>,
    private val selfProvider: suspend () -> PeerCapabilities,
    private val peerBaseUrl: (String) -> String?,
    private val planner: ClusterShardPlanner = ClusterShardPlanner(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) {

    private val filesDir: File = context.filesDir
    private val assembler: ShardAssembler = ShardAssembler(filesDir)

    private val log = logger("ClusterStorageIncubator")

    /** Directory under which reassembled models are cached. */
    fun reassembledDir(): File = File(filesDir, "reassembled")

    /** Acquire a local contiguous model file. Safe to call repeatedly;
     *  an already-verified reassembled file is returned immediately. */
    suspend fun acquireModel(
        modelId: String,
        onProgress: (Long) -> Unit = {},
    ): File = withContext(dispatcher) {
        val source = modelResolver(modelId)
            ?: error("unknown model id: $modelId")

        // Bundled path — one dense starter model ships in the APK.
        // Do not route it through the cluster or network.
        if (source.bundled) {
            return@withContext bundledResolver(modelId)
                ?: error("bundled model $modelId is not installed")
        }

        // Reassembled cache hit. The app-side installer / cleanup UI
        // owns lifecycle; we only check for existence here.
        val cached = File(reassembledDir(), "$modelId.gguf")
        if (cached.exists() && cached.length() == source.sizeBytes) {
            return@withContext cached
        }

        val self = selfProvider()
        val peers = peerProvider()
        val plan = planner.plan(
            modelId = modelId,
            modelSizeBytes = source.sizeBytes,
            totalLayers = source.totalLayers,
            self = self,
            peers = peers,
        )
        when (plan) {
            is ClusterShardPlanner.Plan.FallbackToWholeModel -> {
                log.warn(
                    "incubator.fallback",
                    "falling back to whole-model download",
                    mapOf("modelId" to modelId, "reason" to plan.reason),
                )
                wholeModelDownloader(source)
            }
            is ClusterShardPlanner.Plan.SingleShard -> acquireFromAssignments(
                source = source,
                assignments = plan.assignments,
                onProgress = onProgress,
            )
            is ClusterShardPlanner.Plan.MultiShard -> acquireFromAssignments(
                source = source,
                assignments = plan.assignments,
                onProgress = onProgress,
            )
        }
    }

    private suspend fun acquireFromAssignments(
        source: ModelSource,
        assignments: List<ClusterShardPlanner.ShardAssignment>,
        onProgress: (Long) -> Unit,
    ): File {
        // If the plan says one remote peer hosts the *whole* model,
        // treat it as a single shard. The server exposes it through
        // the same endpoint so the assembler path stays uniform.
        return assembler.reassemble(
            modelId = source.modelId,
            assignments = assignments,
            expectedSha256 = source.sha256,
            peerBaseUrl = peerBaseUrl,
            onProgress = onProgress,
        )
    }

    /** Metadata required to plan + verify one model. */
    data class ModelSource(
        val modelId: String,
        val sizeBytes: Long,
        val totalLayers: Int,
        val sha256: String,
        val bundled: Boolean,
        val downloadUrl: String?,
        val preferredTier: CapabilityTier = CapabilityTier.MID,
    )

    companion object {
        private val INSTANCE = AtomicReference<ClusterStorageIncubator?>(null)

        /** Install process-wide. Subsequent calls are no-ops. */
        fun install(factory: () -> ClusterStorageIncubator) {
            if (INSTANCE.get() == null) {
                INSTANCE.compareAndSet(null, factory())
            }
        }

        /** Process-wide instance. Throws on startup-order bugs. */
        fun get(): ClusterStorageIncubator = INSTANCE.get()
            ?: error("ClusterStorageIncubator not installed — call install() from Application.onCreate")

        /** Test-only reset hook. */
        internal fun resetForTests() {
            INSTANCE.set(null)
        }
    }
}
