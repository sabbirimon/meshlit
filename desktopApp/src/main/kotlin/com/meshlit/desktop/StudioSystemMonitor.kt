package com.meshlit.desktop

import com.meshlit.core.inference.CpuWorkGate
import com.meshlit.core.inference.models.AdaptiveCpuPolicy
import com.meshlit.core.inference.models.CpuConditions
import com.meshlit.core.inference.models.CpuMode as WorkloadMode
import com.meshlit.core.inference.models.CpuOverrides
import kotlinx.serialization.json.*
import java.lang.management.ManagementFactory
import java.util.prefs.Preferences
import oshi.SystemInfo

/** Private, local readings for the workbench; no external processes may be terminated through this API. */
internal class StudioSystemMonitor(private val engine:LocalEngine,private val engineOptions:EnginePreferences) {
    private val reader=ResourceReader()
    private val info=SystemInfo()
    private val recovery=com.meshlit.core.inference.models.CpuRecoveryWindow()
    private val prefs=Preferences.userRoot().node("com/meshlit/desktop-preview/workload")
    @Synchronized fun decision():com.meshlit.core.inference.models.CpuDecision {
        val memory=info.hardware.memory
        val sources=runCatching{info.hardware.powerSources}.getOrDefault(emptyList())
        val battery=sources.map{it.remainingCapacityPercent}.filter{it.isFinite() && it in 0.0..1.0}.minOrNull()?.let{(it*100).toInt()}
        val charging=sources.takeIf{it.isNotEmpty()}?.any{it.isCharging || it.isPowerOnLine}
        val lowMemory=memory.available<maxOf(512L*1024*1024,memory.total/10)
        val mode=WorkloadMode.valueOf(prefs.get("mode","BALANCED"))
        val limit=when(mode){WorkloadMode.BATTERY_SAVER->1;WorkloadMode.BALANCED->4;WorkloadMode.PERFORMANCE->6;else->128}
        val target=AdaptiveCpuPolicy.decide(CpuConditions(Runtime.getRuntime().availableProcessors(),null,null,battery,charging,lowMemory,true,unknownAsPressure=false),
            mode,limit,CpuOverrides(
                heat=false,saver=false,battery=prefs.getBoolean("ignoreBattery",false),memory=prefs.getBoolean("ignoreMemory",false)))
        val stable=recovery.apply(target.threads,System.nanoTime()/1_000_000)
        return target.copy(threads=stable,reason=target.reason+if(stable<target.threads)"; recovery cooldown" else "")
    }
    @Synchronized fun apply(options:EngineOptions):EngineOptions = if(!prefs.getBoolean("enabled",false))options else decision().let{options.copy(threads=it.threads,batchThreads=it.threads)}
    @Synchronized fun needsReload()=engine.isRunning() && engine.appliedCpuThreads!=apply(engineOptions.load()).threads
    @Synchronized fun policy():JsonObject=buildJsonObject{
        val d=decision();put("enabled",prefs.getBoolean("enabled",false));put("mode",prefs.get("mode","BALANCED"))
        put("threads",d.threads);put("headroom",d.headroom);put("reason",d.reason)
        put("savedThreads",engineOptions.load().threads);put("cores",Runtime.getRuntime().availableProcessors())
        put("ignoreBattery",prefs.getBoolean("ignoreBattery",false));put("ignoreMemory",prefs.getBoolean("ignoreMemory",false))
        put("thermalPressureAvailable",false);put("powerSaverAvailable",false);put("appliesOnReload",true);put("appliedThreadHint",engine.appliedCpuThreads?.let(::JsonPrimitive) ?: JsonNull)
    }
    @Synchronized fun save(request:JsonObject):JsonObject {
        require(request.keys.all{it in setOf("id","op","mode","enabled","ignoreBattery","ignoreMemory")})
        val mode=WorkloadMode.valueOf(request.getValue("mode").jsonPrimitive.content)
        val enabled=request.getValue("enabled").jsonPrimitive.boolean
        prefs.put("mode",mode.name);prefs.putBoolean("enabled",enabled)
        prefs.putBoolean("ignoreBattery",request["ignoreBattery"]?.jsonPrimitive?.boolean ?: false)
        prefs.putBoolean("ignoreMemory",request["ignoreMemory"]?.jsonPrimitive?.boolean ?: false);prefs.flush();recovery.reset()
        return policy()
    }
    @Synchronized fun sample():JsonObject {
        val reading=reader.sample();val own=reading.processes.firstOrNull{it.pid.toLong()==ProcessHandle.current().pid()}
        val native=reading.processes.firstOrNull{it.pid.toLong()==engine.processId()}
        val heap=ManagementFactory.getMemoryMXBean().heapMemoryUsage
        return buildJsonObject {
            put("observedAtMs",reading.at.toEpochMilli());put("cpuPercent",reading.cpu?.let{JsonPrimitive(it*100)} ?: JsonNull)
            put("ramTotalBytes",reading.total);put("ramAvailableBytes",reading.available);put("heapUsedBytes",heap.used)
            put("backendRssBytes",own?.rss?.let(::JsonPrimitive) ?: JsonNull);put("modelRssBytes",native?.rss?.let(::JsonPrimitive) ?: JsonNull)
            put("processCount",reading.processCount);put("cores",Runtime.getRuntime().availableProcessors());put("host",System.getProperty("os.name")+" · "+System.getProperty("os.arch"))
            put("activeWork",CpuWorkGate.active?.let(::JsonPrimitive) ?: JsonNull);put("queuedWork",CpuWorkGate.queued)
            put("batteries",JsonArray(reading.batteries.map(::JsonPrimitive)));put("gpus",JsonArray(reading.gpus.map(::JsonPrimitive)))
            put("processes",buildJsonArray{reading.processes.sortedByDescending{it.rss}.take(12).forEach{row->add(buildJsonObject{
                put("pid",row.pid);put("name",row.name);put("rssBytes",row.rss);put("cpuPercent",row.cpu?.let{JsonPrimitive(it*100)} ?: JsonNull)
            })}})
            put("policy",policy());put("modelLoaded",engine.isRunning())
        }
    }
}
