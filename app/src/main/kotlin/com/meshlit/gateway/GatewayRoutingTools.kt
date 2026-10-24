package com.meshlit.gateway
import com.meshlit.core.mcp.*
import kotlinx.serialization.json.*
/** Lazy host resolution avoids the registry/host dependency cycle. No configuration,
 * human resume or permissions changes are exposed to model-generated calls. */
class GatewayRoutingTools(private val host:()->GatewayHost) {
    fun specs()=listOf(
        McpToolSpec("gateway_routes_list","List routes explicitly enabled and delegated by the owner; no credentials.") {
            McpToolResult.Json(Json.encodeToJsonElement(host().routing.routes.value.filter{it.enabled && it.agentAllowed}))
        },
        McpToolSpec("gateway_route_execute","Execute a delegated LLM/MCP/A2A route once; failure is not automatically replayed.",buildJsonObject{
            put("type","object");put("required",buildJsonArray{add("routeId");add("arguments")})
            put("properties",buildJsonObject{put("routeId",buildJsonObject{put("type","string")});put("arguments",buildJsonObject{put("type","object")})})
        }) {args->McpToolResult.Json(host().routing.execute(args.jsonObject["routeId"]!!.jsonPrimitive.content,args.jsonObject["arguments"]!!.jsonObject,true))}
    )
}
