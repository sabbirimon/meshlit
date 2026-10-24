package com.meshlit.gateway

import android.content.Context
import com.meshlit.core.mcp.gateway.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Human-enrolled upstream routes; a route and its tool allowlist are independent of local scopes. */
class RemoteRoutes(context: Context) {
    private val operations = com.meshlit.operations.OperationsControl.get(context).gate
    private val store = EncryptedCredentialStore(context, "gateway-remote-routes")
    private val json = Json { ignoreUnknownKeys = false }
    private val mutex = Mutex()
    @Volatile private var generation = 0L
    @Volatile private var discovered = buildJsonArray {}
    private val clients = java.util.concurrent.ConcurrentHashMap<String, RemoteAgentClient>()
    private val tasks = java.util.concurrent.ConcurrentHashMap<String, MutableSet<String>>()
    fun saved() = store.get("routes") ?: "[]"
    private fun profiles() = json.decodeFromString<List<RemoteAgentRoute>>(saved())
    @Synchronized fun save(text: String) {
        require(text.toByteArray().size <= 65536)
        val routes = json.decodeFromString<List<RemoteAgentRoute>>(text)
        require(routes.size <= 8 && routes.map { it.id }.distinct().size == routes.size)
        routes.forEach { it.validate() }
        store.putCommitted("routes", json.encodeToString(routes)); generation++; discovered = buildJsonArray {}; clients.clear(); tasks.clear()
    }
    fun tools(agent:Boolean = true) = JsonArray(discovered.filter{value-> val id=value.jsonObject["name"]!!.jsonPrimitive.content.removePrefix("remote_").substringBefore("__");profiles().any{it.id==id && it.humanEnabled && (!agent || it.agentEnabled)}})
    fun toolProtocol(name:String):RouteProtocol? = profiles().firstNotNullOfOrNull{it.federatedToolProtocol(name)}
    @Synchronized fun resetSession() { generation++; discovered = buildJsonArray {}; clients.clear(); tasks.clear() }
    suspend fun refresh(): Int = operations.run(com.meshlit.core.common.control.ManagedFeature.GATEWAY) { refreshManaged() }
    private suspend fun refreshManaged(): Int = mutex.withLock {
        val epoch = generation; val next = mutableMapOf<String, RemoteAgentClient>()
        val found = mutableListOf<JsonElement>()
        withTimeout(90000) { profiles().filter { it.humanEnabled }.forEach { route ->
            val client = RemoteAgentClient(route)
            if (route.protocol == "MCP") found.addAll(client.discoverTools(false))
            else { client.discoverAgent(false); listOf("message_send", "tasks_get", "tasks_cancel").forEach { method -> found += buildJsonObject {
                put("name", "remote_${route.id}__a2a_$method")
                put("description", "Approved remote A2A 0.3 JSON-RPC $method on ${route.id}; only task IDs created in this route session are readable/cancellable")
                put("inputSchema", buildJsonObject { put("type", "object"); put("properties", buildJsonObject {}) })
            } } }
            next[route.id] = client
        } }
        synchronized(this) { check(epoch == generation); clients.clear(); clients.putAll(next); discovered = JsonArray(found) }
        found.size
    }
    suspend fun invoke(name: String, args: JsonObject, agent:Boolean = true): JsonObject = operations.run(com.meshlit.core.common.control.ManagedFeature.GATEWAY,agent) { invokeManaged(name,args,agent) }
    private suspend fun invokeManaged(name: String, args: JsonObject, agent:Boolean): JsonObject {
        val epoch = generation
        require(discovered.any { it.jsonObject["name"]?.jsonPrimitive?.content == name }) { "Remote tool unavailable; refresh approved routes" }
        val parts = name.removePrefix("remote_").split("__", limit = 2); require(parts.size == 2)
        val route = profiles().single { it.id == parts[0] && it.humanEnabled && (!agent || it.agentEnabled) }
        val client = clients.getValue(route.id)
        val result = if (route.protocol == "MCP") client.rpc("tools/call", buildJsonObject { put("name", parts[1]); put("arguments", args) },agent)
        else {
            val owned = tasks.getOrPut(route.id) { java.util.concurrent.ConcurrentHashMap.newKeySet() }
            val method = when (parts[1]) { "a2a_message_send" -> "message/send"; "a2a_tasks_get" -> "tasks/get"; "a2a_tasks_cancel" -> "tasks/cancel"; else -> error("Unknown A2A method") }
            if (method != "message/send") require(args["id"]?.jsonPrimitive?.content in owned) { "Task not owned by route session" }
            else { require(owned.size < 100); val message = args["message"]!!.jsonObject; require(message["role"]?.jsonPrimitive?.content == "user"); require(message["taskId"] == null || message["taskId"]?.jsonPrimitive?.content in owned) }
            val value = client.rpc(method, args,agent)
            if (method == "message/send") value["id"]?.jsonPrimitive?.content?.let { require(it.length in 1..256); owned.add(it) }
            buildJsonObject { put("content", buildJsonArray { add(buildJsonObject { put("type", "text"); put("text", value.toString()) }) }); put("isError", false) }
        }
        require(epoch == generation && profiles().single { it.id == route.id } == route) { "Remote route revoked during operation" }
        return result
    }
}
