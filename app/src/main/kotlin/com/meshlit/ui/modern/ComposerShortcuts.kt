package com.meshlit.ui.modern

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.BuildProfile
import com.meshlit.di.koinInject
import com.meshlit.gateway.GatewayHost
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream

internal val LocalResultToChat = staticCompositionLocalOf<(String,String)->Unit> { { _,_ -> } }

@Composable internal fun ExplainToolResult(title:String, result:()->String) {
    val toChat=LocalResultToChat.current
    TextButton({toChat(title,result())}) { Text("Explain in chat") }
}

internal data class ComposerPin(val command:String,val title:String,val target:String)
private val aliases=mapOf("mcp" to "mcp-catalog","internet" to "search","autopilot" to "chat-options")
private fun validTarget(target:String):Boolean {
    if(target.startsWith("https://")) {
        val uri=Uri.parse(target)
        return !uri.host.isNullOrBlank() && uri.userInfo==null && target.none{it.isWhitespace() || it=='\\'}
    }
    if(target.matches(Regex("mcp:[a-zA-Z0-9_.-]{1,128}")))return true
    val id=aliases[target]?:target
    return id=="chat-options" || WorkspaceDestinations.all.any{it.id==id && BuildProfile.routeAllowed(id)}
}
private fun decodePins(raw:String):List<ComposerPin>{
    require(raw.toByteArray().size<=65536)
    val doc=JSONObject(raw)
    require(doc.getInt("schema")==1)
    val rows=doc.getJSONArray("tools")
    require(rows.length()<=32)
    val seen=mutableSetOf<String>()
    return (0 until rows.length()).map{
        val p=rows.getJSONObject(it)
        val pin=ComposerPin(p.getString("command"),p.getString("title").trim(),p.getString("target"))
        require(pin.command.matches(Regex("[a-z][a-z0-9-]{0,31}")) && seen.add(pin.command) && pin.title.length in 1..64 && pin.target.length<=2048 && validTarget(pin.target))
        pin
    }
}
internal fun savedComposerPins(context:Context):List<ComposerPin>{
    val text=context.getSharedPreferences("composer-pins",0).getString("v1",null)?:return emptyList()
    return runCatching {decodePins(text)}.getOrDefault(emptyList())
}

@Composable internal fun ComposerToolPicker(initial:ComposerPin?,draft:String,initialQuery:String?,onClose:()->Unit,onRoute:(String)->Unit,onDraft:(String)->Unit) {
    val ctx=LocalContext.current
    val media=koinInject<com.meshlit.media.MediaGeneration>()
    val chat=koinInject<com.meshlit.chat.ChatController>()
    val gateway=koinInject<GatewayHost>().remoteRoutes
    val scope=rememberCoroutineScope()
    var pins by remember {mutableStateOf(savedComposerPins(ctx))}
    var target by remember {mutableStateOf("")}
    var title by remember {mutableStateOf("")}
    var command by remember {mutableStateOf("")}
    var query by remember {mutableStateOf(initialQuery.orEmpty())}
    var manage by remember {mutableStateOf(false)}
    var message by remember {mutableStateOf("")}
    var confirm by remember {mutableStateOf(initial)}
    var args by remember {mutableStateOf(if(draft.isBlank() || draft.startsWith("/tool ")) "{}" else runCatching {Json.parseToJsonElement(draft).jsonObject.toString()}.getOrElse {JSONObject().put("text",draft.take(3000)).toString()})}
    var imported by remember {mutableStateOf(emptyList<ComposerPin>())}
    var remote by remember {mutableStateOf(gateway.tools(false).map { it.jsonObject.getValue("name").jsonPrimitive.content })}
    var busy by remember {mutableStateOf(false)}
    fun save(next:List<ComposerPin>){
        val data=JSONObject().put("schema",1).put("tools",JSONArray(next.map {JSONObject().put("command",it.command).put("title",it.title).put("target",it.target)})).toString()
        val checked=decodePins(data)
        ctx.getSharedPreferences("composer-pins",0).edit().putString("v1",data).apply()
        pins=checked
    }
    fun stage(name:String,destination:String){
        title=name;target=destination;command=name.lowercase().replace(Regex("[^a-z0-9]+"),"-").trim('-').take(32)
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->
        if(uri!=null)scope.launch {
            try{
                imported=withContext(Dispatchers.IO){
                    requireNotNull(ctx.contentResolver.openInputStream(uri)).use { stream->
                        val out=ByteArrayOutputStream();val chunk=ByteArray(4096)
                        while(true){val n=stream.read(chunk);if(n<0)break;require(out.size()+n<=65536);out.write(chunk,0,n)}
                        decodePins(out.toString("UTF-8"))
                    }
                }
            }catch(e:CancellationException){throw e}catch(_:Exception){message="Choose a valid composer JSON file under 64 KiB with supported targets."}
        }
    }
    AlertDialog(onDismissRequest=onClose,title={Text("Composer tools")},text={
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)){
            TextButton({ manage = !manage }) { Text(if (manage) "Browse all tools" else "Customize shortcuts") }
            OutlinedTextField(query,{query=it},label={Text("Search tools and services")},singleLine=true)
            pins.filter { query.isBlank() || it.title.contains(query,true) || it.command.contains(query,true) }.forEach {pin->Row {
                TextButton({confirm=pin},Modifier.weight(1f)){Text(pin.title)}
                if (manage) TextButton({save(pins-pin)}){Text("Unpin")}
            }}
            val words=query.lowercase().split(Regex("\\W+")).filter { it.length>1 }
            val destinations=WorkspaceDestinations.all.filter { BuildProfile.routeAllowed(it.id) }.map { tool ->
                val text="${tool.id} ${tool.title} ${tool.description} ${tool.keywords}".lowercase()
                tool to words.count { it in text }
            }.filter { words.isEmpty() || it.second>0 }.sortedByDescending { it.second }
            destinations.forEach { (tool,_) ->
                TextButton({if (manage) stage(tool.title,tool.id) else {onClose();onRoute(tool.id)}}){Text(tool.title)}
            }
            TextButton({onClose();onRoute("mcp-catalog")}){Text("Connect an MCP service")}
            TextButton(enabled=!busy,onClick={scope.launch {
                busy=true
                try {withContext(Dispatchers.IO){gateway.refresh()};remote=gateway.tools(false).map {it.jsonObject.getValue("name").jsonPrimitive.content};message="Found ${remote.size} approved tools."}
                catch(e:CancellationException){throw e}catch(_:Exception){message="Check your MCP route, gateway permissions and allowed tool names."}finally{busy=false}
            }}){Text("Discover connected tools")}
            remote.filter { name -> query.isBlank() || words.any { it in name.lowercase() } }.forEach { name ->
                TextButton({ if(manage)stage(name,"mcp:"+name) else confirm=ComposerPin("",name,"mcp:"+name) }) { Text(name) }
            }
            if (manage) {
            OutlinedTextField(target,{target=it.take(2048)},label={Text("Destination or HTTPS service")})
            OutlinedTextField(title,{title=it.take(64)},label={Text("Shortcut title")})
            OutlinedTextField(command,{command=it.take(32)},label={Text("Command · /tool name")},singleLine=true)
            Button({runCatching {save(pins+ComposerPin(command,title,target))}.onSuccess{title="";target="";command="";message="Shortcut pinned."}.onFailure{message="Choose a supported target and a unique lowercase command."}}){Text("Pin shortcut")}
            TextButton({importer.launch(arrayOf("application/json","text/plain","application/octet-stream"))}){Text("Import shortcuts")}
            }
            if(message.isNotBlank())Text(message)
        }
    },confirmButton={TextButton(onClose){Text("Done")}})
    if(imported.isNotEmpty())AlertDialog(onDismissRequest={imported=emptyList()},title={Text("Import shortcuts?")},text={Text(imported.joinToString("\n"){it.title+" → "+it.target},Modifier.verticalScroll(rememberScrollState()))},
        confirmButton={TextButton({runCatching{save(pins+imported)}.onFailure{message="Duplicate command or too many shortcuts."};imported=emptyList()}){Text("Import")}},
        dismissButton={TextButton({imported=emptyList()}){Text("Cancel")}})
    confirm?.let {pin->
        val mcp=pin.target.startsWith("mcp:")
        AlertDialog(onDismissRequest={if(!busy)confirm=null},title={Text(pin.title)},text={Column{Text(pin.target);if(mcp)OutlinedTextField(args,{args=it.take(4000)},label={Text("JSON arguments")})}},
            confirmButton={TextButton(enabled=!busy,onClick={
                if(mcp)scope.launch{
                    busy=true
                    try{
                        val name=pin.target.removePrefix("mcp:")
                        val input=Json.parseToJsonElement(args).jsonObject
                        val result=withContext(Dispatchers.IO){gateway.invoke(name,input,agent=false)}
                        val parts=result["content"] as? JsonArray
                        val reports=mutableListOf<String>()
                        var attached=false
                        for(part in parts.orEmpty().take(16)){
                            val item=part as? JsonObject ?: continue
                            val resource=item["resource"] as? JsonObject
                            val body=resource ?: item
                            val mime=(body["mimeType"] as? JsonPrimitive)?.contentOrNull
                            val data=((body["blob"] ?: body["data"]) as? JsonPrimitive)?.contentOrNull
                            val text=(body["text"] as? JsonPrimitive)?.contentOrNull
                            if(mime!=null && (data!=null || resource!=null && text!=null)){
                                val file=media.saveToolFile(mime,data,text)
                                chat.addMediaToChat(file)
                                attached=true
                            }else if(text!=null)reports+=text
                        }
                        if(reports.isNotEmpty() || !attached)onDraft("Tool result · "+name+"\n"+(reports.takeIf{it.isNotEmpty()}?.joinToString("\n") ?: result.toString()))
                        onClose()
                    }catch(e:CancellationException){throw e}catch(_:Exception){message="Tool call failed. Check the arguments and approved route."}finally{busy=false;confirm=null}
                }else{
                    val id=aliases[pin.target]?:pin.target
                    if(id.startsWith("https://"))runCatching{ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(id)))}.onFailure{message="No browser available."}
                    else {onClose();onRoute(id)}
                    confirm=null
                }
            }){Text(if(mcp)"Run tool" else "Open")}},
            dismissButton={TextButton({confirm=null},enabled=!busy){Text("Cancel")}})
    }
}
