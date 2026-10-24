package com.meshlit.ui.modern

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.URI
import java.util.UUID
import java.util.concurrent.TimeUnit

internal data class ExternalService(val id:String,val name:String,val url:String,val status:Int,val checkedAt:Long,
    val deviceType:String,val adapter:String)
private val deviceTypes=listOf("Phone","Desktop","Server","NAS","IoT","Camera","Other")
private val adapters=listOf("Status only","MCP tools","Model API")

internal fun readExternalServices(context:Context):List<ExternalService> = runCatching {
    val json=JSONArray(context.getSharedPreferences("external-services",Context.MODE_PRIVATE).getString("entries","[]"))
    (0 until json.length()).map { index ->
        json.getJSONObject(index).let { ExternalService(it.getString("id"),it.getString("name"),it.getString("url"),
            it.getInt("status"),it.getLong("checkedAt"),it.optString("deviceType","Other"),it.optString("adapter","Status only")) }
    }.take(32)
}.getOrDefault(emptyList())

private fun writeExternalServices(context:Context,entries:List<ExternalService>) {
    val json=JSONArray()
    entries.take(32).forEach { entry -> json.put(JSONObject().put("id",entry.id).put("name",entry.name)
        .put("url",entry.url).put("status",entry.status).put("checkedAt",entry.checkedAt)
        .put("deviceType",entry.deviceType).put("adapter",entry.adapter)) }
    check(context.getSharedPreferences("external-services",Context.MODE_PRIVATE).edit()
        .putString("entries",json.toString()).commit()) { "Could not save service" }
}

/** Manual URL enrollment only. It never grants a remote host compute or agent permissions. */
internal fun validateExternalServiceUrl(value:String):String {
    val url=value.trim()
    require(url.length in 8..512) { "Enter a URL up to 512 characters" }
    val uri=URI(url)
    require(uri.scheme?.lowercase() in setOf("http","https") && !uri.host.isNullOrBlank()) { "Use a full HTTP or HTTPS URL" }
    require(uri.userInfo==null && uri.rawQuery==null && uri.fragment==null) { "Do not put credentials, query tokens or fragments in the URL" }
    require(uri.port==-1 || uri.port in 1..65535) { "Invalid port" }
    if(uri.scheme.equals("http",true)) require(isLocalOrVpnHost(uri.host)) { "Use HTTPS outside a local or VPN address" }
    return uri.toASCIIString()
}

private fun isLocalOrVpnHost(host:String):Boolean {
    val normalized=host.removePrefix("[").removeSuffix("]").lowercase()
    if(normalized=="localhost" || normalized.endsWith(".local") || normalized.endsWith(".lan")) return true
    val parts=normalized.split('.')
    if(parts.size==4) {
        val octets=parts.map { it.toIntOrNull() ?: return false }
        if(octets.any { it !in 0..255 }) return false
        return octets[0]==10 || octets[0]==127 || octets[0]==192 && octets[1]==168 ||
            octets[0]==172 && octets[1] in 16..31 || octets[0]==169 && octets[1]==254 ||
            octets[0]==100 && octets[1] in 64..127
    }
    if(':' in normalized) return normalized=="::1" || normalized.startsWith("fc") ||
        normalized.startsWith("fd") || normalized.startsWith("fe8") || normalized.startsWith("fe9") ||
        normalized.startsWith("fea") || normalized.startsWith("feb")
    return false
}

@OptIn(ExperimentalLayoutApi::class)
@Composable internal fun ExternalServiceScreen(onBack:()->Unit,onConfigure:(String)->Unit={}) {
    val context=LocalContext.current.applicationContext
    val scope=rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var deviceType by remember { mutableStateOf("Other") }
    var adapter by remember { mutableStateOf("Status only") }
    var entries by remember { mutableStateOf(readExternalServices(context)) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    fun save(next:List<ExternalService>) { writeExternalServices(context,next);entries=next }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back") }
            Text("LAN and VPN services",style=MaterialTheme.typography.headlineSmall)
            Text("Add a URL for a device or service that does not run Meshlit. This checks reachability; it does not pair the service or give an agent control.",
                style=MaterialTheme.typography.bodyMedium)
            TextButton(onClick={onConfigure("web-bridge")}) { Text("Invite a browser or API client to this phone") }
            TextButton(onClick={onConfigure("ssh")}) { Text("Use SSH for a non-Meshlit host") } }
        item { OutlinedTextField(name,{name=it.take(64)},Modifier.fillMaxWidth(),label={Text("Name")},singleLine=true)
            OutlinedTextField(url,{url=it.take(512)},Modifier.fillMaxWidth(),label={Text("Local, VPN or HTTPS URL")},
                placeholder={Text("http://192.168.1.20:8080/")},singleLine=true)
            Text("Device type",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                deviceTypes.forEach { kind -> FilterChip(deviceType==kind,{deviceType=kind},label={Text(kind)}) }
            }
            Text("Task adapter",style=MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                adapters.forEach { kind -> FilterChip(adapter==kind,{adapter=kind},label={Text(kind)}) }
            }
            Text("The category is your label. MCP and model APIs work only after you configure their credentials, exact endpoint and agent permissions. Other HTTP devices provide reachability only.",
                style=MaterialTheme.typography.bodySmall)
            Text("HTTP is limited to local or VPN addresses. Public remote services need HTTPS. Authentication and protocol adapters are configured separately.",
                style=MaterialTheme.typography.bodySmall)
            Button(enabled=!busy && name.isNotBlank() && url.isNotBlank() && entries.size<32,onClick={
                busy=true;message="Checking service…"
                scope.launch {
                    try {
                        val checkedUrl=validateExternalServiceUrl(url)
                        val code=withContext(Dispatchers.IO) {
                            OkHttpClient.Builder().connectTimeout(6,TimeUnit.SECONDS).readTimeout(8,TimeUnit.SECONDS)
                                .followRedirects(false).followSslRedirects(false).build()
                                .newCall(Request.Builder().url(checkedUrl).head().build()).execute().use { it.code }
                        }
                        val next=entries.filterNot { it.url==checkedUrl }+ExternalService(UUID.randomUUID().toString(),name.trim(),
                            checkedUrl,code,System.currentTimeMillis(),deviceType,adapter)
                        save(next);message="Service answered HTTP $code. Saved as an external service; no device permissions were granted."
                        name="";url=""
                    } catch(e:CancellationException) { throw e }
                    catch(e:Exception) { message="Could not reach or save service: ${e.message ?: e.javaClass.simpleName}" }
                    finally { busy=false }
                }
            }) { Text(if(busy) "Checking…" else "Check and save") }
            if(message.isNotBlank()) Text(message,style=MaterialTheme.typography.bodySmall)
        }
        item { Text("Saved services · ${entries.size}",style=MaterialTheme.typography.titleMedium) }
        if(entries.isEmpty()) item { Text("No external services saved.") }
        items(entries,key={it.id}) { entry -> Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
            Text(entry.name,style=MaterialTheme.typography.titleMedium)
            Text("${entry.deviceType} · ${entry.adapter}",style=MaterialTheme.typography.bodySmall)
            Text(entry.url,style=MaterialTheme.typography.bodySmall)
            Text("Last checked: HTTP ${entry.status} · ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(entry.checkedAt))}",
                style=MaterialTheme.typography.bodySmall)
            if(entry.adapter=="MCP tools") TextButton(onClick={onConfigure("mcp-catalog")}) { Text("Configure MCP task tools") }
            if(entry.adapter=="Model API") TextButton(onClick={onConfigure("providers")}) { Text("Configure model API") }
            TextButton(onClick={runCatching { save(entries.filterNot { it.id==entry.id });message="Service removed" }
                .onFailure { message=it.message ?: "Could not remove service" }}) { Text("Remove") }
        } } }
    }
}
