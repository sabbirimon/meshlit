package com.meshlit.audit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import com.meshlit.disco.DiscoveryModeStore
import com.meshlit.disco.PeerRepository
import com.meshlit.core.cloudmcp.android.MeshlitAccessibilityService
import com.meshlit.pipeline.PipelineHost
import com.meshlit.resources.ResourceSnapshotReader
import kotlinx.serialization.json.*

/** One bounded, read-only report. Identifiers, credentials and peer addresses are excluded. */
class DeviceAuditReport(private val context:Context,private val resources:ResourceSnapshotReader,
    private val radar:PeerRepository,private val pipeline:PipelineHost,private val discovery:DiscoveryModeStore) {
    fun collect():JsonObject {
        fun granted(permission:String)=context.checkSelfPermission(permission)==PackageManager.PERMISSION_GRANTED
        val sample=resources.read()
        val peers=radar.uiState.value
        return buildJsonObject {
            put("schemaVersion",1);put("observedAtMs",sample.observedAtMs)
            put("scope","This app and Android-exposed device state; other apps' data and sensor use are unavailable")
            put("device",buildJsonObject {
                put("manufacturer",Build.MANUFACTURER.take(80));put("model",Build.MODEL.take(80));put("androidApi",Build.VERSION.SDK_INT)
                put("supportedAbis",buildJsonArray { Build.SUPPORTED_ABIS.take(4).forEach { add(it) } })
            })
            put("resources",Json.encodeToJsonElement(sample))
            put("permissions",buildJsonObject {
                put("camera",granted(Manifest.permission.CAMERA));put("microphone",granted(Manifest.permission.RECORD_AUDIO))
                put("fineLocation",granted(Manifest.permission.ACCESS_FINE_LOCATION))
                put("notifications",NotificationManagerCompat.from(context).areNotificationsEnabled())
                put("accessibilityServiceBound",MeshlitAccessibilityService.instance!=null)
            })
            put("mesh",buildJsonObject {
                put("discoveryMode",discovery.mode.value.name);put("scanning",peers.scanning)
                put("unverifiedAdvertisements",peers.grouped.values.flatten().distinctBy{it.advertisement.nodeId}.size)
                put("savedWorkerEndpoints",pipeline.peers().size)
                put("coordinatorActive",pipeline.status.value.coordinator);put("workerActive",pipeline.status.value.worker)
                put("physicalDistanceKnown",false)
            })
            put("limitations",buildJsonArray {
                add("No full-system app inventory, call/message content, payment state or passkey status is read")
                add("A saved peer endpoint is not proof that it is online or approved for an operation")
                add("Sensor availability is not evidence of active recording")
            })
        }
    }
}
