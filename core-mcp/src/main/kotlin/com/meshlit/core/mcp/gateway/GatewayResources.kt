package com.meshlit.core.mcp.gateway

import kotlinx.serialization.json.*

/** Protocol description, not an offer of measured capacity, a price quote or readiness proof. */
fun gatewayResources(principal:GatewayPrincipal,models:JsonArray):JsonObject = buildJsonObject {
    put("schema","meshlit.resources.v1")
    put("audience","Any authorized client; no Meshlit application required")
    put("transport",buildJsonObject {
        put("listener","loopback")
        put("lan","owner-configured authenticated encrypted tunnel required")
        put("wan","owner-configured authenticated encrypted tunnel required")
        put("automatic_nat_traversal",false)
    })
    put("resources",buildJsonArray {
        if(principal.allows(GatewayScope.CHAT)) models.forEach { model ->
            val id=model.jsonObject["id"]?.jsonPrimitive?.content ?: return@forEach
            if(principal.allowsModel(id)) add(buildJsonObject {
                put("kind","text_inference");put("model",id)
                put("endpoint","/v1/chat/completions");put("streaming",false)
                put("state","configured");put("readiness","not_verified_by_catalog")
                put("max_output_tokens",principal.maxOutputTokens)
                put("price",JsonNull);put("measured_capacity",JsonNull)
            })
        }
    })
    put("limits",buildJsonObject {put("global_active_requests",2);put("client_active_requests",if(principal.owner) 2 else 1);put("request_deadline_ms",120000)})
    put("unavailable_resources",buildJsonArray {
        listOf("raw_compute_rental","shared_cache_storage","bandwidth_resale","sensor_marketplace","wan_layer_scheduler").forEach {add(it)}
    })
    put("commercial",buildJsonObject {put("payments",false);put("provider_partnerships",false);put("contribution_receipts_are_money",false)})
}

/** Route aliases are model grants too, including when reached through MCP. */
fun GatewayPrincipal.authorizeRouteArguments(route:UnifiedRoute,args:JsonObject):JsonObject {
    if(route.protocol!=RouteProtocol.LLM) return args
    require(allowsModel("route:${route.id}")) {"Route is outside client model scope"}
    val limit=args["maxTokens"]?.jsonPrimitive?.int ?: minOf(256,maxOutputTokens)
    require(limit in 1..maxOutputTokens) {"Route exceeds client output limit"}
    return JsonObject(args+mapOf("maxTokens" to JsonPrimitive(limit)))
}
