package com.meshlit.routing
import com.meshlit.control.AgentBackend
import com.meshlit.core.mcp.*
import kotlinx.serialization.json.*
class RouterTools(private val routes:ModelRoutes,private val backend:AgentBackend) {
    fun specs()=listOf(
        McpToolSpec("model_routes_list","List human-configured scenario routes. These are text-generation routes; not layer sharding or media capability proof."){
            McpToolResult.Json(Json.encodeToJsonElement(routes.routes.value))
        },
        McpToolSpec("model_route_execute","Run an enabled text route, optionally selecting by scenario rules. Requires Models delegation and per-route/provider agent opt-in. No automatic retry/fallback. Multi-model execution is sequential; partial failure is explicit.",buildJsonObject{
            put("type","object");put("properties",buildJsonObject{
                listOf("routeId","scenario","prompt").forEach{key->put(key,buildJsonObject{put("type","string")})}
                put("maxTokens",buildJsonObject{put("type","integer");put("minimum",1);put("maximum",1024)})
            });put("required",buildJsonArray{add("prompt");add("scenario")});put("additionalProperties",false)
        }){args->
            val a=args as JsonObject
            val result=routes.execute(a["routeId"]?.jsonPrimitive?.contentOrNull,a["scenario"]!!.jsonPrimitive.content,
                a["prompt"]!!.jsonPrimitive.content,a["maxTokens"]?.jsonPrimitive?.intOrNull ?: 512,agent=true,
                authorize={require(backend.delegated(AgentBackend.Scope.MODELS)){"Models delegation is required"}})
            McpToolResult.Json(Json.encodeToJsonElement(result))
        }
    )
}
