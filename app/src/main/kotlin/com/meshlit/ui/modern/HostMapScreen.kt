package com.meshlit.ui.modern

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.di.koinInject
import com.meshlit.peering.HostLocationStore
import com.meshlit.peering.RelayManager
import com.meshlit.peering.ImonIpGeoLookup
import com.meshlit.peering.IpGeoEstimate
import com.meshlit.settings.SettingsRepository
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout

private data class MapHost(val id:String,val name:String,val type:String,val latitude:Double?,val longitude:Double?,
    val status:String,val path:String,val own:Boolean=false)
private data class SelfMapFocus(val latitude:Double,val longitude:Double,val request:Int)

/** Offline Leaflet/Natural Earth map. Coordinates are self-reported by relay members. */
@Composable internal fun HostMapScreen(onBack:()->Unit,onPrivacy:()->Unit,onDevices:()->Unit) {
    val app=LocalContext.current.applicationContext as MeshlitApplication
    val relay=koinInject<RelayManager>()
    val relayState by relay.state.collectAsStateWithLifecycle()
    val location=koinInject<HostLocationStore>()
    val pin by location.pin.collectAsStateWithLifecycle()
    val share by location.enabled.collectAsStateWithLifecycle()
    val settings=koinInject<SettingsRepository>()
    val endpoints by settings.remoteEndpointsFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("All") }
    var showIdentifiers by rememberSaveable { mutableStateOf(false) }
    var onlineStreets by rememberSaveable { mutableStateOf(false) }
    var imonServer by rememberSaveable { mutableStateOf("") }
    var lookupIp by rememberSaveable { mutableStateOf("") }
    var ipEstimate by remember { mutableStateOf<IpGeoEstimate?>(null) }
    var lookupMessage by remember { mutableStateOf<String?>(null) }
    var lookupBusy by remember { mutableStateOf(false) }
    var selfFocus by remember { mutableStateOf<SelfMapFocus?>(null) }
    var locateMessage by remember { mutableStateOf<String?>(null) }
    var locating by remember { mutableStateOf(false) }
    val scope=rememberCoroutineScope()
    val locate:()->Unit = {
        scope.launch {
            locating=true;locateMessage=null
            runCatching {
                val source=CancellationTokenSource()
                try { withTimeout(12_000) {
                    LocationServices.getFusedLocationProviderClient(app)
                        .getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY,source.token).await()
                        ?: error("No current location fix. Check Location services and try outdoors.")
                } } finally { source.cancel() }
            }.onSuccess { fix ->
                selfFocus=SelfMapFocus(fix.latitude,fix.longitude,(selfFocus?.request ?: 0)+1)
                locateMessage="Showing this phone's current position locally"
            }.onFailure { locateMessage=it.message ?: "Could not get current location" }
            locating=false
        }
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if(grants.values.any { it }) locate() else locateMessage="Location permission denied. The map can still be used without it."
    }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { kotlinx.coroutines.delay(15_000);now=System.currentTimeMillis() } }
    val members=relayState.members.distinctBy { it.nodeId }
        .filterNot { it.nodeId==app.nodeIdHex && !share && pin!=null }.map { member ->
        val age=now-member.lastSeenMs
        val status=when { age<0 -> "Status unknown";age<=90_000 && member.activityState=="hibernating" -> "Hibernating"
            age<=90_000 -> "Online";age<=86_400_000 -> "Recently online";else -> "Offline" }
        val recentPin=member.locationUpdatedMs>0 && now-member.locationUpdatedMs<=90_000
        MapHost(member.nodeId,member.name.ifBlank { "Relay member" },member.deviceType,
            member.latitude.takeIf { recentPin },member.longitude.takeIf { recentPin },status,
            if(relayState.enabled) "Hosted relay" else "Joined relay",member.nodeId==app.nodeIdHex)
    }
    val ownPin=pin?.takeIf { !members.any { member -> member.id==app.nodeIdHex } }
    val own=ownPin?.let { MapHost(app.nodeIdHex,app.displayName,"Android phone",it.latitude,it.longitude,
        if(share) "Map sharing on" else "Only visible here","This device",true) }
    val knownIds=members.mapTo(mutableSetOf()) { it.id }
    val saved=endpoints.filter { it.protocol==EndpointProtocol.MESHLIT_SSE && it.id.removePrefix("node-") !in knownIds }
        .map { endpoint ->
            val age=now-endpoint.lastSeenMs
            MapHost(endpoint.id.removePrefix("node-"),endpoint.name,"Saved device",null,null,
                if(endpoint.lastSeenMs==0L) "Status unknown" else if(age<=86_400_000) "Recently online" else "Offline",
                "Saved endpoint")
        }
    val estimateHost=ipEstimate?.let { MapHost("estimate-${it.ip}","IP estimate · ${it.ip}",it.place,
        it.latitude,it.longitude,"IP estimate","IMON · ${it.source}") }
    val hosts=members + listOfNotNull(own,estimateHost) + saved
    val shown=hosts.filter { host ->
        (filter=="All" || when(filter) { "On map"->host.latitude!=null && host.longitude!=null
            "Online"->host.status=="Online";"Recent"->host.status=="Recently online"
            "Hibernating"->host.status=="Hibernating"
            "Offline"->host.status=="Offline";else->true }) &&
            (query.isBlank() || "${host.name} ${host.type} ${host.status} ${host.path}".contains(query.trim(),true))
    }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding(),contentPadding=PaddingValues(16.dp),
        verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back") }
            Text("Global host map",style=MaterialTheme.typography.headlineSmall)
            Text("Pan and zoom the world map. Relay pins are self-reported; a manually requested public-IP lookup is an approximate network estimate.",style=MaterialTheme.typography.bodySmall) }
        item { WorldHostMap(shown.filter { it.latitude!=null && it.longitude!=null },onlineStreets,selfFocus)
            OutlinedButton(onClick={
                if(ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) locate()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
            },enabled=!locating,modifier=Modifier.fillMaxWidth()) { Text(if(locating) "Locating…" else "Locate this phone on map") }
            locateMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            Text("This blue position stays on this phone. Use Map privacy to share an approximate saved pin.",style=MaterialTheme.typography.bodySmall)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Switch(checked=onlineStreets,onCheckedChange={onlineStreets=it})
                Column { Text("OpenStreetMap streets",style=MaterialTheme.typography.titleSmall)
                    Text(if(onlineStreets) "Online tiles on" else "Offline world map",style=MaterialTheme.typography.bodySmall) }
            }
            Text("Online streets send the viewed map area and your IP address to OpenStreetMap's tile service. Turn them off to use the built-in map.",style=MaterialTheme.typography.bodySmall)
            Text("${shown.count { it.latitude!=null }} mapped · ${members.count { it.status=="Online" }} relay members online · ${saved.size} saved hosts with unknown current reachability",style=MaterialTheme.typography.bodySmall) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Estimate a remote public IP",style=MaterialTheme.typography.titleMedium)
            Text("Connect to your own IMON Privacy Checker WordPress API. This sends the public IP to that server and its configured geo provider only when you tap Lookup. It does not locate a person or a private Wi-Fi address.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(imonServer,{imonServer=it},Modifier.fillMaxWidth(),label={Text("IMON server HTTPS URL")},singleLine=true)
            OutlinedTextField(lookupIp,{lookupIp=it},Modifier.fillMaxWidth(),label={Text("Public IPv4 or IPv6 address")},singleLine=true)
            Button(onClick={scope.launch { lookupBusy=true;lookupMessage=null
                runCatching { ImonIpGeoLookup.lookup(imonServer,lookupIp) }
                    .onSuccess { ipEstimate=it;lookupMessage="Approximate ${it.place} · source ${it.source}" }
                    .onFailure { ipEstimate=null;lookupMessage=it.message ?: "Lookup failed" }
                lookupBusy=false
            }},enabled=!lookupBusy,modifier=Modifier.fillMaxWidth()) { Text(if(lookupBusy) "Looking up…" else "Lookup and preview on map") }
            lookupMessage?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            if(ipEstimate!=null) TextButton(onClick={ipEstimate=null;lookupMessage=null}) { Text("Clear estimate") }
            Text("An IP map point may reflect an ISP, VPN or data center. It is kept on this screen only and is never shared as a device pin.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick=onPrivacy,modifier=Modifier.fillMaxWidth()) { Text(if(share) "Map privacy · sharing on" else "Map privacy · sharing off") }
            OutlinedButton(onClick=onDevices,modifier=Modifier.fillMaxWidth()) { Text("Find and pair hosts") }
            Button(onClick=relay::refreshMembership,modifier=Modifier.fillMaxWidth(),
                enabled=relayState.enabled || relayState.joinedUrl!=null) { Text("Refresh relay hosts") }
        } }
        if(!relayState.enabled && relayState.joinedUrl==null) item {
            Text("No relay connected. Join or host a relay in Networking to see other consenting members. Saved devices remain in the list below.",style=MaterialTheme.typography.bodySmall)
        }
        item { OutlinedTextField(query,{query=it},Modifier.fillMaxWidth(),label={Text("Find host name, type or status")},singleLine=true)
            TextButton(onClick={showIdentifiers=!showIdentifiers}) { Text(if(showIdentifiers) "Hide IDs and coordinates" else "Show IDs and coordinates") }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("All","On map","Online").forEach { choice ->
                    FilterChip(filter==choice,{filter=choice},label={Text(choice)})
                }
            }
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
                listOf("Recent","Hibernating","Offline").forEach { choice ->
                    FilterChip(filter==choice,{filter=choice},label={Text(choice)})
                }
            }
        }
        item { Text("Hosts · ${shown.size}",style=MaterialTheme.typography.titleMedium) }
        if(shown.isEmpty()) item { Text("No hosts match this view. Location sharing is optional, so a host can be listed without a map pin.") }
        items(shown,key={"host:${it.id}:${it.path}"}) { host ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(host.name,style=MaterialTheme.typography.titleMedium)
                Text("${host.type} · ${host.path}",style=MaterialTheme.typography.bodySmall)
                Text(host.status,style=MaterialTheme.typography.labelMedium,color=when(host.status) {
                    "Online"->Color(0xFF21B675);"Recently online","Hibernating"->Color(0xFFCE9232);else->MaterialTheme.colorScheme.onSurfaceVariant })
                Text(if(host.latitude!=null) "Approximate pin shared" else "No location shared",style=MaterialTheme.typography.bodySmall)
                if(showIdentifiers) {
                    Text("Device ID: ${host.id}",style=MaterialTheme.typography.bodySmall)
                    if(host.latitude!=null && host.longitude!=null)
                        Text("${host.latitude}, ${host.longitude}",style=MaterialTheme.typography.bodySmall)
                }
            } }
        }
        item { Text("A relay member can report an inaccurate device type or coordinate. A saved endpoint is not proof that its device is online. No host is published to a global public directory.",style=MaterialTheme.typography.bodySmall) }
    }
}

@Composable private fun WorldHostMap(hosts:List<MapHost>,onlineStreets:Boolean,selfFocus:SelfMapFocus?) {
    val markers=remember(hosts) { JSONArray().apply {
        hosts.forEach { host -> put(JSONObject().apply {
            put("latitude", host.latitude)
            put("longitude", host.longitude)
            put("color", if(host.own) "#4b9bff" else if(host.status=="Online") "#21b675" else "#ce9232")
        }) }
    }.toString() }
    AndroidView(
        modifier=Modifier.fillMaxWidth().height(340.dp),
        factory={ context -> WebView(context).apply {
            settings.javaScriptEnabled=true
            settings.allowFileAccess=false
            settings.allowContentAccess=false
            settings.domStorageEnabled=false
            settings.setSupportZoom(false)
            settings.userAgentString="MeshlitAndroid/2.0 (+https://github.com/sabbirimon/meshlit-v2) ${settings.userAgentString}"
            webViewClient=object:WebViewClient() {
                override fun onPageFinished(view:WebView?,url:String?) {
                    super.onPageFinished(view,url)
                    (view?.tag as? Triple<*,*,*>)?.let { state ->
                        view.evaluateJavascript("window.setHosts && window.setHosts(${state.first}); window.setOnlineTiles && window.setOnlineTiles(${state.second}); ${state.third}",null)
                    }
                }
                override fun shouldInterceptRequest(view:WebView?,request:WebResourceRequest?):WebResourceResponse? {
                    val uri=request?.url ?: return blocked()
                    if(uri.scheme=="https" && uri.host=="appassets.androidplatform.net" && uri.path?.startsWith("/assets/map/")==true) {
                        val name=uri.path!!.removePrefix("/assets/map/")
                        if(name !in setOf("index.html","leaflet.js","leaflet.css","land.js")) return blocked()
                        val mime=when { name.endsWith(".js")->"application/javascript";name.endsWith(".css")->"text/css";else->"text/html" }
                        return try { WebResourceResponse(mime,"UTF-8",view!!.context.assets.open("map/$name")) }
                        catch(_:Exception) { blocked() }
                    }
                    if(uri.scheme=="https" && uri.host=="tile.openstreetmap.org" && uri.path?.matches(Regex("/[0-7]/[0-9]+/[0-9]+\\.png"))==true) return null
                    return blocked()
                }
                private fun blocked()=WebResourceResponse("text/plain","UTF-8",ByteArrayInputStream(ByteArray(0)))
            }
            loadUrl("https://appassets.androidplatform.net/assets/map/index.html")
        } },
        update={ web -> val focusScript=selfFocus?.let { "window.focusSelf && window.focusSelf(${it.latitude},${it.longitude},${it.request})" }.orEmpty()
            web.tag=Triple(markers,onlineStreets,focusScript)
            web.evaluateJavascript("window.setHosts && window.setHosts($markers); window.setOnlineTiles && window.setOnlineTiles($onlineStreets); $focusScript",null) },
        onRelease={ web -> web.stopLoading();web.destroy() },
    )
}
