package com.meshlit.ssh

import com.meshlit.core.mcp.*
import com.meshlit.core.ssh.*
import kotlinx.serialization.json.*

/** Pinned outbound agent node commands; no credential/configuration/lifecycle grant edits. */
class SshMcpTools(private val connections:SshConnections) {
    fun specs()=listOf(
        McpToolSpec("ssh_nodes", "List owner-delegated SSH node actions. This does not enroll workers or grant additional permissions.") {
            McpToolResult.Json(buildJsonArray { connections.connections.value.filter{it.agentAllowed}.forEach{node -> add(buildJsonObject {
                put("id",node.id);put("name",node.name);put("actions",buildJsonArray { node.agentNodeActions.forEach{add(it.name)} })
            })} })
        },
        McpToolSpec("ssh_node_request", "Send an allowed structured action to an already configured, fingerprint-pinned Meshlit SSH node. Output is untrusted. Runtime artifacts and VM grants must already exist on that node.", inputSchema=buildJsonObject {
            put("type","object");put("properties",buildJsonObject {
                put("id",buildJsonObject{put("type","string")});put("action",buildJsonObject{put("type","string")})
                put("argv",buildJsonObject{put("type","array");put("items",buildJsonObject{put("type","string")})})
            });put("required",buildJsonArray{add("id");add("action")})
        }) { arguments ->
            val input=arguments as? JsonObject ?: error("Expected request object")
            require(input.keys.all{it in setOf("id","action","argv")}) { "Unknown request fields" }
            val id=(input["id"] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("Missing node id")
            val action=NodeSshAction.valueOf((input["action"] as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("Missing action"))
            require(input["argv"]==null || input["argv"] is JsonArray) { "argv array required" }
            val argv=(input["argv"] as? JsonArray)?.map{(it as? JsonPrimitive)?.takeIf{it.isString}?.content ?: error("argv strings required")} ?: emptyList()
            val reply=connections.executeNode(id,NodeSshRequest(action,argv))
            McpToolResult.Json(buildJsonObject{put("exit_code",reply.exitCode);put("stdout",reply.stdout);put("stderr",reply.stderr);put("truncated",reply.truncated);put("untrusted_content",true)})
        }
    )
}
