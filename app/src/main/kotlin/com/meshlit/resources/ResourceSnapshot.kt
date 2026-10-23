package com.meshlit.resources

import android.app.ActivityManager
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.models.ModelLibrary
import com.meshlit.pipeline.PipelineHost
import com.meshlit.power.PowerReading
import com.meshlit.power.PowerRepository
import kotlinx.serialization.Serializable

@Serializable data class AvailableSensor(val type: Int, val name: String, val vendor: String)
@Serializable data class ResourceSnapshot(
    val observedAtMs: Long, val availableRamBytes: Long, val totalRamBytes: Long,
    val freeAppStorageBytes: Long, val cpuCores: Int, val power: PowerReading,
    val modelLoaded: Boolean, val generating: Boolean, val activeModelTransfers: Int,
    val pipelineWorker: Boolean, val pipelineCoordinator: Boolean,
    val availableSensors: List<AvailableSensor>,
    val sensorUseStatus: String = "Android does not expose other apps' live sensor use to Meshlit; availability is not active use."
)

/** One read-only observation shared by the user screen and agent resource_status tool. */
class ResourceSnapshotReader(private val context: Context, private val inference: InferenceCoordinator,
    private val library: ModelLibrary, private val pipeline: PipelineHost) {
    fun read(): ResourceSnapshot {
        val memory = ActivityManager.MemoryInfo()
        context.getSystemService(ActivityManager::class.java).getMemoryInfo(memory)
        val sensors = context.getSystemService(SensorManager::class.java)
            .getSensorList(Sensor.TYPE_ALL).distinctBy { it.type to it.name }
            .take(128).map { AvailableSensor(it.type, it.name.take(100), it.vendor.take(100)) }
        val status = pipeline.status.value
        return ResourceSnapshot(System.currentTimeMillis(), memory.availMem, memory.totalMem,
            library.freeStorage(), Runtime.getRuntime().availableProcessors(), PowerRepository(context).reading(),
            inference.loadedModel() != null, inference.state.value is CoordinatorState.Generating,
            library.models.value.count { it.active }, status.worker, status.coordinator, sensors)
    }
}
