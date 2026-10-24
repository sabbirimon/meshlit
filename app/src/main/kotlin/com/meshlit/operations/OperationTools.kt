package com.meshlit.operations
import com.meshlit.control.AgentBackend
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.mcp.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
class OperationTools(private val control:OperationsControl,private val capacity:ClusterControls,private val stop:StopCoordinator,private val backend:AgentBackend) {
    fun specs()=listOf(
        McpToolSpec("operations_status","Read saved stop latch, feature switches and capacity limits. No credentials; capacity is a management ceiling, not benchmark evidence.") {McpToolResult.Json(buildJsonObject{put("policy",Json.encodeToJsonElement(control.gate.policy.value));put("capacity",Json.encodeToJsonElement(capacity.state.value));put("stopReport",Json.encodeToJsonElement(stop.state.value))})},
        McpToolSpec("operations_emergency_stop","Latch emergency stop for this Meshlit instance and cancel managed work. Only a human may resume. Remote process termination is not guaranteed by closing a transport.") {stop.stopAll();McpToolResult.Json(buildJsonObject{put("emergencyStopped",true);put("remoteCancellationAcknowledged",false)})},
        McpToolSpec("cluster_inventory_capacity_set","Set inventory admission limit within separately saved human bounds; requires Cluster delegation. Does not enroll or approve devices or enlarge the native worker ceiling.",buildJsonObject{put("type","object");put("required",buildJsonArray{add("limit")});put("properties",buildJsonObject{put("limit",buildJsonObject{put("type","integer");put("minimum",1);put("maximum",10000)})});put("additionalProperties",false)}) {args->
            control.gate.requireAllowed(ManagedFeature.CLUSTER,true);require(backend.delegated(AgentBackend.Scope.CLUSTER));capacity.changeAgent(args.jsonObject["limit"]!!.jsonPrimitive.int);McpToolResult.Json(Json.encodeToJsonElement(capacity.state.value))
        }
    )
}
