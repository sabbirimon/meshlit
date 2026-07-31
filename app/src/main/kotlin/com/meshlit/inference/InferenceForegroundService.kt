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
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationCenter
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

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        val app = applicationContext as MeshlitApplication
        coordinator = InferenceCoordinator()
        notificationCenter = app.notificationCenter
        startInForeground()
        log.info("fgs.create", "InferenceForegroundService created")
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
                scope.launch {
                    val result = coordinator.infer(
                        InferenceRequest(
                            prompt = prompt,
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

    override fun onDestroy() {
        log.info("fgs.destroy", "InferenceForegroundService destroying")
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

        const val ACTION_LOAD_MODEL = "com.meshlit.inference.LOAD_MODEL"
        const val ACTION_INFER = "com.meshlit.inference.INFER"
        const val ACTION_CANCEL = "com.meshlit.inference.CANCEL"

        const val EXTRA_MODEL_PATH = "model_path"
        const val EXTRA_CONTEXT_SIZE = "context_size"
        const val EXTRA_GPU_LAYERS = "gpu_layers"
        const val EXTRA_CPU_THREADS = "cpu_threads"
        const val EXTRA_GPU_BACKEND = "gpu_backend"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_MAX_TOKENS = "max_tokens"
        const val EXTRA_TEMPERATURE = "temperature"

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