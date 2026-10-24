package com.meshlit.core.mcp.gateway

import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Semaphore

/** Bounded, stateless MCP JSON-response transport and A2A 0.3 task bridge.
 * Loopback only. Authentication is independent of optional content filters.
 * Host agentgateway can front this endpoint through an explicitly configured tunnel.
 */
class EmbeddedGateway(port:Int,private val token:String,
    private val policy:()->GatewayPolicy,
    private val tools:()->JsonArray,
    private val invoke:suspend(String,JsonObject)->JsonObject,
    private val models:suspend()->JsonArray,
    private val chat:suspend(JsonObject)->JsonObject,
    private val sendTask:suspend(JsonObject)->JsonObject,
    private val getTask:suspend(String)->JsonObject?,
    private val cancelTask:suspend(String)->JsonObject?,
    private val clientAccess:GatewayClientAccess?=null,
):NanoHTTPD("127.0.0.1",port) {
    private val admissions=Semaphore(2)
    private val lifetime=SupervisorJob()
    init { require(port in 1024..65535); require(token.length in 32..4096 && token.none { it.isWhitespace() }) }
    override fun stop() { lifetime.cancel();super.stop() }
    override fun serve(session:IHTTPSession):Response {
        if(session.headers["origin"]!=null) return failure(Response.Status.FORBIDDEN,"Browser origins are not enabled")
        val authorization=session.headers["authorization"].orEmpty()
        if(!authorization.startsWith("Bearer ") || authorization.length>4103) return failure(Response.Status.UNAUTHORIZED,"Authentication required")
        val key=authorization.removePrefix("Bearer ")
        val owner=MessageDigest.isEqual(token.toByteArray(),key.toByteArray())
        val principal=if(owner) GatewayPrincipal("owner",GatewayScope.entries.toSet(),emptySet(),emptySet(),1024,Long.MAX_VALUE,true)
            else clientAccess?.authenticate(key) ?: return failure(Response.Status.UNAUTHORIZED,"Authentication required")
        val needed=when(session.uri){"/v1/models"->GatewayScope.MODELS;"/v1/chat/completions"->GatewayScope.CHAT;"/mcp"->GatewayScope.MCP;"/a2a","/.well-known/agent-card.json"->GatewayScope.A2A;else->null}
        if(needed!=null && !principal.allows(needed)) return failure(Response.Status.FORBIDDEN,"Client permission denied")
        if(!owner && clientAccess?.acquire(principal)!=true) return failure(Response.Status.TOO_MANY_REQUESTS,"Client rate or concurrency limit reached")
        if(!admissions.tryAcquire()) {if(!owner) clientAccess?.release(principal);return failure(Response.Status.TOO_MANY_REQUESTS,"Gateway is busy")}
        val context=lifetime+principal
        val deadline=minOf(120000L,principal.expiresAtMs-System.currentTimeMillis()).coerceAtLeast(1)
        try {
            if(session.method==Method.GET) return when(session.uri) {
                "/health"->json(buildJsonObject { put("status","ready");put("implementation","meshlit-kotlin");put("streaming",false) })
                "/.well-known/agent-card.json"->json(card())
                "/v1/models"->json(runBlocking(context) { withTimeout(15000) { buildJsonObject {put("object","list");put("data",JsonArray(models().filter{principal.allowsModel(it.jsonObject["id"]!!.jsonPrimitive.content)}))} } })
                "/mcp"->failure(Response.Status.METHOD_NOT_ALLOWED,"Server-initiated SSE is not supported")
                else->failure(Response.Status.NOT_FOUND,"Unknown endpoint")
            }
            if(session.method!=Method.POST) return failure(Response.Status.METHOD_NOT_ALLOWED,"Use POST")
            val length=session.headers["content-length"]?.toIntOrNull()
            require(length!=null && length in 1..262144 && session.headers["transfer-encoding"]==null) { "Content-Length required; maximum 256 KiB" }
            require(session.headers["content-type"].orEmpty().substringBefore(';').trim()=="application/json") { "Use application/json" }
            val bytes=ByteArray(length);var offset=0
            while(offset<length) { val count=session.inputStream.read(bytes,offset,length-offset);require(count>0);offset+=count }
            val request=Json.parseToJsonElement(bytes.toString(Charsets.UTF_8)) as? JsonObject ?: throw IllegalArgumentException("Object required")
            if(session.uri=="/v1/chat/completions") {
                require(request["stream"]?.jsonPrimitive?.booleanOrNull!=true) { "This embedded endpoint currently supports buffered text only" }
                require(request["tools"]==null) { "Use the MCP endpoint for tools; LLM tool calling is not available here" }
                return json(runBlocking(context) { withTimeout(deadline) { require(principal.allowsModel(request["model"]?.jsonPrimitive?.content.orEmpty())) { "Model is outside client scope" }
                    val limit=(request["max_completion_tokens"] ?: request["max_tokens"])?.jsonPrimitive?.int ?: minOf(256,principal.maxOutputTokens)
                    require(limit in 1..principal.maxOutputTokens)
                    val bounded=JsonObject(request+mapOf("max_tokens" to JsonPrimitive(limit)))
                    policy().check(bounded.toString());chat(bounded).also {policy().check(it.toString(),true)} } })
            }
            if(session.uri !in setOf("/mcp","/a2a")) return failure(Response.Status.NOT_FOUND,"Unknown endpoint")
            require(request["jsonrpc"]?.jsonPrimitive?.content=="2.0") { "JSON-RPC 2.0 required" }
            val method=request["method"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Method required")
            val id=request["id"]
            require(id==null || id is JsonPrimitive && id!=JsonNull && (id.isString || id.longOrNull!=null))
            val params=request["params"] as? JsonObject ?: buildJsonObject {}
            if(session.uri=="/mcp") {
                val version=session.headers["mcp-protocol-version"]
                require(version==null || version in VERSIONS) { "Unsupported MCP protocol version" }
                val accept=session.headers["accept"].orEmpty()
                require("application/json" in accept && "text/event-stream" in accept) { "Accept JSON and SSE" }
                if(id==null) {
                    require(method=="notifications/initialized") { "Unsupported notification" }
                    return newFixedLengthResponse(Response.Status.ACCEPTED,"application/json","")
                }
            } else require(id!=null) { "A2A requests require an ID" }
            return try {
                val result=runBlocking(context) { withTimeout(deadline) {
                    if(session.uri=="/mcp") when(method) {
                        "initialize"->buildJsonObject {
                            val requested=params["protocolVersion"]?.jsonPrimitive?.content
                            put("protocolVersion",requested?.takeIf {it in VERSIONS} ?: VERSIONS.last())
                            put("capabilities",buildJsonObject {put("tools",buildJsonObject {})})
                            put("serverInfo",buildJsonObject {put("name","meshlit");put("version","1.0")})
                        }
                        "ping"->buildJsonObject {}
                        "tools/list"->buildJsonObject {put("tools",JsonArray(tools().filter{principal.allowsTool(it.jsonObject["name"]!!.jsonPrimitive.content)}))}
                        "tools/call"->{
                            val name=params["name"]?.jsonPrimitive?.content ?: throw IllegalArgumentException("Tool name required")
                            require(principal.allowsTool(name)) {"Tool is outside client scope"}
                            val args=params["arguments"] as? JsonObject ?: buildJsonObject {}
                            policy().check(args.toString());invoke(name,args).also {policy().check(it.toString(),true)}
                        }
                        else->throw UnsupportedOperationException()
                    } else when(method) {
                        "message/send"->{policy().check(params.toString());sendTask(params)}
                        "tasks/get"->getTask(taskId(params)) ?: throw NoSuchElementException()
                        "tasks/cancel"->cancelTask(taskId(params)) ?: throw NoSuchElementException()
                        else->throw UnsupportedOperationException()
                    }
                } }
                json(buildJsonObject {put("jsonrpc","2.0");put("id",id ?: JsonNull);put("result",result)})
            } catch(_:UnsupportedOperationException) { rpcError(id,-32601,"Method not supported") }
            catch(_:NoSuchElementException) {rpcError(id,-32001,"Task not found")}
            catch(_:IllegalArgumentException) {rpcError(id,-32602,"Invalid parameters or policy denied")}
            catch(_:TimeoutCancellationException) {rpcError(id,-32000,"Request timed out; inspect task state before retry")}
            catch(_:Exception) {rpcError(id,-32000,"Operation failed; inspect permissions and task state")}
        } catch(_:IllegalArgumentException) {return failure(Response.Status.BAD_REQUEST,"Invalid request or policy denied")}
        catch(_:Exception) {return failure(Response.Status.INTERNAL_ERROR,"Gateway request failed")}
        finally {admissions.release();if(!owner) clientAccess?.release(principal)}
    }
    private fun taskId(params:JsonObject):String=params["id"]?.jsonPrimitive?.content?.also {require(it.matches(Regex("[A-Za-z0-9_-]{1,80}")))} ?: throw IllegalArgumentException()
    private fun card()=buildJsonObject {
        put("name","Meshlit");put("description","Owner-delegated model tasks; authentication and saved model permissions required")
        put("url","http://127.0.0.1:$listeningPort/a2a");put("version","1.0");put("protocolVersion","0.3.0");put("preferredTransport","JSONRPC")
        put("capabilities",buildJsonObject {put("streaming",false);put("pushNotifications",false)})
        put("defaultInputModes",buildJsonArray {add("text")});put("defaultOutputModes",buildJsonArray {add("text")})
        put("securitySchemes",buildJsonObject {put("bearer",buildJsonObject {put("type","http");put("scheme","bearer")})})
        put("security",buildJsonArray {add(buildJsonObject {put("bearer",buildJsonArray {})})})
        put("skills",buildJsonArray {add(buildJsonObject {put("id","model-task");put("name","Model task");put("description","Generate text using the owner-selected model");put("tags",buildJsonArray {add("text")})})})
    }
    private fun json(value:JsonElement)=newFixedLengthResponse(Response.Status.OK,"application/json",value.toString())
    private fun rpcError(id:JsonElement?,code:Int,message:String)=json(buildJsonObject {put("jsonrpc","2.0");put("id",id ?: JsonNull);put("error",buildJsonObject {put("code",code);put("message",message)})})
    private fun failure(status:Response.Status,message:String)=newFixedLengthResponse(status,"application/json",buildJsonObject {put("error",message)}.toString())
    companion object { val VERSIONS=listOf("2025-03-26","2025-06-18","2025-11-25") }
}
