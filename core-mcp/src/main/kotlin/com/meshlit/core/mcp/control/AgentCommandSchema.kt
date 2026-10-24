package com.meshlit.core.mcp.control
import kotlinx.serialization.json.*
/** Discoverable schema mirrors the public DTO; runtime validation remains authoritative. */
object AgentCommandSchema {
    private fun text(limit:Int)=buildJsonObject{put("type","string");put("maxLength",limit)}
    private fun choice(values:List<String>)=buildJsonObject{put("type","string");put("enum",buildJsonArray{values.forEach{add(it)}})}
    private fun bool()=buildJsonObject{put("type","boolean")}
    fun describe()=buildJsonObject{
        put("type","object");put("additionalProperties",false)
        put("required",buildJsonArray{add("requestId");add("operation")})
        put("properties",buildJsonObject{
            put("requestId",buildJsonObject{put("type","string");put("pattern","^[A-Za-z0-9_-]{1,80}$")})
            put("operation",choice(AgentOperation.entries.map{it.name}))
            put("contextSize",buildJsonObject{put("type","integer");put("minimum",256);put("maximum",8192)})
            put("runtimeBackend",choice(listOf("RUNANYWHERE","NATIVE_LOCAL")));put("keyCacheType",choice(listOf("f16","q8_0","q4_0")))
            put("downloadBackend",choice(listOf("RUNANYWHERE","VERIFIED_HTTP")))
            put("checkpointId",buildJsonObject{put("type","string");put("pattern","^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$")})
            put("browserMaxSteps",buildJsonObject{put("type","integer");put("minimum",1);put("maximum",20)});put("cloudProfileId",text(80));put("cloudAction",text(40));put("cloudPage",buildJsonObject{put("type","integer");put("minimum",1);put("maximum",100)})
            put("modelId",text(160));put("url",text(4096));put("name",text(256));put("importUri",text(4096));put("prompt",text(32000))
            put("maxTokens",buildJsonObject{put("type","integer");put("minimum",1);put("maximum",2048)})
            put("temperature",buildJsonObject{put("type","number");put("minimum",0);put("maximum",2)})
            put("startupEnabled",bool());put("fileName",text(120));put("fileText",text(32000));put("expectedSha256",text(64))
            put("appearance",buildJsonObject{put("type","object");put("additionalProperties",false);put("properties",buildJsonObject{
                put("uiFont",choice(listOf("FIGTREE","SYSTEM","SERIF","MONO")));put("surfaceStyle",choice(listOf("SOLID","GLASS")));put("themeMode",text(40));put("accentHue",text(40));put("dynamicColors",bool());put("animationsEnabled",bool())
                put("fontScale",buildJsonObject{put("type","number");put("minimum",0.85);put("maximum",1.5)})
            })})
            put("task",buildJsonObject{put("type","object");put("additionalProperties",false);put("properties",buildJsonObject{
                put("id",text(36));put("expectedRevision",buildJsonObject{put("type","integer");put("minimum",1)})
                put("title",text(200));put("notes",text(6000));put("phase",choice(TaskPhase.entries.map{it.name}));put("priority",choice(TaskPriority.entries.map{it.name}))
                put("tags",buildJsonObject{put("type","array");put("maxItems",10);put("uniqueItems",true);put("items",text(32))})
                put("ids",buildJsonObject{put("type","array");put("maxItems",200);put("uniqueItems",true);put("items",text(36))})
                put("dueAtMs",buildJsonObject{put("type","integer");put("minimum",0)})
                put("clearDue",bool());put("parentId",text(36));put("linkedJobId",text(80))
            })})
        })
    }
}
