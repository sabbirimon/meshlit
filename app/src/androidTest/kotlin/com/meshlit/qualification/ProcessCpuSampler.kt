package com.meshlit.qualification
import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File
import kotlinx.serialization.json.*
/** Observed app-process CPU time. Child processes and exclusive physical core ownership are not implied. */
internal class ProcessCpuSampler {
    private val hz=Os.sysconf(OsConstants._SC_CLK_TCK).toDouble()
    private fun ticks():Map<String,Long> = File("/proc/self/task").listFiles().orEmpty().mapNotNull { task ->
        runCatching { val text=File(task,"stat").readText();val fields=text.substringAfterLast(") ").split(' ')
            task.name to (fields[11].toLong()+fields[12].toLong()) }.getOrNull()
    }.toMap()
    private var previous=ticks()
    private val started=SystemClock.elapsedRealtime()
    private var total=0L
    private var peakActive=0
    private val perThread=mutableMapOf<String,Long>()
    fun sample() {
        val current=ticks();var active=0
        current.forEach { (id,value) ->
            val delta=previous[id]?.let{(value-it).coerceAtLeast(0)} ?: 0
            if(delta>0){active++;total+=delta;perThread[id]=(perThread[id] ?: 0)+delta}
        }
        peakActive=maxOf(peakActive,active);previous=current
    }
    fun result():JsonObject {sample();val elapsed=SystemClock.elapsedRealtime()-started
        return buildJsonObject {put("sampleWallMs",elapsed);put("processCpuMs",total*1000/hz)
            put("averageLogicalCoreEquivalent",if(elapsed>0) total*1000/hz/elapsed else 0.0)
            put("peakThreadsWithCpuInInterval",peakActive);put("intervalMs",100)
            put("perThreadCpuMs",buildJsonObject{perThread.forEach{(id,t)->put(id,t*1000/hz)}})
            put("scope","App process including UI/GC/profiler; active threads per interval are not simultaneous core occupancy")}
    }
}
