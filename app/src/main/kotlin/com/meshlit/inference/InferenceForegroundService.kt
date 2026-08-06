package com.meshlit.inference

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.meshlit.MainActivity
import com.meshlit.MeshlitApplication
import com.meshlit.R
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.logger
import com.meshlit.core.inference.BackendHints
import com.meshlit.core.inference.FinishReason
import com.meshlit.core.inference.GpuBackend
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.inference.InferenceEvent
import com.meshlit.core.inference.InferenceRequest
import com.meshlit.core.inference.InferenceResult
import com.meshlit.core.inference.ModelInfo
import com.meshlit.core.inference.cluster.ShardServer
import com.meshlit.core.inference.net.InferenceHttpServer
import com.meshlit.core.inference.net.RawTcpActivationServer
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationCenter
import com.meshlit.inference.ForwardingProxy
import com.meshlit.inference.MiniRouter
import com.meshlit.inference.PeerHealthCache
import com.meshlit.inference.PeerRegistry
import com.meshlit.inference.RemoteInferenceClientFactory
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * Foreground service that hosts the [InferenceCoordinator]. The
 * coordinator itself is a JVM object; this service is *just* the
 * Android lifecycle plumbing:
 *
 *  - start as FGS-data-sync (the closest fit for "long-running job queue")
 *  - post a persistent notification while running
 *  - keep the coordinator alive even when the app is backgrounded
 *  - handle Android 15+ onTimeout() (6h cap on dataSync) by either
 *    re-posting as a different type or gracefully shutting down
 *  - bind to a small IPC surface so the UI can dispatch prompts
 *
 * Why a service and not a WorkManager job: FGS gives us the
 * guaranteed unbounded lifetime we need for live inference. WorkManager
 * is for deferrable unit-of-work — wrong tool.
 *
 * Lifecycle:
 *  - startService(InferenceForegroundService) → service onCreate fires
 *  - coordinator.loadModel(...) from a caller → state transitions
 *  - coordinator.infer(...) from a caller → state → Generating → Ready
 *  - stopService(...) → onDestroy → coordinator.unloadModel, scope cancel
 */
class InferenceForegroundService : Service() {

    private val log = logger("InferenceForegroundService")

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var coordinator: InferenceCoordinator
    private lateinit var notificationCenter: NotificationCenter

    /**
     * Router stack wired in `onCreate`, torn down in `onDestroy`.
     *  - [peerRegistry]      : DataStore-backed list of peer IPs.
     *  - [clientFactory]     : shared HttpClient (one engine per process).
     *  - [healthCache]       : per-peer health snapshot, refreshed every 30s.
     *  - [miniRouter]        : capability-aware decision (LOCAL vs FORWARD).
     *  - [forwardingProxy]   : streams peer SSE back through the server's outgoing channel.
     *  - [httpServer]        : the embedded Ktor server itself.
     */
    private var peerRegistry: PeerRegistry? = null
    private var clientFactory: RemoteInferenceClientFactory? = null
    private var healthCache: PeerHealthCache? = null
    private var miniRouter: MiniRouter? = null
    private var forwardingProxy: ForwardingProxy? = null
    private var httpServer: InferenceHttpServer? = null
    private var metricsRegistry: MetricsRegistry? = null
    private var activationServer: RawTcpActivationServer? = null
    private var shardServer: ShardServer? = null

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as MeshlitApplication
        // Use the app-scoped coordinator so every screen (Agent, Jobs,
        // Terminal) talks to the same engine instance. Previously this
        // service created a private coordinator which meant AgentSession
        // never saw the loaded model — it would always surface the
        // "No model loaded" error.
        coordinator = app.inferenceCoordinator
        notificationCenter = app.notificationCenter
        startInForeground()
        log.info("fgs.create", "InferenceForegroundService created")

        // Auto-load the bundled model if extraction is finished by the
        // time the FGS starts. Extraction is async; if the file isn't
        // ready yet, the Jobs screen's Send button will retry (or
        // Models → Re-extract does it manually). The bundled-model
        // path is the default; a custom path the user has saved in
        // Settings wins when present.
        scope.launch { autoLoadDefaultModel(app) }

        // Build router stack and start the embedded HTTP/SSE server.
        try {
            val reg = app.peerRegistry
            val factory = RemoteInferenceClientFactory()
            val cache = PeerHealthCache(factory)
            val router = MiniRouter(coordinator, reg, cache)
            val proxy = ForwardingProxy(factory)
            val enricher = HealthEnricherImpl(
                tierProvider = { app.capabilityTier },
                engineTagProvider = { coordinator.engineTag },
                metrics = app.metricsRegistry,
                loadedShardsProvider = { coordinator.loadedShards.value },
            )
            val jobLifecycle = AppJobLifecycle(app.metricsRegistry)
            // Wire the cluster-shard surface on the same port. ShardServer
            // answers /v1/capabilities, /v1/shards/{modelId}/{shardId},
            // /v1/manifest/{modelId}; peer's InferenceHttpServer.serve()
            // dispatches shard traffic first so it never blocks on the
            // coordinator mutex.
            val shard = ShardServer(
                filesDir = filesDir,
                selfCapabilities = { app.selfCapabilities() },
            )
            val server = InferenceHttpServer(
                coordinator = coordinator,
                router = router,
                forwarder = proxy,
                enricher = enricher,
                lifecycle = jobLifecycle,
                port = InferenceHttpServer.DEFAULT_PORT,
                shardServer = shard,
                // Port-layer firewall: evaluated before the phase-3
                // CIDR / node / tier gate so an unauthorized peer
                // can't even probe `/v1/capabilities` until the
                // user has explicitly opened the port they're
                // trying to hit.
                meshFirewall = app.meshlitFirewall,
            )
            peerRegistry = reg
            clientFactory = factory
            healthCache = cache
            app.setActivePeerHealthCache(cache)
            miniRouter = router
            forwardingProxy = proxy
            metricsRegistry = app.metricsRegistry
            shardServer = shard
            httpServer = server
            // Raw-TCP activation transport server. Inbound channels
            // are wired into a list — Phase 2.3 will hand each one
            // to the ShardForwarder that owns the upstream peer. For
            // now we just log the connection so we can verify the
            // wire format round-trips.
            val activation = RawTcpActivationServer(ACTIVATION_PORT) { ch ->
                log.info(
                    "fgs.activation.channel",
                    "activation channel opened",
                    mapOf("port" to ACTIVATION_PORT),
                )
                // Channels are short-lived (one per peer handshake);
                // we don't keep a strong ref because we don't have a
                // consumer yet.
            }
            activation.start()
            activationServer = activation
            // NanoHTTPD's start() blocks on the calling thread until
            // stop() is called. We launch it on the FGS scope so it
            // runs on its own daemon thread and shuts down cleanly
            // when the service is destroyed.
            scope.launch { server.start() }
            scope.launch { cache.refreshLoop(this, reg) }
            log.info(
                "fgs.router.start",
                "router stack ready",
                mapOf("port" to InferenceHttpServer.DEFAULT_PORT),
            )
        } catch (t: Throwable) {
            log.warn("fgs.router.fail", "router stack init failed", mapOf("err" to (t.message ?: "")))
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_LOAD_MODEL -> {
                val path = intent.getStringExtra(EXTRA_MODEL_PATH) ?: return START_NOT_STICKY
                val hints = readHints(intent)
                scope.launch {
                    val result = coordinator.loadModel(
                        modelPath = path,
                        contextSize = intent.getIntExtra(EXTRA_CONTEXT_SIZE, 4096),
                        gpuLayers = intent.getIntExtra(EXTRA_GPU_LAYERS, 0),
                        hints = hints,
                    )
                    if (result is MeshlitResult.Failure) {
                        log.warn("fgs.load_fail", "load failed: ${result.error.tag}")
                    }
                }
            }
            ACTION_INFER -> {
                val prompt = intent.getStringExtra(EXTRA_PROMPT) ?: return START_NOT_STICKY
                // Build the identity-tagged prompt. We seed the
                // model with the host application name + loaded
                // model + origin + version so when the user asks
                // "what's your name / identify yourself" the
                // model answers with the right tags.
                val fgsApp = applicationContext as MeshlitApplication
                val seededPrompt = buildIdentitySeededPrompt(
                    app = fgsApp,
                    coordinator = coordinator,
                    userPrompt = prompt,
                )
                scope.launch {
                    val result = coordinator.infer(
                        InferenceRequest(
                            prompt = seededPrompt,
                            maxTokens = intent.getIntExtra(EXTRA_MAX_TOKENS, 256),
                            temperature = intent.getFloatExtra(EXTRA_TEMPERATURE, 0.7f),
                            onToken = { _ -> /* events surface via coordinator.events */ },
                        ),
                    )
                    if (result is MeshlitResult.Success) {
                        log.info(
                            "fgs.infer.ok",
                            "inference ok",
                            mapOf(
                                "tokens" to result.value.generatedTokens,
                                "durationMs" to result.value.totalDurationMs,
                                "reason" to result.value.finishReason.tag,
                            ),
                        )
                    }
                }
            }
            ACTION_CANCEL -> coordinator.cancel()
            ACTION_ACQUIRE_SHARDED -> {
                val modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: return START_NOT_STICKY
                val contextSize = intent.getIntExtra(EXTRA_CONTEXT_SIZE, 4096)
                val gpuLayers = intent.getIntExtra(EXTRA_GPU_LAYERS, 0)
                scope.launch {
                    val result = coordinator.loadShardedModel(
                        modelId = modelId,
                        contextSize = contextSize,
                        gpuLayers = gpuLayers,
                    )
                    if (result is MeshlitResult.Failure) {
                        log.warn(
                            "fgs.acquire_sharded.fail",
                            "sharded acquisition failed: ${result.error.tag}",
                            mapOf("modelId" to modelId),
                        )
                    }
                }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onTimeout(startId: Int, foregroundServiceType: Int) {
        // Android 15+ caps dataSync FGS at 6h initially. The framework
        // calls this when the cap is hit. We log it and let the system
        // shut us down — the user can re-launch from the UI.
        log.warn("fgs.timeout", "foreground service timeout", mapOf(
            "startId" to startId,
            "type" to foregroundServiceType,
        ))
    }

    /**
     * Pick a model to load on FGS startup. Resolution order:
     *  1. User-configured custom path (Settings → Models).
     *  2. Bundled GGUF that was extracted on app launch.
     *  3. Nothing — wait for an explicit Load command.
     *
     * If the bundled file isn't ready yet (extraction still running
     * on the app scope), we poll for up to ~30s. After that we give
     * up — the user can hit Re-extract from Models or the Jobs Send
     * button will re-evaluate next time.
     */
    private suspend fun autoLoadDefaultModel(app: MeshlitApplication) {
        val settingsRepo: SettingsRepository = app.settingsRepository
        val customPath = settingsRepo.customModelPathSync()
        if (customPath.isNullOrBlank()) {
            // Bundled-model path. Wait up to 30s for extraction to land.
            val deadline = System.currentTimeMillis() + 30_000L
            while (app.bundledModelPath() == null && System.currentTimeMillis() < deadline) {
                kotlinx.coroutines.delay(500L)
            }
        }
        val customPathNow = settingsRepo.customModelPathSync()
        val bundledFile = app.bundledModelPath()
        val target = when {
            !customPathNow.isNullOrBlank() -> {
                val f = java.io.File(customPathNow)
                if (f.exists() && f.length() > 0L) f else null
            }
            bundledFile != null && bundledFile.exists() && bundledFile.length() > 0L -> bundledFile
            else -> null
        }
        if (target == null) {
            log.info("fgs.auto_load.skip", "no model to auto-load")
            return
        }
        // Don't double-load if the coordinator is already in Ready state
        // (e.g. the user bound and unbound, then we come back up).
        if (coordinator.state.value is com.meshlit.core.inference.CoordinatorState.Ready) {
            log.info("fgs.auto_load.skip", "coordinator already Ready")
            return
        }
        log.info(
            "fgs.auto_load.start",
            "auto-loading model on FGS startup",
            mapOf("path" to target.absolutePath, "source" to if (customPathNow.isNullOrBlank()) "bundled" else "custom"),
        )
        val result = coordinator.loadModel(
            modelPath = target.absolutePath,
            contextSize = 4096,
            gpuLayers = 0,
            hints = com.meshlit.core.inference.BackendHints.CpuOnly,
        )
        if (result is com.meshlit.core.common.MeshlitResult.Success) {
            log.info("fgs.auto_load.ok", "auto-load ok")
        } else if (result is com.meshlit.core.common.MeshlitResult.Failure) {
            log.warn("fgs.auto_load.fail", "auto-load failed: ${result.error.tag}")
        }
    }

    override fun onDestroy() {
        log.info("fgs.destroy", "InferenceForegroundService destroying")
        // Tear down the router stack in reverse construction order:
        // server first (so no new requests land), then the factory
        // (closes the shared HttpClient), then drop references.
        runCatching { httpServer?.stop() }
        runCatching { activationServer?.close() }
        runCatching { clientFactory?.close() }
        httpServer = null
        activationServer = null
        forwardingProxy = null
        miniRouter = null
        healthCache = null
        clientFactory = null
        peerRegistry = null

        coordinator.shutdown()
        scope.launch { coordinator.unloadModel() }
        scope.cancel()
        super.onDestroy()
    }

    private fun startInForeground() {
        val tapIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification: Notification = NotificationCompat.Builder(this, NotificationCategory.FOREGROUND_SERVICE.channelId)
            .setSmallIcon(R.drawable.ic_meshlit_notification)
            .setContentTitle(getString(R.string.fgs_inference_title))
            .setContentText(getString(R.string.fgs_inference_body))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun readHints(intent: Intent): BackendHints {
        val gpuBackend = intent.getStringExtra(EXTRA_GPU_BACKEND)
            ?.let { runCatching { GpuBackend.valueOf(it) }.getOrNull() }
            ?: GpuBackend.NONE
        return BackendHints(
            cpuThreads = intent.getIntExtra(EXTRA_CPU_THREADS, 0),
            gpuLayers = intent.getIntExtra(EXTRA_GPU_LAYERS, 0),
            gpuBackend = gpuBackend,
        )
    }

    /**
     * Local binder for in-process callers. The Activity binds to
     * this and dispatches prompts through it. Cross-process binding
     * is intentionally not supported in Phase 1 — single-app only.
     */
    inner class LocalBinder : android.os.Binder() {
        fun service(): InferenceForegroundService = this@InferenceForegroundService
        fun coordinator(): InferenceCoordinator = this@InferenceForegroundService.coordinator
        fun state(): StateFlow<com.meshlit.core.inference.CoordinatorState> = coordinator.state
        fun events(): SharedFlow<InferenceEvent> = coordinator.events
    }

    companion object {
        const val NOTIFICATION_ID = 1001

        /** Default port for the raw-TCP activation transport. Picked
         *  away from the HTTP port (8080) so they coexist on one phone. */
        const val ACTIVATION_PORT = 9090

        const val ACTION_LOAD_MODEL = "com.meshlit.inference.LOAD_MODEL"
        const val ACTION_INFER = "com.meshlit.inference.INFER"
        const val ACTION_CANCEL = "com.meshlit.inference.CANCEL"
        /** Acquire a model via the cluster-shard path: the planner picks
         *  peers, the transport pulls shards, and the coordinator loads
         *  the reassembled GGUF. Surfaces failures through [coordinator]
         *  state. */
        const val ACTION_ACQUIRE_SHARDED = "com.meshlit.inference.ACQUIRE_SHARDED"

        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_CONTEXT_SIZE = "context_size"
        const val EXTRA_GPU_LAYERS = "gpu_layers"
        const val EXTRA_CPU_THREADS = "cpu_threads"
        const val EXTRA_GPU_BACKEND = "gpu_backend"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_MAX_TOKENS = "max_tokens"
        const val EXTRA_TEMPERATURE = "temperature"
        const val EXTRA_MODEL_ID = "model_id"

        /** Build an intent that triggers a cluster-shard acquisition.
         *  Use this from the UI (ModelsScreen "Acquire via cluster"
         *  button) so the service handles the long-running work off
         *  the main thread. */
        fun buildAcquireShardedIntent(
            context: Context,
            modelId: String,
            contextSize: Int = 4096,
            gpuLayers: Int = 0,
        ): Intent = Intent(context, InferenceForegroundService::class.java).apply {
            action = ACTION_ACQUIRE_SHARDED
            putExtra(EXTRA_MODEL_ID, modelId)
            putExtra(EXTRA_CONTEXT_SIZE, contextSize)
            putExtra(EXTRA_GPU_LAYERS, gpuLayers)
        }

        /** Convenience helper to start the service from a caller. */
        fun startForInference(context: Context) {
            val intent = Intent(context, InferenceForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Convenience helper to stop the service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, InferenceForegroundService::class.java))
        }
    }
}

/** Convenience extension to build a load-model intent without spelling
 *  out `EXTRA_*` constants on every call site. */
fun buildLoadModelIntent(
    context: Context,
    modelPath: String,
    contextSize: Int = 4096,
    gpuLayers: Int = 0,
    cpuThreads: Int = 0,
    gpuBackend: GpuBackend? = null,
): Intent = Intent(context, InferenceForegroundService::class.java).apply {
    action = InferenceForegroundService.ACTION_LOAD_MODEL
    putExtra(InferenceForegroundService.EXTRA_MODEL_PATH, modelPath)
    putExtra(InferenceForegroundService.EXTRA_CONTEXT_SIZE, contextSize)
    putExtra(InferenceForegroundService.EXTRA_GPU_LAYERS, gpuLayers)
    putExtra(InferenceForegroundService.EXTRA_CPU_THREADS, cpuThreads)
    gpuBackend?.let { putExtra(InferenceForegroundService.EXTRA_GPU_BACKEND, it.name) }
}

fun buildInferIntent(
    context: Context,
    prompt: String,
    maxTokens: Int = 256,
    temperature: Float = 0.7f,
): Intent = Intent(context, InferenceForegroundService::class.java).apply {
    action = InferenceForegroundService.ACTION_INFER
    putExtra(InferenceForegroundService.EXTRA_PROMPT, prompt)
    putExtra(InferenceForegroundService.EXTRA_MAX_TOKENS, maxTokens)
    putExtra(InferenceForegroundService.EXTRA_TEMPERATURE, temperature)
}

fun buildCancelIntent(context: Context): Intent =
    Intent(context, InferenceForegroundService::class.java).apply {
        action = InferenceForegroundService.ACTION_CANCEL
    }

/**
 * Wrap [userPrompt] with the identity prefix so the model can
 * identify itself with the right tags when the user asks "what's
 * your name / identify yourself".
 *
 * Layout:
 *
 *     <identity-prefix>
 *
 *     <user-prompt>
 *
 * Where `<identity-prefix>` is the seed string from
 * [com.meshlit.ui.components.identitySystemPrompt]. We resolve
 * the identity on the FGS side because the FGS owns the
 * coordinator and the engine tag — keeping the call site here
 * means every consumer of [buildInferIntent] (Jobs screen, Voice
 * intent path, agent) gets the same identity behaviour for free.
 *
 * If anything goes wrong while resolving the identity we fall
 * back to the raw user prompt — never block inference on a
 * presentation-layer detail.
 */
fun buildIdentitySeededPrompt(
    app: com.meshlit.MeshlitApplication,
    coordinator: com.meshlit.core.inference.InferenceCoordinator,
    userPrompt: String,
): String {
    return runCatching {
        val resolver = com.meshlit.ui.components.IdentityResolver(app)
        val identity = resolver.resolve(
            dispatchMode = com.meshlit.inference.InferenceDispatchMode.LOCAL,
            peerLabel = "",
            state = coordinator,
        )
        val prefix = com.meshlit.ui.components.identitySystemPrompt(identity)
        "$prefix\n\n$userPrompt"
    }.getOrDefault(userPrompt)
}