package com.meshlit.ui.modern

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.core.users.UserStore
import com.meshlit.core.common.EndpointProtocol
import com.meshlit.di.koinInject
import com.meshlit.disco.PeerRepository
import com.meshlit.disco.PeerCategory
import com.meshlit.disco.DiscoveryMode
import com.meshlit.disco.DiscoveryModeStore
import com.meshlit.pipeline.PipelineHost
import com.meshlit.peering.DeviceIdentity
import com.meshlit.core.trust.PeerCrypto
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.cos
import kotlin.math.sin

/** A network topology view. Rings are connection classes, never metres or GPS positions. */
@Composable internal fun RadarScreen(onBack:()->Unit,onScan:()->Unit,onChat:()->Unit,onLogs:()->Unit,onMap:()->Unit) {
    val app=androidx.compose.ui.platform.LocalContext.current.applicationContext as MeshlitApplication
    val repo=koinInject<PeerRepository>()
    val discoveryMode=koinInject<DiscoveryModeStore>()
    val mode by discoveryMode.mode.collectAsStateWithLifecycle()
    val pipeline=koinInject<PipelineHost>()
    val userStore=koinInject<UserStore>()
    val identity=koinInject<DeviceIdentity>()
    val settings=koinInject<SettingsRepository>()
    val state by repo.uiState.collectAsStateWithLifecycle()
    val endpoints by settings.remoteEndpointsFlow.collectAsStateWithLifecycle(initialValue=emptyList())
    val pipelineStatus by pipeline.status.collectAsStateWithLifecycle()
    val animateScan=LocalMeshlitThemeConfig.current.animationsEnabled
    var showIdentifiers by rememberSaveable { mutableStateOf(false) }
    var selectedMarker by remember { mutableIntStateOf(-1) }
    var fingerprint by remember { mutableStateOf("Loading…") }
    LaunchedEffect(Unit) { app.refreshDiscovery();repo.refreshAvailability() }
    LaunchedEffect(app.nodeIdHex) {
        if (app.nodeIdHex.isNotBlank()) fingerprint=withContext(Dispatchers.IO) {
            PeerCrypto.fingerprint(identity.get(app.nodeIdHex).publicKey)
        }
    }
    val visible=state.grouped.values.flatten().distinctBy { it.advertisement.nodeId }
        .filter { it.advertisement.nodeId != app.nodeIdHex }
    val liveIds=visible.mapTo(mutableSetOf()) { it.advertisement.nodeId }
    val savedOnly=endpoints.filter { it.protocol==EndpointProtocol.MESHLIT_SSE && it.id.removePrefix("node-") !in liveIds }
    val workers=remember(state,pipelineStatus) { pipeline.peers() }
    val markers=visible.map { discovered ->
        val peer=discovered.advertisement
        val saved=endpoints.firstOrNull { it.id=="node-${peer.nodeId}" }
        val key=saved?.notes?.substringAfter("identity.key=", "")?.substringBefore('\n').orEmpty()
        RadarMarker(discovered.category.ordinal,
            known=saved?.trusted==true && key.isNotBlank() && PeerCrypto.fingerprint(key)==peer.fingerprint,
            remote=discovered.category==PeerCategory.CLUSTER || discovered.category==PeerCategory.INTERNET,
            savedOnly=false)
    } + savedOnly.map { RadarMarker(2,it.trusted,false,true) } +
        workers.map { RadarMarker(1,true,true,true) }
    val markerNames=visible.map { it.advertisement.displayName.ifBlank { "Nearby Meshlit device" } } +
        savedOnly.map { it.name } + workers.map { "Worker · ${it.group}" }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("radar-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back to settings") };Text("Device radar",style=MaterialTheme.typography.headlineSmall)
            Text("Live and saved Meshlit devices by connection type. Rings do not measure physical distance.",style=MaterialTheme.typography.bodyMedium) }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            TextButton(onClick={showIdentifiers=!showIdentifiers},modifier=Modifier.fillMaxWidth()) {
                Icon(if(showIdentifiers) Icons.Default.VisibilityOff else Icons.Default.Visibility,null)
                Spacer(Modifier.width(8.dp))
                Text(if(showIdentifiers) "Hide identifiers" else "Show identifiers")
            }
            if(showIdentifiers) {
                Text("Local user ID: ${userStore.current().id}")
                Text("Device node ID: ${app.nodeIdHex.ifBlank { "Loading…" }}")
                Text("Device key fingerprint: $fingerprint")
                Text("Identifiers are local records, not proof of ownership. Verify the other device's fingerprint before approving it.",style=MaterialTheme.typography.bodySmall)
            }
        } } }
        item { RadarTopology(markers,state.scanning,animateScan,markerNames) { selectedMarker=it }
            markerNames.getOrNull(selectedMarker)?.let { name -> Text("Selected: $name · review its entry below before connecting",style=MaterialTheme.typography.bodyMedium) }
            Text("${visible.size} discovered now · ${savedOnly.size} saved devices · ${workers.size} saved layer workers · ${if(mode==DiscoveryMode.OFF) "discovery off in Privacy" else if(state.scanning) "scanning" else "scan paused"}.",style=MaterialTheme.typography.bodySmall)
            Text("Green: locally approved (live dots also match the saved key) · red: unverified · blue ring: cluster or Internet route · hollow: saved, not seen live.",style=MaterialTheme.typography.bodySmall)
            Text("Saved entries do not prove a device is online.",style=MaterialTheme.typography.bodySmall) }
        if(markers.isNotEmpty()) item {
            Text("Radar device table",style=MaterialTheme.typography.titleSmall)
            markerNames.forEachIndexed { index,name ->
                val id=when {
                    index<visible.size -> visible[index].advertisement.nodeId
                    index<visible.size+savedOnly.size -> savedOnly[index-visible.size].id.removePrefix("node-")
                    else -> workers[index-visible.size-savedOnly.size].fingerprint
                }
                val detail=when {
                    index<visible.size -> {
                        val peer=visible[index]
                        "${peer.category.displayName} · ${peer.advertisement.transport} · ${peer.advertisement.host}:${peer.advertisement.port}"
                    }
                    index<visible.size+savedOnly.size -> "Saved device · reachability unknown"
                    else -> "Saved worker · reachability unknown"
                }
                Card(Modifier.fillMaxWidth().padding(vertical=3.dp)) { Column(Modifier.padding(10.dp)) {
                    Text("#${index+1}  $name",style=MaterialTheme.typography.titleSmall)
                    Text("ID ${id.take(12)}… · $detail",style=MaterialTheme.typography.bodySmall)
                } }
            }
            Text("CPU, memory, load and battery are unknown from discovery alone. Open a verified device session for live capability and resource metrics. Show identifiers above for full IDs.",style=MaterialTheme.typography.labelSmall)
        }
        item { Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Button(onClick={app.refreshDiscovery();repo.refreshAvailability()},modifier=Modifier.fillMaxWidth(),enabled=mode!=DiscoveryMode.OFF) { Text("Refresh discovery") }
            OutlinedButton(onClick=onScan,modifier=Modifier.fillMaxWidth()) { Text("Open device scan") }
            OutlinedButton(onClick=onMap,modifier=Modifier.fillMaxWidth()) { Text("Global host map") }
            OutlinedButton(onClick=onChat,modifier=Modifier.fillMaxWidth()) { Text("Peer chat") }
            TextButton(onClick=onLogs,modifier=Modifier.fillMaxWidth()) { Text("Logs") }
        }; Text("Use the Search icon above for global search across settings, chats, saved devices, models and articles. Peer chat needs its own verified, connected session; discovery does not join a chat.",style=MaterialTheme.typography.bodySmall) }
        item { Text("Discovered now",style=MaterialTheme.typography.titleMedium) }
        if (visible.isEmpty()) item { Text(when {
            mode==DiscoveryMode.OFF -> "Discovery is off. Enable Auto or On in Security & privacy to look for nearby devices."
            state.scanning -> "No live advertisements yet. Check both apps are open and the network allows local discovery."
            else -> "Discovery is paused. Refresh it, or review saved devices below."
        }) }
        items(visible,key={it.advertisement.nodeId}) { item ->
            val peer=item.advertisement
            val saved=endpoints.firstOrNull { it.id=="node-${peer.nodeId}" }
            val key=saved?.notes?.substringAfter("identity.key=", "")?.substringBefore('\n').orEmpty()
            val known=saved?.trusted==true && key.isNotBlank() && PeerCrypto.fingerprint(key)==peer.fingerprint
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text(peer.displayName.ifBlank { "Nearby Meshlit device" },style=MaterialTheme.typography.titleSmall)
                RadarStatusDot(if(known) "Known and approved" else "Unverified discovery",known,
                    item.category==PeerCategory.CLUSTER || item.category==PeerCategory.INTERNET)
                Text("${item.category.displayName} · ${peer.transport} · ${listOf(peer.manufacturer,peer.model).filter(String::isNotBlank).joinToString(" ")}")
                if(showIdentifiers) {
                    Text("Node ID: ${peer.nodeId}",style=MaterialTheme.typography.bodySmall)
                    Text("Address: ${peer.host}:${peer.port}",style=MaterialTheme.typography.bodySmall)
                    Text("Claimed fingerprint: ${peer.fingerprint}",style=MaterialTheme.typography.bodySmall)
                }
                Text("Distance unknown · advertisement is not verified identity or live compute capacity",style=MaterialTheme.typography.bodySmall)
            } }
        }
        item { Text("Saved devices",style=MaterialTheme.typography.titleMedium) }
        if(savedOnly.isEmpty()) item { Text("No other saved device endpoints.") }
        items(savedOnly,key={"saved:${it.id}"}) { endpoint ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(endpoint.name,style=MaterialTheme.typography.titleSmall)
                RadarStatusDot(if(endpoint.trusted) "Approved · not seen live" else "Saved · unverified",endpoint.trusted,false)
                Text(if(endpoint.trusted) "Approved on this phone · current reachability unknown" else "Saved only · identity or approval pending",style=MaterialTheme.typography.bodySmall)
                if(showIdentifiers) {
                    Text("Node ID: ${endpoint.id.removePrefix("node-")}",style=MaterialTheme.typography.bodySmall)
                    Text("Endpoint: ${endpoint.baseUrl}",style=MaterialTheme.typography.bodySmall)
                }
            } }
        }
        item { Text("Saved model workers",style=MaterialTheme.typography.titleMedium) }
        if (workers.isEmpty()) item { Text("No approved layer workers saved.") }
        items(workers,key={"${it.host}:${it.port}"}) { worker ->
            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                Text("Worker · ${worker.group}")
                RadarStatusDot("Approved worker · reachability unknown",true,true)
                if(showIdentifiers) {
                    Text("${worker.host}:${worker.port}",style=MaterialTheme.typography.bodySmall)
                    Text("Pinned TLS fingerprint: ${worker.fingerprint}",style=MaterialTheme.typography.bodySmall)
                }
                Text("Saved approval · reachability and resources need a fresh assessment",style=MaterialTheme.typography.bodySmall)
            } }
        }
    }
}

internal data class RadarMarker(val category:Int,val known:Boolean,val remote:Boolean,val savedOnly:Boolean)
private val knownGreen=Color(0xFF20B979)
private val unknownRed=Color(0xFFE15B5B)
private val remoteBlue=Color(0xFF4B9BFF)

@Composable private fun RadarStatusDot(label:String,known:Boolean,remote:Boolean) {
    Row(verticalAlignment=androidx.compose.ui.Alignment.CenterVertically,
        horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Canvas(Modifier.size(14.dp)) {
            drawCircle(if(known) knownGreen else unknownRed,radius=4.dp.toPx())
            if(remote) drawCircle(remoteBlue,radius=6.5.dp.toPx(),style=Stroke(2.dp.toPx()))
        }
        Text(label,style=MaterialTheme.typography.bodySmall)
    }
}

@Composable internal fun RadarTopology(markers:List<RadarMarker>,scanning:Boolean,animateScan:Boolean,names:List<String> = emptyList(),onMarkerTap:(Int)->Unit={}) {
    val primary=MaterialTheme.colorScheme.primary
    val muted=MaterialTheme.colorScheme.outline
    val transition=rememberInfiniteTransition(label="radar-sweep")
    val sweep by transition.animateFloat(initialValue=0f,targetValue=360f,
        animationSpec=infiniteRepeatable(tween(3000,easing=LinearEasing),RepeatMode.Restart),label="radar-angle")
    val ping by transition.animateFloat(initialValue=0f,targetValue=1f,
        animationSpec=infiniteRepeatable(tween(1800,easing=LinearEasing),RepeatMode.Restart),label="radar-ping")
    Canvas(Modifier.fillMaxWidth().height(300.dp).testTag("radar-topology").pointerInput(markers) {
        detectTapGestures { tap ->
            val center=Offset(size.width/2f,size.height/2f)
            val radius=minOf(size.width,size.height)*0.47f
            val hit=markers.indices.map { index ->
                val fraction=when(markers[index].category) { 0->0.3f;1->0.5f;2->0.7f;else->0.95f }
                val angle=index*2.399963f
                val point=Offset(center.x+cos(angle)*radius*fraction,center.y+sin(angle)*radius*fraction)
                index to (tap-point).getDistance()
            }.minByOrNull { it.second }
            if(hit!=null && hit.second<=22.dp.toPx()) onMarkerTap(hit.first)
        }
    }) {
        val center=Offset(size.width/2,size.height/2)
        val radius=minOf(size.width,size.height)*0.47f
        listOf(0.33f,0.65f,1f).forEach { fraction -> drawCircle(muted.copy(alpha=0.5f),radius*fraction,center,style=Stroke(2.dp.toPx())) }
        if(scanning && animateScan) {
            drawCircle(primary.copy(alpha=0.24f*(1f-ping)),radius*(0.08f+0.92f*ping),center,
                style=Stroke(2.dp.toPx()))
            // Draw a stepped alpha wedge so the sweep remains legible on dark
            // and light palettes without implying physical bearing or range.
            repeat(18) { step ->
                drawArc(primary.copy(alpha=0.025f+step*0.017f),sweep-63f+step*3.5f,3.7f,true,
                    topLeft=Offset(center.x-radius,center.y-radius),size=Size(radius*2,radius*2))
            }
            val radians=Math.toRadians(sweep.toDouble())
            drawLine(primary,center,
                Offset(center.x+cos(radians).toFloat()*radius,center.y+sin(radians).toFloat()*radius),2.5.dp.toPx())
        }
        drawCircle(primary.copy(alpha=0.22f),12.dp.toPx(),center)
        drawCircle(primary,6.dp.toPx(),center)
        markers.forEachIndexed { index,marker ->
            val fraction=when(marker.category) { 0->0.3f;1->0.5f;2->0.7f;else->0.95f }
            val angle=index*2.399963f
            val point=androidx.compose.ui.geometry.Offset(center.x+cos(angle)*radius*fraction,center.y+sin(angle)*radius*fraction)
            val color=if(marker.known) knownGreen else unknownRed
            if(!marker.savedOnly && animateScan) {
                drawCircle(color.copy(alpha=0.55f*(1f-ping)),(6f+17f*ping).dp.toPx(),point,
                    style=Stroke(1.5.dp.toPx()))
                val second=(ping+0.5f)%1f
                drawCircle(color.copy(alpha=0.3f*(1f-second)),(6f+17f*second).dp.toPx(),point,
                    style=Stroke(1.dp.toPx()))
            }
            if(marker.savedOnly) drawCircle(color,4.dp.toPx(),point,style=Stroke(1.5.dp.toPx()))
            else drawCircle(color,4.dp.toPx(),point)
            if(marker.remote) drawCircle(remoteBlue,7.dp.toPx(),point,style=Stroke(1.5.dp.toPx()))
            val shortName=names.getOrNull(index)?.substringAfterLast('/')?.take(9).orEmpty()
            val label="#${index+1} $shortName"
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color=android.graphics.Color.WHITE;textSize=11.dp.toPx()
            }
            val labelX=(point.x+9.dp.toPx()).coerceAtMost(size.width-paint.measureText(label)-3.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label,labelX,point.y-9.dp.toPx(),paint)
        }
    }
    Text(if(scanning) "Center: this device · filled: discovered · hollow: saved · sweep: active scan"
        else "Scan paused · enable discovery to see the moving sweep",style=MaterialTheme.typography.labelSmall)
}
