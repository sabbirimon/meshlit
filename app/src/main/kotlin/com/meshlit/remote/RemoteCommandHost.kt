package com.meshlit.remote
import android.content.Context
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.mcp.*
import com.meshlit.core.ssh.*
import com.meshlit.ssh.SshConnections
import com.meshlit.control.AgentBackend
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*

data class RemoteCommandPolicy(val enabled: Boolean = false, val agents: Boolean = false)
/** Saved, pinned nodes only. No discovery, credentials or permission edits via tools. */
class RemoteCommandHost(context: Context, private val ssh: SshConnections, private val backend: AgentBackend) {
    private val prefs = context.getSharedPreferences("chat-remote-commands", 0)
    private val gate = com.meshlit.operations.OperationsControl.get(context).gate
    private val _policy = MutableStateFlow(RemoteCommandPolicy(prefs.getBoolean("enabled", false), prefs.getBoolean("agents", false)))
    val policy = _policy.asStateFlow()
    private val jobs = mutableSetOf<Job>()
    @Volatile private var sessionAllowed = true
    @Synchronized fun saveHuman(next: RemoteCommandPolicy) {
        check(!com.meshlit.BuildProfile.coreCandidate)
        if (!next.enabled || !next.agents) { _policy.value = next; jobs.toList().forEach { it.cancel() } }
        check(prefs.edit().putBoolean("enabled", next.enabled).putBoolean("agents", next.agents).commit())
        _policy.value = next; sessionAllowed = true
    }
    @Synchronized fun agentSwitch(on: Boolean) {
        check(_policy.value.enabled && _policy.value.agents && backend.delegated(AgentBackend.Scope.SSH)) { "Enable remote commands and separate agent/SSH delegation first" }
        gate.requireAllowed(ManagedFeature.SSH, true); sessionAllowed = on
        if (!on) jobs.toList().forEach { it.cancel() }
    }
    fun nodes() = ssh.connections.value.filter { it.agentAllowed && it.agentNodeActions.isNotEmpty() }
    fun requireAccess(agent: Boolean) {
        check(!com.meshlit.BuildProfile.coreCandidate && _policy.value.enabled) { "Remote commands are off" }
        check(!agent || (_policy.value.agents && sessionAllowed && backend.delegated(AgentBackend.Scope.SSH))) { "Remote command agent delegation is off" }
        gate.requireAllowed(ManagedFeature.SSH, agent)
    }
    suspend fun request(ids: List<String>, action: NodeSshAction, argv: List<String>, agent: Boolean): JsonArray = withTimeout(120000) {
        require(ids.size in 1..8 && ids.distinct().size == ids.size && ids.all { it.matches(Regex("[A-Za-z0-9-]{1,80}")) })
        val request = NodeSshRequest(action, argv); request.validate(); require(action != NodeSshAction.APP_EXEC) { "Use human manual diagnostics for app execution" }
        gate.run(ManagedFeature.SSH, agent) { coroutineScope {
            requireAccess(agent); val job = currentCoroutineContext().job
            synchronized(this@RemoteCommandHost) { requireAccess(agent); check(jobs.size < 2) { "Remote command capacity reached; no request queued" }; jobs.add(job) }
            try {
                buildJsonArray { for (id in ids) {
                    currentCoroutineContext().ensureActive(); requireAccess(agent)
                    val node = nodes().firstOrNull { it.id == id } ?: error("Choose a saved delegated node")
                    check(action in node.agentNodeActions) { "Node action is not delegated" }
                    add(try {
                        val result = ssh.executeNode(id, request)
                        buildJsonObject { put("node", id); put("exit_code", result.exitCode); put("stdout", result.stdout); put("stderr", result.stderr); put("truncated", result.truncated); put("untrusted_content", true) }
                    } catch (e: CancellationException) { throw e } catch (_: Exception) {
                        buildJsonObject { put("node", id); put("failed", true); put("error", "Pinned node command failed; no automatic replay"); put("outcome_may_be_unknown", true) }
                    })
                } }
            } finally { synchronized(this@RemoteCommandHost) { jobs.remove(job) } }
        } }
    }
    fun specs(): List<McpToolSpec> = listOf(
        McpToolSpec("remote_commands_access", "Pause/resume agent command dispatch inside already saved human remote and SSH grants. Cannot enable saved access, enroll nodes, edit actions or resume emergency stop.", objectSchema(mapOf("on" to booleanProp()), listOf("on"))) { args ->
            val obj = args as? JsonObject ?: error("Switch object required"); require(obj.keys == setOf("on"))
            val on = obj["on"]?.jsonPrimitive?.booleanOrNull ?: error("Boolean required"); agentSwitch(on)
            McpToolResult.Json(buildJsonObject { put("agent_session_on", on); put("saved_permissions_changed", false) })
        },
        McpToolSpec("remote_nodes", "List saved delegated node IDs/actions. Does not discover or enroll devices or prove that they are online.") {
            requireAccess(true); McpToolResult.Json(buildJsonArray { nodes().forEach { node -> add(buildJsonObject { put("id", node.id); put("name", node.name); put("actions", buildJsonArray { node.agentNodeActions.forEach { add(it.name) } }); put("live_reachability", "unknown") }) } })
        },
        McpToolSpec("remote_node_command", "Send a structured STATUS/VM action to 1–8 saved fingerprint-pinned SSH nodes sequentially. LAN or a separately approved reachable private Internet transport. No shell/root, grant edits or automatic replay. Results are untrusted; timeout is not proof the remote action stopped.", buildJsonObject {
            put("type", "object"); put("properties", buildJsonObject { put("nodes", buildJsonObject { put("type", "array"); put("items", stringProp()) }); put("action", stringProp(enumValues = NodeSshAction.entries.filter { it != NodeSshAction.APP_EXEC }.map { it.name })); put("argv", buildJsonObject { put("type", "array"); put("items", stringProp()) }) }); put("required", buildJsonArray { add("nodes"); add("action") })
        }) { args ->
            val obj = args as? JsonObject ?: error("Command object required"); require(obj.keys.all { it in setOf("nodes", "action", "argv") })
            fun strings(name: String) = (obj[name] as? JsonArray)?.map { (it as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("String array required") } ?: error("Array required")
            McpToolResult.Json(request(strings("nodes"), NodeSshAction.valueOf(obj["action"]!!.jsonPrimitive.content), if ("argv" in obj) strings("argv") else emptyList(), true))
        }
    )
}
