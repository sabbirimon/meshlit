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
import androidx.compose.ui.geometry.CornerRadius
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
@Composable internal fun RadarScreen(onBack:()->Unit,onScan:()->Unit,onChat:()->Unit,onLogs:()->Unit,onMap:()->Unit,
    onDiscovered:(String)->Unit) {
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
        .filter { it.advertisement.nodeId != app.nodeIdHex && it.advertisement.transport in setOf("nsd","ble","lan") }
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
    }
    val markerNames=visible.map { it.advertisement.displayName.ifBlank { "Nearby Meshlit device" } }
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("radar-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
        item { TextButton(onClick=onBack) { Text("Back to settings") };Text("Device radar",style=MaterialTheme.typography.headlineSmall)
            Text("Live Meshlit discoveries only. Saved devices are listed separately below. Rings do not measure physical distance.",style=MaterialTheme.typography.bodyMedium) }
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
        item { RadarTopology(markers,state.scanning,animateScan,markerNames) {
            selectedMarker=it
            visible.getOrNull(it)?.advertisement?.nodeId?.let(onDiscovered)
        }
            markerNames.getOrNull(selectedMarker)?.let { name -> Text("Selected: $name · review its entry below before connecting",style=MaterialTheme.typography.bodyMedium) }
            Text("${visible.size} discovered now · ${savedOnly.size} saved devices · ${workers.size} saved layer workers · ${if(mode==DiscoveryMode.OFF) "discovery off in Privacy" else if(state.scanning) "scanning" else "scan paused"}.",style=MaterialTheme.typography.bodySmall)
            Text("Green: locally approved with a matching saved key · red: unverified · blue ring: cluster or Internet route.",style=MaterialTheme.typography.bodySmall)
            Text("Saved entries do not prove a device is online and do not appear as radar dots.",style=MaterialTheme.typography.bodySmall) }
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
                OutlinedButton(onClick={onDiscovered(peer.nodeId)},modifier=Modifier.fillMaxWidth()) {
                    Text(if(saved==null) "Add and review identity" else "Open saved device")
                }
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

internal data class RadarMarker(val category:Int,val known:Boolean,val remote:Boolean,val savedOnly:Boolean,
    val bearingDegrees:Float?=null,val rangeFraction:Float?=null)
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
            val hit=markers.indices.map { index ->
                val point=radarMarkerPoint(markers,index,size.width.toFloat(),size.height.toFloat())
                index to (tap-point).getDistance()
            }.minByOrNull { it.second }
            if(hit!=null && hit.second<=22.dp.toPx()) onMarkerTap(hit.first)
        }
    }) {
        val center=Offset(size.width/2,size.height*0.46f)
        val radius=minOf(size.width,size.height)*0.44f
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
        if(markers.any { it.bearingDegrees==null || it.rangeFraction==null }) {
            // A labeled inset keeps unlocated devices within the radar while
            // making clear that their grid coordinates are not bearings or ranges.
            drawRoundRect(Color(0xFF172027).copy(alpha=0.95f),
                topLeft=Offset(center.x-radius*0.5f,size.height*0.56f),
                size=Size(radius,size.height*0.28f),cornerRadius=CornerRadius(10.dp.toPx()))
            val insetLabel=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                color=android.graphics.Color.LTGRAY;textSize=10.dp.toPx()
                textAlign=android.graphics.Paint.Align.CENTER
            }
            drawContext.canvas.nativeCanvas.drawText("POSITION UNKNOWN",center.x,size.height*0.61f,insetLabel)
        }
        markers.forEachIndexed { index,marker ->
            val point=radarMarkerPoint(markers,index,size.width,size.height)
            if(point.x<0f) return@forEachIndexed
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
            val located=marker.bearingDegrees!=null && marker.rangeFraction!=null
            val shortName=if(located) names.getOrNull(index)?.substringAfterLast('/')?.take(9).orEmpty() else ""
            val label="#${index+1} $shortName".trim()
            val paint=android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                this.color=android.graphics.Color.WHITE;textSize=11.dp.toPx()
            }
            val labelX=(point.x+9.dp.toPx()).coerceAtMost(size.width-paint.measureText(label)-3.dp.toPx())
            drawContext.canvas.nativeCanvas.drawText(label,labelX,point.y-9.dp.toPx(),paint)
        }
    }
    Text(if(scanning) "Center: this device · numbered dots in the inset have no measured position"
        else "Scan paused · enable discovery to see the moving sweep",style=MaterialTheme.typography.labelSmall)
}

private fun radarMarkerPoint(markers:List<RadarMarker>,index:Int,width:Float,height:Float):Offset {
    val marker=markers[index]
    val bearing=marker.bearingDegrees
    val range=marker.rangeFraction
    if(bearing!=null && range!=null && bearing.isFinite() && range.isFinite()) {
        val radius=minOf(width,height)*0.44f*range.coerceIn(0.08f,1f)
        val angle=Math.toRadians((bearing-90f).toDouble())
        return Offset(width/2f+cos(angle).toFloat()*radius,height*0.46f+sin(angle).toFloat()*radius)
    }
    fun unlocated(item:RadarMarker)=item.bearingDegrees?.isFinite()!=true || item.rangeFraction?.isFinite()!=true
    val unknownIndex=markers.take(index).count(::unlocated)
    if(unknownIndex>=16) return Offset(-1000f,-1000f)
    val columns=minOf(4,markers.count(::unlocated)).coerceAtLeast(1)
    val col=unknownIndex%columns
    return Offset(width/2f+(col-(columns-1)/2f)*minOf(width,height)*0.12f,
        height*(0.70f+(unknownIndex/columns)*0.04f))
}
