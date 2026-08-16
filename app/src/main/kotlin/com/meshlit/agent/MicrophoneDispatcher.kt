package com.meshlit.agent

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

/**
 * `agent_mic_listen` dispatcher. Captures a short audio clip
 * (default 5 seconds, max 30) via [MediaRecorder] in opus/ogg
 * format and returns base64-encoded bytes.
 *
 * **Why MediaRecorder and not AudioRecord:**
 *  - MediaRecorder produces a self-contained audio file we can
 *    hand back to the LLM (opus/ogg inside ogg container).
 *  - AudioRecord gives raw PCM we'd have to encode ourselves —
 *    more code, larger payload, no fidelity benefit.
 *
 * **No STT:**
 *  - We don't transcribe. The LLM sees raw audio bytes; if it
 *    wants a transcript it can hand them to a hosted STT tool.
 *  - Adding local STT would pull in the sherpa-onnx KWS model
 *    (~150 MB); out of scope for the first cut.
 */
class MicrophoneDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
) {
    suspend fun listen(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Microphone)) {
            return error("permission-denied: microphone")
        }
        val durationMs = (args["durationMs"]?.jsonPrimitive?.contentOrNull
            ?.toLongOrNull() ?: 5_000L).coerceIn(500L, 30_000L)

        return withContext(Dispatchers.IO) {
            val outFile = File.createTempFile("meshlit-mic-", ".ogg", appContext.cacheDir)
            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(appContext)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            runCatching {
                recorder.setAudioSource(MediaRecorder.AudioSource.MIC)
                recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                recorder.setAudioSamplingRate(48_000)
                recorder.setAudioEncodingBitRate(96_000)
                recorder.setOutputFile(outFile.absolutePath)
                recorder.prepare()
                recorder.start()
                delay(durationMs)
                runCatching { recorder.stop() }
                recorder.release()
                val bytes = outFile.readBytes()
                val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
                ok(buildJsonObject {
                    put("mime", JsonPrimitive("audio/ogg; codecs=opus"))
                    put("bytes", JsonPrimitive(bytes.size))
                    put("durationMs", JsonPrimitive(durationMs))
                    put("dataBase64", JsonPrimitive(b64))
                }.toString())
            }.getOrElse { err ->
                runCatching { recorder.release() }
                error("mic-failed: ${err.javaClass.simpleName}: ${err.message}")
            }.also {
                runCatching { outFile.delete() }
            }
        }
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
}