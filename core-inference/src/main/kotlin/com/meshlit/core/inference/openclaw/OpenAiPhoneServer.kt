package com.meshlit.core.inference.openclaw

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Semaphore

/** Opt-in OpenAI text provider. No remote model loading, tools or privilege grant. */
class OpenAiPhoneServer(
    host:String, port:Int, private val token:String,
    private val model:()->ModelInfo?,
    private val generate:suspend(InferenceRequest)->MeshlitResult<InferenceResult>,
):NanoHTTPD(host,port) {
    private val gate=Semaphore(1)
    init { require(token.length>=32) }
    override fun serve(session:IHTTPSession):Response {
        val supplied=session.headers["authorization"].orEmpty().removePrefix("Bearer ")
        if(!MessageDigest.isEqual(token.toByteArray(),supplied.toByteArray())) return error(Response.Status.UNAUTHORIZED,"Authentication required")
        if(session.method==Method.GET && session.uri=="/v1/models") {
            val loaded=model()
            return json(buildJsonObject { put("object","list");put("data",buildJsonArray {
                if(loaded!=null) add(buildJsonObject{put("id","meshlit-local");put("object","model");put("owned_by","meshlit")})
            }) })
        }
        if(session.method!=Method.POST || session.uri!="/v1/chat/completions") return error(Response.Status.NOT_FOUND,"Unknown endpoint")
        if(!gate.tryAcquire()) return error(Response.Status.TOO_MANY_REQUESTS,"Phone generation is busy")
        try {
            if(model()==null) return error(Response.Status.SERVICE_UNAVAILABLE,"Load a local or layer pipeline model in Meshlit first")
            val length=session.headers["content-length"]?.toIntOrNull()
            if(length==null || length !in 1..262144 || session.headers.containsKey("transfer-encoding"))
                return error(Response.Status.BAD_REQUEST,"Content-Length required; maximum body is 256 KiB")
            val body=ByteArray(length);var offset=0
            while(offset<length){val read=session.inputStream.read(body,offset,length-offset);require(read>0){"Incomplete body"};offset+=read}
            val request=Json.parseToJsonElement(body.toString(Charsets.UTF_8)).jsonObject
            val parsed=PhoneChatRequest.parse(request)
            // Bound admission + inference; cancellation belongs to this call, never cancels unrelated UI work.
            val outcome=runBlocking { withTimeout(180_000) { generate(InferenceRequest(parsed.prompt,
                maxTokens=parsed.maxTokens,temperature=parsed.temperature,topP=parsed.topP,onToken={})) } }
            if(outcome !is MeshlitResult.Success) return error(Response.Status.INTERNAL_ERROR,"Model generation failed; inspect Meshlit logs")
            val result=outcome.value
            if(result.finishReason==FinishReason.ERROR || result.finishReason==FinishReason.CANCELLED)
                return error(Response.Status.INTERNAL_ERROR,"Model generation did not complete")
            val id="chatcmpl-${UUID.randomUUID()}"
            val finish=if(result.finishReason==FinishReason.MAX_TOKENS) "length" else "stop"
            val created=System.currentTimeMillis()/1000
            if(parsed.stream) {
                // Buffered SSE completion. Do not advertise token-by-token streaming or tool calls.
                fun chunk(delta:JsonObject,reason:JsonElement)=buildJsonObject {
                    put("id",id);put("object","chat.completion.chunk");put("created",created);put("model","meshlit-local")
                    put("choices",buildJsonArray{add(buildJsonObject{put("index",0);put("delta",delta);put("finish_reason",reason)})})
                }
                val content=chunk(buildJsonObject{put("role","assistant");put("content",result.finalText)},JsonNull)
                val terminal=chunk(buildJsonObject{},JsonPrimitive(finish))
                return newFixedLengthResponse(Response.Status.OK,"text/event-stream","data: $content\n\ndata: $terminal\n\ndata: [DONE]\n\n")
            }
            return json(buildJsonObject {
                put("id",id);put("object","chat.completion");put("created",created);put("model","meshlit-local")
                put("choices",buildJsonArray{add(buildJsonObject{put("index",0);put("finish_reason",finish)
                    put("message",buildJsonObject{put("role","assistant");put("content",result.finalText)})})})
                if(result.promptTokens!=null && result.generatedTokens!=null) put("usage",buildJsonObject{
                    put("prompt_tokens",result.promptTokens);put("completion_tokens",result.generatedTokens)
                    put("total_tokens",result.promptTokens+result.generatedTokens)})
            })
        } catch(_:TimeoutCancellationException){return error(Response.Status.SERVICE_UNAVAILABLE,"Phone generation timed out")}
        catch(e:IllegalArgumentException){return error(Response.Status.BAD_REQUEST,e.message ?: "Invalid request")}
        catch(_:Exception){return error(Response.Status.INTERNAL_ERROR,"Provider request failed")}
        finally {gate.release()}
    }
    private fun json(value:JsonElement)=newFixedLengthResponse(Response.Status.OK,"application/json",value.toString())
    private fun error(status:Response.Status,message:String)=newFixedLengthResponse(status,"application/json",
        buildJsonObject{put("error",buildJsonObject{put("message",message);put("type","meshlit_provider_error")})}.toString())
}

data class PhoneChatRequest(val prompt:String,val maxTokens:Int,val temperature:Float,val topP:Float,val stream:Boolean) {
    companion object {
        fun parse(input:JsonObject):PhoneChatRequest {
            require(input["model"]?.jsonPrimitive?.content=="meshlit-local"){"Choose meshlit-local"}
            require(input["tools"]==null || input["tools"]==JsonNull || (input["tools"] as? JsonArray)?.isEmpty()==true){"Function tool calling is not supported by this phone provider"}
            val messages=input["messages"] as? JsonArray ?: error("messages must be an array")
            require(messages.size in 1..64){"Use 1–64 text messages"}
            val prompt=messages.joinToString("\n\n"){entry ->
                val message=entry.jsonObject
                val role=message["role"]?.jsonPrimitive?.content
                require(role in setOf("system","user","assistant")){"Only text system/user/assistant messages are supported"}
                val content=message["content"] as? JsonPrimitive
                require(content!=null && content.isString){"Text content is required"}
                "$role: ${content.content}"
            }+"\n\nassistant:"
            require(prompt.length<=32_000){"Context exceeds the phone text budget"}
            val max=(input["max_completion_tokens"] ?: input["max_tokens"])?.jsonPrimitive?.int ?: 256
            val temperature=input["temperature"]?.jsonPrimitive?.float ?: 0.7f
            val topP=input["top_p"]?.jsonPrimitive?.float ?: 0.95f
            require(max in 1..2048 && temperature.isFinite() && temperature in 0f..2f && topP.isFinite() && topP in 0f..1f){"Invalid sampling limits"}
            return PhoneChatRequest(prompt,max,temperature,topP,input["stream"]?.jsonPrimitive?.boolean ?: false)
        }
    }
}
