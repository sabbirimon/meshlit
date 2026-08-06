package com.meshlit.agent

import android.content.Context
import android.telephony.SmsManager
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * `agent_sms_send` dispatcher. High-risk: per-message confirmation
 * is enforced upstream by the agent-loop confirmation flow; this
 * dispatcher still validates the recipient allowlist defensively
 * so a programming error can't bypass it.
 *
 * **Wire shape:** `SmsManager.getDefault().sendTextMessage(...)`
 * — the platform's plain-text SMS API. Multipart messages are
 * split on our side via `SmsManager.divideMessage` if the body
 * exceeds 160 chars (GSM-7) or 70 (UCS-2).
 *
 * **Why per-recipient allowlist instead of one toggle:**
 * SMS is one-way to a real person. A user flipping the master
 * "send SMS" toggle must still approve every recipient phone
 * number. There is no wildcard allowlist — recipients have to be
 * added one at a time. This is the same posture Google Messages
 * uses for RCS auto-replies.
 */
class SmsDispatcher(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
) {
    suspend fun send(args: JsonObject): McpEvent.ToolResult {
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        val body = args["body"]?.jsonPrimitive?.contentOrNull.orEmpty()

        if (number.isBlank()) return error("missing 'number' arg")
        if (body.isBlank()) return error("missing 'body' arg")
        if (body.length > 1600) return error("body too long (max 1600)")

        // The agent loop should have already gated this via the
        // confirmation dialog. We re-check defensively so a UI
        // bypass can't slip an SMS through.
        if (!registry.isAllowed(AgentCapability.Sms, target = number)) {
            return error("recipient '$number' not on SMS allowlist")
        }

        return withContext(Dispatchers.IO) {
            val sm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                appContext.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }
            runCatching {
                val parts = sm.divideMessage(body)
                if (parts.size == 1) {
                    sm.sendTextMessage(number, null, body, null, null)
                } else {
                    sm.sendMultipartTextMessage(number, null, parts, null, null)
                }
                ok(buildJsonObject {
                    put("status", JsonPrimitive("sent"))
                    put("number", JsonPrimitive(number))
                    put("bytes", JsonPrimitive(body.toByteArray(Charsets.UTF_8).size))
                    put("parts", JsonPrimitive(parts.size))
                }.toString())
            }.getOrElse { err ->
                error("sms-failed: ${err.javaClass.simpleName}: ${err.message}")
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