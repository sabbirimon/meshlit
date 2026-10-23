package com.meshlit.control
import android.content.Context
import com.meshlit.core.mcp.*
import com.meshlit.power.*
import com.meshlit.devices.ExternalDevices
import com.meshlit.providers.OnlineProviders
import com.meshlit.ssh.SshConnections
import kotlinx.serialization.json.*
class EnvironmentTools(private val context:Context,private val backend:AgentBackend,private val providers:OnlineProviders,private val ssh:SshConnections,
    private val resources:com.meshlit.resources.ResourceSnapshotReader,
    private val radar:com.meshlit.disco.PeerRepository,private val pipeline:com.meshlit.pipeline.PipelineHost,
    private val discovery:com.meshlit.disco.DiscoveryModeStore) {
    private fun schema(properties:Map<String,JsonElement>,required:List<String> = emptyList())=buildJsonObject{put("type","object");put("properties",JsonObject(properties));put("required",JsonArray(required.map(::JsonPrimitive)));put("additionalProperties",false)}
    private fun string()=buildJsonObject{put("type","string")}
    fun specs()=listOf(
        McpToolSpec("device_audit_report","Generate a redacted, read-only device report. Requires separate saved AUDIT delegation; never reads other apps' data, secrets, payment or passkey state.") {
            require(backend.delegated(AgentBackend.Scope.AUDIT)){"Enable AUDIT delegation in Agents first"}
            val report=com.meshlit.audit.DeviceAuditReport(context,resources,radar,pipeline,discovery).collect()
            require(backend.delegated(AgentBackend.Scope.AUDIT)){"AUDIT delegation was revoked"}
            McpToolResult.Json(report)
        },
        McpToolSpec("resource_status","Read live device resources, Meshlit model and pipeline activity, and available sensors. Sensor availability is not active use."){
            McpToolResult.Json(Json.encodeToJsonElement(resources.read()))
        },
        McpToolSpec("device_radar_status","Read current discovered devices and saved layer worker endpoints. Network class is not physical distance; advertisements are unverified until pairing. No scan or connection is started.") {
            val state=radar.uiState.value
            McpToolResult.Json(buildJsonObject {
                put("scanning",state.scanning)
                put("discovered",buildJsonArray { state.grouped.values.flatten().distinctBy { it.advertisement.nodeId }.forEach { peer -> add(buildJsonObject {
                    put("node_id",peer.advertisement.nodeId);put("host",peer.advertisement.host);put("transport",peer.advertisement.transport)
                    put("network_class",peer.category.displayName);put("physical_distance_m",JsonNull);put("verified_identity",false)
                }) } })
                put("saved_workers",buildJsonArray { pipeline.peers().forEach { peer -> add(buildJsonObject {
                    put("host",peer.host);put("port",peer.port);put("group",peer.group);put("live_status_known",false)
                }) } })
            })
        },
        McpToolSpec("discovery_mode_set","Change device discovery between AUTO (visible app), ON (app process) and OFF. Requires the human's separate discovery delegation; no peer is trusted or paired by this change.",schema(mapOf("mode" to string()),listOf("mode"))) { args ->
            val values=args as? JsonObject ?: error("Mode object required")
            require(values.keys==setOf("mode"))
            val mode=com.meshlit.disco.DiscoveryMode.valueOf(values["mode"]!!.jsonPrimitive.content.uppercase(java.util.Locale.ROOT))
            discovery.saveAgent(mode)
            (context.applicationContext as com.meshlit.MeshlitApplication).refreshDiscovery()
            McpToolResult.Json(buildJsonObject { put("mode",mode.name);put("saved",true);put("peer_trust_changed",false) })
        },
        McpToolSpec("power_status","Read observed whole-device battery metrics and saved workload policy. Null values mean unavailable; watts are battery-side, not app-only."){
            val power=PowerRepository(context);McpToolResult.Json(buildJsonObject{put("reading",Json.encodeToJsonElement(power.reading()));put("policy",Json.encodeToJsonElement(power.policy()));put("downloadBlockedReason",power.downloadBlockReason()?.let(::JsonPrimitive) ?: JsonNull)})
        },
        McpToolSpec("external_device_status","Read actual USB devices, storage volumes and granted storage trees. Discovery does not supply accelerator drivers."){
            McpToolResult.Json(Json.encodeToJsonElement(ExternalDevices(context).status()))
        },
        McpToolSpec("online_profiles_list","List saved online model profiles without credentials. Agent use needs Models delegation and agentAllowed on the chosen profile."){
            McpToolResult.Json(buildJsonObject{put("profiles",Json.encodeToJsonElement(providers.profiles.value))})
        },
        McpToolSpec("ssh_connections_list","List configured SSH connections without secrets. Connections are not automatically approved compute workers."){
            McpToolResult.Json(buildJsonObject{put("connections",Json.encodeToJsonElement(ssh.connections.value))})
        },
        McpToolSpec("ssh_command_execute","Execute a command on a saved SSH host. Requires SSH delegation and the per-host agent opt-in; mandatory host-key pin, 60s and 64KiB output limits. No automatic retry.",schema(mapOf("connectionId" to string(),"command" to string()),listOf("connectionId","command"))){args->
            require(backend.delegated(AgentBackend.Scope.SSH)){"Enable human-controlled SSH delegation first"}
            val values=args as JsonObject;McpToolResult.Json(Json.encodeToJsonElement(ssh.execute(values["connectionId"]!!.jsonPrimitive.content,values["command"]!!.jsonPrimitive.content,agent=true)))
        }
    )
}
