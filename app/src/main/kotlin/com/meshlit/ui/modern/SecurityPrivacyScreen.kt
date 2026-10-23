package com.meshlit.ui.modern

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.meshlit.MeshlitApplication
import com.meshlit.di.koinInject
import com.meshlit.disco.DiscoveryMode
import com.meshlit.disco.DiscoveryModeStore
import com.meshlit.settings.SettingsRepository
import com.meshlit.peering.HostLocationStore
import com.meshlit.peering.RelayManager
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/** Direct links to the controls that enforce each privacy and security boundary. */
@Composable internal fun SecurityPrivacyScreen(onBack:()->Unit,onOpen:(String)->Unit) {
    val discovery=koinInject<DiscoveryModeStore>()
    val mode by discovery.mode.collectAsStateWithLifecycle()
    val agentAllowed by discovery.agentAllowed.collectAsStateWithLifecycle()
    val settings=koinInject<SettingsRepository>()
    val privateName by settings.privateContributorNameFlow.collectAsStateWithLifecycle(initialValue=false)
    val hostLocation=koinInject<HostLocationStore>()
    val shareLocation by hostLocation.enabled.collectAsStateWithLifecycle()
    val pin by hostLocation.pin.collectAsStateWithLifecycle()
    val relay=koinInject<RelayManager>()
    val scope=rememberCoroutineScope()
    val app=LocalContext.current.applicationContext as MeshlitApplication
    var latitude by rememberSaveable { mutableStateOf("") }
    var longitude by rememberSaveable { mutableStateOf("") }
    var mapError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pin) {
        pin?.let { latitude=it.latitude.toString();longitude=it.longitude.toString() }
    }
    val locationPermission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if(granted.values.any { it }) scope.launch {
            runCatching {
                val fix=LocationServices.getFusedLocationProviderClient(app).lastLocation.await()
                    ?: error("No recent location fix. Enter an approximate position manually.")
                require(System.currentTimeMillis()-fix.time in 0L..1_800_000L) {
                    "The saved location is older than 30 minutes. Enter an approximate position manually."
                }
                latitude=fix.latitude.toString();longitude=fix.longitude.toString()
                mapError="Location filled in. Save the pin to use it."
            }.onFailure { mapError=it.message ?: "Could not read location" }
        } else mapError="Location permission was not granted. You can enter a position manually."
    }
    val entries=listOf(
        Triple("permissions","Android permissions","Review camera, microphone, nearby devices and Accessibility grants"),
        Triple("guardrails","Local behavior and guardrails","Control optional local instructions and Gateway term rules"),
        Triple("operations","Operation switches","Stop managed features and review agent access"),
        Triple("network","Network and pairing","Approve devices, review transport and worker access"),
        Triple("host-map","Global host map","See approximate pins shared by consenting relay members"),
        Triple("identity-recovery","Device identity backup","Back up the signing identity used for device verification"),
        Triple("audit","Audit and telemetry","Review local records, exports and opt-in collector settings"),
        Triple("logs","Logs and export","Search redacted diagnostics and export selected entries"),
        Triple("legal","Terms and privacy","Read the offline privacy policy and accepted agreement")
    )
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("security-privacy-screen"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item { TextButton(onClick=onBack) { Text("Back to settings") };Text("Security & privacy",style=MaterialTheme.typography.headlineSmall)
            Text("Choose each feature's actual permission and access controls. Device discovery, peer chat, audio and agent commands require their own separate grants. The app does not infer trust from a nearby signal or shared network.") }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Mesh device discovery",style=MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) { DiscoveryMode.entries.forEach { value ->
                FilterChip(mode==value,onClick={discovery.saveHuman(value);app.refreshDiscovery()},label={Text(value.name)})
            } }
            Text("Default AUTO scans while Meshlit is visible. ON keeps scanning while its process runs. OFF stops discovery. Android nearby-device permissions still apply; this does not pair or approve a device.",style=MaterialTheme.typography.bodySmall)
            Row { Text("Allow agents to change discovery mode",Modifier.weight(1f));Switch(agentAllowed,{discovery.allowAgentHuman(it)}) }
            Text("An agent can change only this discovery mode after this grant; it cannot grant Android permissions, pair devices or identify a person from an advertisement.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Private contributor name",style=MaterialTheme.typography.titleMedium)
            Row { Text("Hide my device name from nearby peers",Modifier.weight(1f));Switch(privateName,{ enabled -> scope.launch { settings.setPrivateContributorName(enabled);app.refreshDiscovery() } }) }
            Text("Nearby peers see ‘Private contributor’ instead of your chosen name or hardware model. Pairing still reveals this device's public key, network address and stable device ID; this is a pseudonym, not full anonymity. Signed contribution records remain tied to that ID to prevent fake rewards.",style=MaterialTheme.typography.bodySmall)
        } } }
        item { Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text("Host map visibility",style=MaterialTheme.typography.titleMedium)
            Row { Text("Share my approximate map pin with joined relay members",Modifier.weight(1f))
                Switch(shareLocation,{ enabled -> runCatching { hostLocation.setEnabled(enabled);relay.refreshMembership();mapError=null }
                    .onFailure { mapError=it.message } }) }
            Text("Off by default. Only members of a relay you join or host can see the pin. Coordinates are rounded to about 1 km before sharing. Other members report their own positions; Meshlit cannot verify them.",style=MaterialTheme.typography.bodySmall)
            OutlinedTextField(latitude,{latitude=it},Modifier.fillMaxWidth(),label={Text("Latitude (-90 to 90)")},singleLine=true)
            OutlinedTextField(longitude,{longitude=it},Modifier.fillMaxWidth(),label={Text("Longitude (-180 to 180)")},singleLine=true)
            OutlinedButton(onClick={
                if(ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(app,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED)
                    scope.launch { runCatching {
                        val fix=LocationServices.getFusedLocationProviderClient(app).lastLocation.await()
                            ?: error("No recent location fix. Enter an approximate position manually.")
                        require(System.currentTimeMillis()-fix.time in 0L..1_800_000L) {
                            "The saved location is older than 30 minutes. Enter an approximate position manually."
                        }
                        latitude=fix.latitude.toString();longitude=fix.longitude.toString()
                        mapError="Location filled in. Save the pin to use it."
                    }.onFailure { mapError=it.message } }
                else locationPermission.launch(arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION,Manifest.permission.ACCESS_FINE_LOCATION))
            },modifier=Modifier.fillMaxWidth()) { Text("Use recent device location") }
            Button(onClick={runCatching { hostLocation.setPin(latitude.trim().toDouble(),longitude.trim().toDouble());relay.refreshMembership();mapError="Map pin saved" }
                .onFailure { mapError=it.message ?: "Invalid coordinates" }},modifier=Modifier.fillMaxWidth()) { Text("Save map pin") }
            TextButton(onClick={runCatching { hostLocation.clearPin();relay.refreshMembership();latitude="";longitude="";mapError="Map pin removed" }
                .onFailure { mapError=it.message }},modifier=Modifier.fillMaxWidth()) { Text("Remove map pin") }
            mapError?.let { Text(it,style=MaterialTheme.typography.bodySmall) }
            Text("Location sharing controls the map only. It does not grant pairing, compute, or remote control.",style=MaterialTheme.typography.bodySmall)
        } } }
        entries.filter { com.meshlit.BuildProfile.routeAllowed(it.first) }.forEach { (id,title,description) ->
            item { Card(onClick={onOpen(id)},modifier=Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                Text(title,style=MaterialTheme.typography.titleMedium);Text(description,style=MaterialTheme.typography.bodySmall)
            } } }
        }
    }
}
