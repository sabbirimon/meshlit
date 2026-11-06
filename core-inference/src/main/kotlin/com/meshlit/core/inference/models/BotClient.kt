package com.meshlit.core.inference.models

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.BufferedSource
import java.util.UUID
import java.util.concurrent.TimeUnit

@Serializable enum class BotConnectionKind(val label:String) {
    ASTRBOT("AstrBot chat"), GROK_ROUTINE("Grok Bot routine")
}
@Serializable data class BotConnection(val id:String=UUID.randomUUID().toString(),val name:String="",val endpoint:String="",
    val kind:BotConnectionKind=BotConnectionKind.ASTRBOT,val configId:String="",val enabled:Boolean=false) {
    fun destination():HttpUrl {
        require(UUID.fromString(id).toString()==id && name.length in 1..60 && name.none(Char::isISOControl))
        require(endpoint.length in 1..2048 && endpoint.none{it.isWhitespace() || it.isISOControl()})
        val url=endpoint.toHttpUrl()
        require(url.username.isEmpty() && url.password.isEmpty() && url.query==null && url.fragment==null)
        require(url.isHttps || kind==BotConnectionKind.ASTRBOT && url.host in setOf("127.0.0.1","::1")) { "Use HTTPS or a literal loopback AstrBot tunnel" }
        require(configId.isEmpty() || kind==BotConnectionKind.ASTRBOT && configId.matches(Regex("[A-Za-z0-9_.-]{1,80}")))
        return when(kind) {
            BotConnectionKind.ASTRBOT -> if(url.encodedPath.endsWith("/api/v1/chat"))url else
                (url.toString().trimEnd('/')+"/api/v1/chat").toHttpUrl()
            BotConnectionKind.GROK_ROUTINE -> url.also{require(it.encodedPath!="/"){"Paste the exact routine webhook URL"}}
        }
    }
    val astrBotUsername get()="meshlit_${id.replace("-","")}" // Stable per local connection, never an administrator name supplied by the model.
    override fun toString()="BotConnection(id=$id, kind=$kind, destination=REDACTED, enabled=$enabled)"
}

sealed interface BotResult {
    data class Reply(val text:String):BotResult
    data object RoutineAccepted:BotResult
}

/** Human-initiated requests only, no retries or follow-on channel dispatch. */
class BotClient {
    private val http=OkHttpClient.Builder().connectTimeout(10,TimeUnit.SECONDS).readTimeout(75,TimeUnit.SECONDS)
        .callTimeout(120,TimeUnit.SECONDS).retryOnConnectionFailure(false).followRedirects(false).followSslRedirects(false).build()

    suspend fun send(connection:BotConnection,token:String,input:String,sessionId:String,beforeDispatch:()->Unit={}):BotResult =
        withContext(Dispatchers.IO) {coroutineScope {
            val url=connection.destination()
            require(connection.enabled && token.length in 8..4096 && token.all{it.code in 33..126}){"Enable this connection with a valid token"}
            require(input.isNotBlank() && input.toByteArray(Charsets.UTF_8).size<=12000 && '\u0000' !in input){"Use at most 12 KiB of text"}
            require(UUID.fromString(sessionId).toString()==sessionId)
            val body=buildJsonObject {
                if(connection.kind==BotConnectionKind.ASTRBOT) {
                    put("username",connection.astrBotUsername);put("session_id",sessionId);put("message",input)
                    put("enable_streaming",false)
                    if(connection.configId.isNotBlank())put("config_id",connection.configId)
                } else put("input",input)
            }
            val call=http.newCall(Request.Builder().url(url).header("Authorization","Bearer $token")
                .header("Accept",if(connection.kind==BotConnectionKind.ASTRBOT)"text/event-stream" else "application/json")
                .post(body.toString().oneShotJsonBody()).build())
            val watch=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
            try {
                currentCoroutineContext().ensureActive();beforeDispatch()
                call.execute().use { response ->
                    check(response.code==200){"Bot endpoint returned HTTP ${response.code}"}
                    if(connection.kind==BotConnectionKind.GROK_ROUTINE)BotResult.RoutineAccepted
                    else {
                        require(response.header("Content-Type").orEmpty().substringBefore(';').trim()=="text/event-stream") {"AstrBot returned no chat stream"}
                        BotResult.Reply(readAstrBotReply(checkNotNull(response.body).source(),sessionId))
                    }
                }
            } finally {watch.cancel()}
        }}
}

private suspend fun readAstrBotReply(source:BufferedSource,sessionId:String):String {
    val answer=StringBuilder();var bytes=0;var seenSession=false
    while(!source.exhausted()) {
        currentCoroutineContext().ensureActive()
        val line=source.readUtf8LineStrict(65536)
        bytes+=line.toByteArray(Charsets.UTF_8).size+2
        require(bytes<=1_048_576){"AstrBot stream exceeds 1 MiB"}
        if(!line.startsWith("data:"))continue
        val event=runCatching{Json.parseToJsonElement(line.removePrefix("data:").trim()) as? JsonObject}.getOrNull()
            ?: error("AstrBot returned an invalid event")
        fun value(key:String)=(event[key] as? JsonPrimitive)?.takeIf{it.isString}?.content
        when(value("type")) {
            "session_id" -> {
                require(!seenSession && (value("session_id") ?: value("data"))==sessionId){"AstrBot returned a different session"}
                seenSession=true
            }
            "plain" -> if(value("chain_type") !in setOf("reasoning","tool_call","tool_call_result","agent_stats")) {
                require(seenSession){"AstrBot text arrived without a verified session"}
                val text=value("data") ?: error("AstrBot text event is invalid")
                require(answer.length+text.length<=48000){"AstrBot text exceeds the display limit"}
                answer.append(text)
            }
            "error","follow_up_captured" -> error("AstrBot reported a failed or interrupted run")
            "end" -> {
                check(seenSession && answer.isNotBlank()){ "AstrBot finished without a text reply" }
                return answer.toString()
            }
        }
    }
    error("AstrBot disconnected before the end of its reply")
}
