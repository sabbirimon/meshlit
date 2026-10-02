package com.meshlit.agent

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import com.meshlit.core.cloudmcp.McpEvent
import com.meshlit.core.cloudmcp.agent.AgentCapability
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRegistry
import com.meshlit.core.cloudmcp.agent.AgentCapabilityRouter
import com.meshlit.core.cloudmcp.agent.AgentCapabilityTools
import com.meshlit.core.common.HookTrigger
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Runtime dispatcher for the on-device agent capabilities.
 *
 * One suspending function per `agent_*` tool. Each function:
 *  1. Checks the [AgentCapabilityRegistry] — refuses if the
 *     capability is disabled or the runtime permission isn't
 *     granted.
 *  2. Confirms any per-target allowlist (SMS recipient, etc).
 *  3. Calls the platform API on `Dispatchers.IO` (or `Main` for
 *     dialer intents).
 *  4. Returns a [McpEvent.ToolResult] with a `body` the LLM can
 *     read.
 *
 * **Why suspend + IO:**
 * The agent loop awaits these as if they were any other MCP call.
 * We route off the main thread because CameraX, MediaRecorder,
 * and SmsManager are blocking APIs.
 *
 * **Why one class for the cheap dispatchers:**
 * The trivial ones (data_state, call_dial) live in here. The
 * heavier ones (camera, mic, location, SMS, storage) live in
 * separate files in this package — each is a self-contained
 * `class FooDispatcher(context, registry) { suspend fun foo(...): ToolResult }`.
 */
class AgentCapabilityDispatchers(
    private val appContext: Context,
    private val registry: AgentCapabilityRegistry,
    private val settings: SettingsRepository,
    private val termuxBridge: com.meshlit.network.termux.TermuxBridge =
        com.meshlit.network.termux.AndroidTermuxBridge(appContext),
    // Production wiring in `CoreModule.kt` injects a real
    // `TermuxAuditSink` (writes JSONL to filesDir/agent/) and a
    // `DenyByDefaultApprovalSink` (denies any non-allowlisted
    // command). The fallback default here is identical to the
    // production behavior; we keep it only so this class can be
    // instantiated in a unit test that doesn't bind Koin first.
    private val termuxApprovals: ApprovalSink = DenyByDefaultApprovalSink(),
    private val termuxAudit: AuditSink = TermuxAuditSink(appContext),
    // Phase 8 — optional hooks engine. `null` is the default so the
    // class can be instantiated in unit tests that don't bind Koin.
    // When non-null we fire `PreToolCall` before each delegation
    // and `PostToolCall` after.
    private val hookEngine: com.meshlit.agent.hooks.HookEngine? = null,
) : AgentCapabilityRouter.DispatcherFacade {
    val camera = CameraDispatcher(appContext, registry)
    val microphone = MicrophoneDispatcher(appContext, registry)
    val location = LocationDispatcher(appContext, registry)
    val sms = SmsDispatcher(appContext, registry)
    val storage = StorageDispatcher(appContext, registry, settings)
    val termux = TermuxRunCommandDispatcher(
        appContext = appContext,
        registry = registry,
        bridge = termuxBridge,
        approvals = termuxApprovals,
        auditSink = termuxAudit,
    )

    override suspend fun cameraCapture(args: JsonObject) =
        wrap("agent_camera_capture", args) { camera.capture(it) }
    override suspend fun micListen(args: JsonObject) =
        wrap("agent_mic_listen", args) { microphone.listen(it) }
    override suspend fun locationGet(args: JsonObject) =
        wrap("agent_location_get", args) { location.get(it) }
    override suspend fun dataState(args: JsonObject): McpEvent.ToolResult =
        wrap("agent_data_state", args) { dataStateImpl(it) }
    override suspend fun callDial(args: JsonObject): McpEvent.ToolResult =
        wrap("agent_call_dial", args) { callDialImpl(it) }
    override suspend fun smsSend(args: JsonObject) =
        wrap("agent_sms_send", args) { sms.send(it) }
    override suspend fun storageList(args: JsonObject) =
        wrap("agent_storage_list", args) { storage.list(it) }
    override suspend fun storageRead(args: JsonObject) =
        wrap("agent_storage_read", args) { storage.read(it) }
    override suspend fun storageWrite(args: JsonObject) =
        wrap("agent_storage_write", args) { storage.write(it) }
    override suspend fun termuxRunCommand(args: JsonObject) =
        wrap("agent_termux_run_command", args) { termux.runCommand(it) }

    /**
     * Fire `PreToolCall` (synchronous, awaited with the per-hook
     * timeout) and `PostToolCall` (fire-and-forget) around each
     * dispatch. The pre-hook is `await`ed but its result is ignored
     * for v1 — deny semantics land in a follow-up. Errors thrown by
     * the wrapped call are converted to a [HookTrigger.OnError] fire
     * and re-thrown so the existing exception path keeps working.
     */
    private suspend fun wrap(
        toolName: String,
        args: JsonObject,
        block: suspend (JsonObject) -> McpEvent.ToolResult,
    ): McpEvent.ToolResult {
        val engine = hookEngine ?: return block(args)
        val vars = mapOf(
            "tool_name" to toolName,
            "call_id" to "",
            "provider_id" to AgentCapabilityTools.PROVIDER_ID,
            "args_preview" to args.toString().take(1024),
        )
        // Pre-hook: blocking but its result is logged-only for now.
        engine.firePre(HookTrigger.PreToolCall, vars)
        val result = try {
            block(args)
        } catch (t: Throwable) {
            engine.fire(HookTrigger.OnError, vars + ("error" to (t.message ?: t.javaClass.simpleName)))
            throw t
        }
        engine.fire(
            HookTrigger.PostToolCall,
            vars + ("ok" to result.ok.toString()),
        )
        return result
    }

    /**
     * `agent_data_state` — read-only, no permission, no target.
     */
    private suspend fun dataStateImpl(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.DataState)) {
            return denied(AgentCapability.DataState)
        }
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.activeNetwork
        val caps = active?.let { cm.getNetworkCapabilities(it) }
        val wifi = cm.getNetworkCapabilities(active)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
        val cellular = caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        val ethernet = caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true
        val vpn = caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metered = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == false
        val body = buildJsonObject {
            put("wifi", JsonPrimitive(wifi))
            put("cellular", JsonPrimitive(cellular))
            put("ethernet", JsonPrimitive(ethernet))
            put("vpn", JsonPrimitive(vpn))
            put("validated", JsonPrimitive(validated))
            put("metered", JsonPrimitive(metered))
            if (wifi) {
                val wm = appContext.applicationContext
                    .getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val info = wm?.connectionInfo
                put("ssid", JsonPrimitive(info?.ssid?.trim('"') ?: ""))
                put("linkSpeedMbps", JsonPrimitive(info?.linkSpeed ?: -1))
                put("rssiDbm", JsonPrimitive(info?.rssi ?: -127))
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put("restrictBackground", JsonPrimitive(
                    cm.restrictBackgroundStatus,
                ))
            }
        }
        return ok(body.toString())
    }

    /**
     * `agent_call_dial` — opens the dialer with a pre-filled number.
     * No permission required. The user still has to tap the green
     * button; we never place the call ourselves.
     */
    private suspend fun callDialImpl(args: JsonObject): McpEvent.ToolResult {
        if (!registry.isAllowed(AgentCapability.Call)) {
            return denied(AgentCapability.Call)
        }
        val number = args["number"]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
        if (number.isBlank()) {
            return McpEvent.ToolResult(
                providerId = AgentCapabilityTools.PROVIDER_ID,
                callId = "",
                ok = false,
                body = "missing 'number' arg",
            )
        }
        val intent = Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return withContext(Dispatchers.Main) {
            runCatching { appContext.startActivity(intent) }
                .fold(
                    onSuccess = {
                        ok(buildJsonObject {
                            put("status", JsonPrimitive("dialer-opened"))
                            put("number", JsonPrimitive(number))
                        }.toString())
                    },
                    onFailure = { err ->
                        McpEvent.ToolResult(
                            providerId = AgentCapabilityTools.PROVIDER_ID,
                            callId = "",
                            ok = false,
                            body = "dialer-failed: ${err.message}",
                        )
                    },
                )
        }
    }

    private fun denied(capability: AgentCapability) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = false,
        body = "permission-denied: ${capability.tag}",
    )

    private fun ok(body: String) = McpEvent.ToolResult(
        providerId = AgentCapabilityTools.PROVIDER_ID,
        callId = "",
        ok = true,
        body = body,
    )
}