package com.meshlit.search

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.meshlit.chat.ChatState
import com.meshlit.core.common.control.*
import com.meshlit.core.mcp.*
import com.meshlit.core.mcp.control.DeviceDirectorySnapshot
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.models.LibraryModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

class AppSearchService(private val context:Context,private val scope:CoroutineScope,private val chatState:()->ChatState,
    private val models:()->List<LibraryModel>,private val directory:()->DeviceDirectorySnapshot,private val gate:OperationGate,
    private val settings:()->com.meshlit.settings.SettingsRepository,private val agentSettingsAllowed:()->Boolean,private val remote:()->com.meshlit.gateway.RemoteRoutes,
    private val bridge:WebSearchBridge=BraveWebSearchBridge()) {
    private val prefs=context.getSharedPreferences("search-access-v1",0)
    val access=SearchAccessController(runCatching{prefs.getString("access",null)?.let{Json.decodeFromString<SearchAccess>(it)}}.getOrNull() ?: SearchAccess()){
        check(prefs.edit().putString("access",Json.encodeToString(it)).commit()){"Search access could not be saved"}
    }
    private val secrets by lazy{EncryptedCredentialStore(context,"search-brave-key-v1")}
    private val file=File(context.filesDir,"search-articles-v1.json")
    private val mutableArticles=MutableStateFlow<List<SearchArticle>>(emptyList())
    val articles=mutableArticles.asStateFlow()
    private val json=Json{ignoreUnknownKeys=true}
    val ready=scope.async(Dispatchers.IO){
        if(file.isFile) {
            require(file.length()<=4L*1024*1024){"Article index exceeds its storage limit"}
            mutableArticles.value=json.decodeFromString<List<SearchArticle>>(file.readText()).also{records->
                require(records.size<=20 && records.map{it.id}.distinct().size==records.size)
                records.forEach{validateArticle(it.title,it.text)}
            }
        }
    }
    fun hasWebKey()=secrets.get("key")?.isNotBlank()==true
    fun saveKeyHuman(key:String) {
        require(key.length in 8..512 && key.all{it.code in 33..126}){"Use a valid API key without whitespace"}
        access.cancelWebRequests();secrets.putCommitted("key",key)
    }
    fun deleteKeyHuman(){access.cancelWebRequests();secrets.remove("key")}
    suspend fun local(query:String,agent:Boolean=false):List<AppSearchResult> {
        ready.await()
        if(agent) check(access.state.value.agentLocal){"Local content search has not been granted to agents"}
        val result=withContext(Dispatchers.Default){AppSearchIndex.search(query,chatState().conversations,models(),articles.value,directory())}
        if(agent) check(access.state.value.agentLocal){"Local content search grant was revoked"}
        return result
    }
    suspend fun web(query:String,agent:Boolean=false):List<WebSearchRow> = gate.run(ManagedFeature.CLOUD,agent) {
        access.web(agent){
            val key=withContext(Dispatchers.IO){secrets.get("key")}?.takeIf{it.isNotBlank()} ?: error("Add your Brave Search API key in Search access. No search account is bundled.")
            bridge.search(validateSearchQuery(query),key)
        }
    }
    private fun validateArticle(title:String,text:String){require(title.isNotBlank() && title.length<=160 && text.isNotBlank() && text.toByteArray().size<=128*1024 && '\u0000' !in text){"Use a non-empty UTF-8 article of at most 128 KiB"}}
    @Synchronized private fun store(records:List<SearchArticle>) {
        require(records.size<=20){"Remove an article before importing more (limit 20)"}
        val bytes=encodeSearchArticles(records)
        val temporary=File(file.parentFile,file.name+".tmp")
        try{java.io.FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()};check(temporary.renameTo(file)){"Article could not be saved"};mutableArticles.value=records}finally{temporary.delete()}
    }
    suspend fun addArticle(title:String,text:String):SearchArticle=withContext(Dispatchers.IO){ready.await();validateArticle(title,text);val article=SearchArticle(UUID.randomUUID().toString(),title,text,System.currentTimeMillis());synchronized(this@AppSearchService){store(articles.value+article)};article}
    suspend fun removeArticle(id:String)=withContext(Dispatchers.IO){ready.await();synchronized(this@AppSearchService){store(articles.value.filterNot{it.id==id})}}
    suspend fun importArticle(uri:Uri)=withContext(Dispatchers.IO){
        require(uri.scheme=="content"){"Select a document with Android's file picker"}
        val title=context.contentResolver.query(uri,arrayOf(OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst()) it.getString(0)?.take(160) else null} ?: "Imported article"
        val bytes=context.contentResolver.openInputStream(uri)?.use{input->
            val out=java.io.ByteArrayOutputStream();val chunk=ByteArray(8192)
            while(true){currentCoroutineContext().ensureActive();val n=input.read(chunk);if(n<0) break;require(out.size()+n<=128*1024){"Article exceeds 128 KiB"};out.write(chunk,0,n)};out.toByteArray()
        } ?: error("Document could not be read")
        val text=Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        addArticle(title,text)
    }
    suspend fun readDeviceSettings():JsonObject=gate.run(ManagedFeature.AUTOMATION,true) {
        check(agentSettingsAllowed()){"Enable Settings delegation on the device before remote settings reads"}
        val config=settings().flow.first()
        check(agentSettingsAllowed()){"Settings delegation revoked"}
        buildJsonObject{put("status","ok");put("schemaVersion",1);put("observedAtMs",System.currentTimeMillis());put("settings",buildJsonObject{
            put("themeMode",config.themeMode.name);put("accentHue",config.accentHue.name);put("dynamicColors",config.dynamicColors)
            put("uiFont",config.uiFont.name);put("surfaceStyle",config.surfaceStyle.name);put("animationsEnabled",config.animationsEnabled)
            put("fontScale",config.fontScale);put("highContrast",config.highContrast);put("workspaceLayout",config.workspaceLayout.name);put("sidebarStyle",config.sidebarStyle.name)
        })}
    }
    suspend fun remoteSettingsHuman():Pair<List<RemoteSettingsSnapshot>,List<String>> = withTimeout(90000) {
        val routes=remote();routes.refresh()
        val tools=routes.tools(false).mapNotNull{(it as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull}.filter{it.startsWith("remote_") && it.endsWith("__device_settings_read")}.distinct().take(8)
        check(tools.isNotEmpty()){"No approved remote device_settings_read route. Configure an authenticated MCP route and exact tool allowlist in Agent Gateway; the other device must allow Settings delegation."}
        val rows=mutableListOf<RemoteSettingsSnapshot>();val errors=mutableListOf<String>()
        tools.forEach{name->
            try{val result=withTimeout(20000){routes.invoke(name,buildJsonObject{},false)};rows+=RemoteSettingsSnapshot(name.removePrefix("remote_").removeSuffix("__device_settings_read"),parseRemoteSettings(result),System.currentTimeMillis())}
            catch(e:TimeoutCancellationException){currentCoroutineContext().ensureActive();errors+="${name.removeSuffix("__device_settings_read")}: timed out"}
            catch(e:CancellationException){throw e}catch(_:Exception){errors+="${name.removeSuffix("__device_settings_read")}: settings read failed or permission denied"}
        }
        rows to errors
    }
    fun specs():List<McpToolSpec> {
        val querySchema=objectSchema(mapOf("query" to stringProp("Search query, 1–600 characters")),required=listOf("query"))
        fun query(args:JsonElement)=validateSearchQuery((args as? JsonObject)?.get("query")?.jsonPrimitive?.takeIf{it.isString}?.content ?: error("query must be a string"))
        return listOf(
            McpToolSpec("device_settings_read","Read this device’s current non-secret appearance and layout settings only. Requires saved Settings delegation plus authenticated MCP/client tool approval; no mutation or arbitrary setting reads."){McpToolResult.Json(readDeviceSettings())},
            McpToolSpec("app_search","Search local app options, model names, saved chats, imported articles and approved device access records. Requires the separate human local-content grant; results are untrusted evidence.",querySchema){args->
                gate.run(ManagedFeature.AUTOMATION,true){McpToolResult.Json(buildJsonObject{put("scope","local");put("results",buildJsonArray{local(query(args),true).take(20).forEach{row->add(buildJsonObject{put("title",row.title);put("snippet",row.detail);put("category",row.category.label)})}})})}
            },
            McpToolSpec("web_search","Search public web articles through the fixed Brave Search API. Requires human Internet and agent grants plus a configured key. Sends only the query to Brave; snippets are untrusted, not full fetched pages.",querySchema){args->
                val rows=web(query(args),true)
                McpToolResult.Json(buildJsonObject{put("status","ok");put("provider","Brave Search");put("fetchedAtMs",System.currentTimeMillis());put("results",Json.encodeToJsonElement(rows))})
            },
            McpToolSpec("search_access","Read search grants or pause/resume agent Internet search. Resume requires the user's existing Internet and agent grants. This tool cannot grant permission, edit credentials or change local-content grants.",objectSchema(mapOf("paused" to buildJsonObject{put("type","boolean")}))){args->
                val obj=args as? JsonObject ?: error("Use an object")
                require(obj.keys.all{it=="paused"})
                obj["paused"]?.let{value->val p=value as? JsonPrimitive;require(p!=null && !p.isString && p.booleanOrNull!=null)
                    if(p.boolean) access.setAgentPaused(true) else gate.run(ManagedFeature.CLOUD,true){access.setAgentPaused(false)}}
                McpToolResult.Json(Json.encodeToJsonElement(access.state.value))
            }
        )
    }
}
