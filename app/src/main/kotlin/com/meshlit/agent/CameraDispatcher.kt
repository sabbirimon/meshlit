package com.meshlit.agent

import android.content.Context
import android.util.Base64
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import com.google.common.util.concurrent.ListenableFuture
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * `agent_camera_capture` dispatcher. Captures a single JPEG via
 * CameraX's [ImageCapture] bound to a synthetic [LifecycleOwner],
 * returns base64-encoded bytes so the LLM can see what the
 * camera saw.
 *
 * **Why a synthetic LifecycleOwner:**
 *  - The agent loop runs through the foreground service, not an
 *    Activity.
 *  - We need CameraX to tear the camera down the instant the
 *    capture resolves so we don't hold the lens open between
 *    requests.
 *
 * **Why CAMERA permission is required:**
 *  CameraX uses the platform `CameraDevice` under the hood. The
 *  Code Scanner path avoids the permission because Play Services'
 *  bundled UI is opaque from our process — here we bind CameraX
 *  ourselves, so the runtime grant is mandatory.
 */
class CameraDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
) {
    private val cameraExecutor: Executor by lazy {
        Executors.newSingleThreadExecutor()
    }

    suspend fun capture(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Camera)) {
            return error("permission-denied: camera")
        }
        val lensFacing = args["lensFacing"]?.jsonPrimitive?.contentOrNull ?: "back"
        val maxWidthPx = args["maxWidthPx"]?.jsonPrimitive?.contentOrNull
            ?.toIntOrNull() ?: 1280
        val flashModeStr = args["flashMode"]?.jsonPrimitive?.contentOrNull ?: "auto"

        val selector = when (lensFacing) {
            "front" -> CameraSelector.DEFAULT_FRONT_CAMERA
            else -> CameraSelector.DEFAULT_BACK_CAMERA
        }
        val flashMode = when (flashModeStr) {
            "on" -> ImageCapture.FLASH_MODE_ON
            "off" -> ImageCapture.FLASH_MODE_OFF
            else -> ImageCapture.FLASH_MODE_AUTO
        }

        return withContext(Dispatchers.IO) {
            runCatching {
                val file = File.createTempFile(
                    "meshlit-capture-",
                    ".jpg",
                    appContext.cacheDir,
                )
                try {
                    val bytes = captureOnce(selector, flashMode, file)
                    val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                    ok(buildJsonObject {
                        put("mime", JsonPrimitive("image/jpeg"))
                        put("bytes", JsonPrimitive(bytes.size))
                        put("dataBase64", JsonPrimitive(b64))
                        put("width", JsonPrimitive(maxWidthPx))
                        put("lensFacing", JsonPrimitive(lensFacing))
                    }.toString())
                } finally {
                    runCatching { file.delete() }
                }
            }.getOrElse { err ->
                error("camera-failed: ${err.javaClass.simpleName}: ${err.message}")
            }
        }
    }

    /**
     * Bind CameraX, take one JPEG, return its bytes, unbind
     * everything. The lifecycle walks CREATED → STARTED → RESUMED
     * while the camera is active, then CREATED → DESTROYED on
     * the way out.
     */
    private suspend fun captureOnce(
        selector: CameraSelector,
        flashMode: Int,
        outFile: File,
    ): ByteArray {
        val lifecycle = SyntheticLifecycle()
        try {
            val provider = ProcessCameraProvider.getInstance(appContext).await()
            val capture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                .setFlashMode(flashMode)
                .build()
            lifecycle.moveTo(Lifecycle.State.STARTED)
            provider.unbindAll()
            provider.bindToLifecycle(lifecycle, selector, Preview.Builder().build(), capture)
            lifecycle.moveTo(Lifecycle.State.RESUMED)
            return takePicture(capture, outFile)
        } finally {
            lifecycle.moveTo(Lifecycle.State.DESTROYED)
        }
    }

    private suspend fun takePicture(capture: ImageCapture, outFile: File): ByteArray =
        suspendCancellableCoroutine { cont ->
            val opts = ImageCapture.OutputFileOptions.Builder(outFile).build()
            capture.takePicture(
                opts,
                ContextCompat.getMainExecutor(appContext),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val bytes = runCatching { outFile.readBytes() }.getOrNull()
                        if (bytes != null && cont.isActive) cont.resume(bytes)
                        else if (cont.isActive) cont.resumeWithException(
                            IllegalStateException("captured file unreadable: ${outFile.absolutePath}"),
                        )
                    }

                    override fun onError(exc: ImageCaptureException) {
                        if (cont.isActive) cont.resumeWithException(exc)
                    }
                },
            )
        }

    private fun ok(body: String) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = true,
        body = body,
    )

    private fun error(message: String) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = false,
        body = message,
    )

    /**
     * Minimal LifecycleOwner + LifecycleRegistry. CameraX only
     * needs the state transitions; we never post events because
     * no observers are attached.
     */
    private class SyntheticLifecycle : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry
        fun moveTo(state: Lifecycle.State) {
            registry.currentState = state
        }
    }
}

/** `.await()` for ListenableFuture — common coroutine bridge. */
private suspend fun <T> ListenableFuture<T>.await(): T =
    suspendCancellableCoroutine { cont ->
        addListener({
            try {
                cont.resume(get())
            } catch (e: Throwable) {
                cont.resumeWithException(e)
            }
        }, Runnable::run)
        cont.invokeOnCancellation { cancel(false) }
    }