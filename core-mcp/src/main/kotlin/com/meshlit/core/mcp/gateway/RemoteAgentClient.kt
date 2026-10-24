package com.meshlit.core.mcp.gateway

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class RemoteAgentRoute(val id: String, val endpoint: String, val token: String,
    val protocol: String = "MCP", val allowedTools: Set<String> = emptySet(), val agentEnabled: Boolean = false, val humanEnabled: Boolean = true) {
    /** Metadata lookup only; it does not grant human/agent access or discover a tool. */
    fun federatedToolProtocol(name:String):RouteProtocol? {
        if(id.endsWith("_") || id.contains("__")) return null
        val prefix="remote_${id}__"
        if(!name.startsWith(prefix)) return null
        val tool=name.removePrefix(prefix)
        return when(protocol){
            "MCP"->if(tool in allowedTools) RouteProtocol.MCP else null
            "A2A"->if(tool in setOf("a2a_message_send","a2a_tasks_get","a2a_tasks_cancel")) RouteProtocol.A2A else null
            else->null
        }
    }
    fun validate() {
        require(id.matches(Regex("[a-z][a-z0-9_]{0,15}")) && !id.contains("__") && !id.endsWith("_") && protocol in setOf("MCP", "A2A"))
        val url = endpoint.toHttpUrl()
        require(url.scheme == "https" || url.scheme == "http" && url.host in setOf("127.0.0.1", "::1"))
        require(url.encodedUsername.isEmpty() && url.encodedPassword.isEmpty() && url.query == null && url.fragment == null)
        require(token.length in 32..4096 && token.none { it.isWhitespace() })
        require(allowedTools.size <= 50 && allowedTools.all { it.matches(Regex("[A-Za-z0-9_.-]{1,96}")) })
    }
}

/** JSON-response MCP and A2A JSON-RPC connector. No SSE/OAuth/browser fallback.
 * Discovered content is data, never local shell or permission instructions. */
class RemoteAgentClient(private val route: RemoteAgentRoute) {
    private val client = OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS).callTimeout(35, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private var session: String? = null
    private var version = "2025-11-25"
    init { route.validate() }
    suspend fun discoverAgent(agent: Boolean = true): JsonObject = withContext(Dispatchers.IO) {
        require(route.protocol == "A2A" && route.humanEnabled && (!agent || route.agentEnabled))
        // Exact origin, fixed discovery path. Never follow a card-supplied URL with credentials.
        val url = route.endpoint.toHttpUrl().newBuilder().encodedPath("/.well-known/agent-card.json").build()
        val call = client.newCall(Request.Builder().url(url).header("Authorization", "Bearer ${route.token}").get().build())
        val response = suspendCancellableCoroutine<Response> { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, value, _ -> value.close() } }
            }); cont.invokeOnCancellation { call.cancel() }
        }
        response.use { reply ->
            require(reply.isSuccessful && reply.header("Content-Type").orEmpty().startsWith("application/json"))
            val source = requireNotNull(reply.body).source(); require(!source.request(65537))
            val card = Json.parseToJsonElement(source.readUtf8()).jsonObject
            require(card["protocolVersion"]?.jsonPrimitive?.content == "0.3.0" && card["preferredTransport"]?.jsonPrimitive?.content == "JSONRPC") { "Only A2A 0.3 JSON-RPC discovery is supported" }
            require(card["name"]?.jsonPrimitive?.content?.length in 1..256)
            card
        }
    }
    private suspend fun post(body: JsonObject, notification: Boolean = false): JsonObject = withContext(Dispatchers.IO) {
        val bytes = body.toString().toByteArray(); require(bytes.size <= 262144)
        val request = Request.Builder().url(route.endpoint).header("Authorization", "Bearer ${route.token}")
            .header("Accept", "application/json, text/event-stream")
        if (route.protocol == "MCP") {
            request.header("MCP-Protocol-Version", version)
            session?.let { request.header("Mcp-Session-Id", it) }
        }
        val call = client.newCall(request.post(bytes.toRequestBody("application/json".toMediaType())).build())
        val response = suspendCancellableCoroutine<Response> { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, value, _ -> value.close() } }
            }); cont.invokeOnCancellation { call.cancel() }
        }
        response.use { reply ->
            require(reply.isSuccessful) { "Remote route rejected request" }
            if (notification) { require(reply.code == 202 || reply.code == 204); return@withContext buildJsonObject {} }
            require(reply.header("Content-Type").orEmpty().startsWith("application/json")) { "Remote streaming transport is unavailable" }
            val source = requireNotNull(reply.body).source(); require(!source.request(262145)) { "Remote reply exceeds 256 KiB" }
            val value = Json.parseToJsonElement(source.readUtf8()).jsonObject
            require(value["jsonrpc"]?.jsonPrimitive?.content == "2.0" && value["id"] == body["id"] && value["error"] == null) { "Remote RPC failure or mismatched ID" }
            if (body["method"]?.jsonPrimitive?.content == "initialize") {
                session = reply.header("Mcp-Session-Id")?.also { require(it.length in 1..128 && it.all { c -> c.code in 33..126 }) }
            }
            value["result"] as? JsonObject ?: throw IllegalArgumentException("Remote object result required")
        }
    }
    suspend fun rpc(method: String, params: JsonObject, agent: Boolean = true): JsonObject {
        require(route.humanEnabled && (!agent || route.agentEnabled)) { "Remote agent route disabled" }
        require(if (route.protocol == "MCP") method in setOf("initialize", "tools/list", "tools/call") else method in setOf("message/send", "tasks/get", "tasks/cancel"))
        if (method == "tools/call") require(params["name"]?.jsonPrimitive?.content in route.allowedTools) { "Remote tool not approved" }
        return post(buildJsonObject { put("jsonrpc", "2.0"); put("id", UUID.randomUUID().toString()); put("method", method); put("params", params) })
    }
    suspend fun discoverTools(agent: Boolean = true): JsonArray {
        require(route.protocol == "MCP")
        val initialized = rpc("initialize", buildJsonObject {
            put("protocolVersion", version); put("capabilities", buildJsonObject {})
            put("clientInfo", buildJsonObject { put("name", "Meshlit"); put("version", "1") })
        }, agent)
        version = initialized["protocolVersion"]!!.jsonPrimitive.content
        require(version in EmbeddedGateway.VERSIONS)
        post(buildJsonObject { put("jsonrpc", "2.0"); put("method", "notifications/initialized") }, notification = true)
        val approved = mutableListOf<JsonElement>(); var cursor: String? = null
        repeat(4) {
            val page = rpc("tools/list", buildJsonObject { cursor?.let { put("cursor", it) } }, agent)
            val tools = page["tools"] as? JsonArray ?: throw IllegalArgumentException("Remote tools missing")
            require(tools.size <= 200)
            tools.forEach { tool ->
                val obj = tool.jsonObject; val name = obj["name"]!!.jsonPrimitive.content
                require(name.matches(Regex("[A-Za-z0-9_.-]{1,96}")))
                if (name in route.allowedTools) {
                    require(obj["inputSchema"] is JsonObject)
                    approved.add(buildJsonObject {
                        put("name", "remote_${route.id}__$name"); put("description", "Approved remote MCP tool on ${route.id}: " + (obj["description"]?.jsonPrimitive?.content.orEmpty().take(1000)))
                        put("inputSchema", obj["inputSchema"]!!)
                    })
                }
            }
            cursor = page["nextCursor"]?.jsonPrimitive?.content?.also { require(it.length <= 1024) }
            if (cursor == null) { require(approved.map { it.jsonObject["name"] }.distinct().size == approved.size); return JsonArray(approved) }
        }
        error("Remote pagination budget exceeded")
    }
}
