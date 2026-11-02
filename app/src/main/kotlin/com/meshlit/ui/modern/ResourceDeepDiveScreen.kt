package com.meshlit.ui.modern

import android.app.ActivityManager
import android.content.Context
import android.os.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.CpuMode
import com.meshlit.models.AccelerationPreferences
import kotlinx.coroutines.*
import java.io.File

private data class DeepSample(val at:Long,val rows:List<Pair<String,String>>,val cpu:Float?,val ram:Float,val battery:Float?)
private class DeepReader(private val context:Context) {
    private var at=0L;private var cpu=0L
    private var threadTimes=emptyMap<String,Long>()
    private var nativeTimes=emptyMap<Pair<Int,Long>,Long>()
    fun read():DeepSample {
        val now=SystemClock.elapsedRealtime();val used=Process.getElapsedCpuTime()
        val children=com.meshlit.resources.OwnedNativeProcesses.read()
        val childDelta=children.sumOf{child->nativeTimes[child.pid to child.startedTicks]?.let{(child.cpuMs-it).coerceAtLeast(0)} ?: 0L}
        nativeTimes=children.associate{(it.pid to it.startedTicks) to it.cpuMs}
        val equivalent=if(at>0 && now>at) ((used-cpu).coerceAtLeast(0)+childDelta).toDouble()/(now-at) else null
        at=now;cpu=used
        val memory=ActivityManager.MemoryInfo().also{(context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager).getMemoryInfo(it)}
        val runtime=Runtime.getRuntime();val decision=AndroidCpuBudget.decision()
        fun mb(n:Long)="%.1f MiB".format(n/1048576.0)
        val prefs=AccelerationPreferences(context)
        val rows=mutableListOf(
            "CPU mode" to prefs.cpuMode().name.replace('_',' '),
            "Adaptive target" to "${decision.threads} workers; ${decision.headroom} cores of headroom",
            "Policy status" to decision.reason,
            "Active Meshlit work" to (CpuWorkGate.active ?: "Idle"),
            "Queued heavy operations" to CpuWorkGate.queued.toString(),
            "Last SDK thread hint" to (AndroidCpuBudget.lastRequestedThreads?.toString() ?: "No request yet"),
            "Observed Meshlit CPU" to (equivalent?.let{"%.2f logical cores equivalent; %.1f%% of reported CPU capacity".format(it,it*100/runtime.availableProcessors())} ?: "Collecting second sample"),
            "System available / total RAM" to "${mb(memory.availMem)} / ${mb(memory.totalMem)}",
            "Android low-memory status" to memory.lowMemory.toString(),
            "App process PSS" to mb(Debug.getPss()*1024),
            "App Java heap used / limit" to "${mb(runtime.totalMemory()-runtime.freeMemory())} / ${mb(runtime.maxMemory())}",
            "App native heap allocated" to mb(Debug.getNativeHeapAllocatedSize()),
            "App storage free / total" to "${mb(context.filesDir.usableSpace)} / ${mb(context.filesDir.totalSpace)}",
            "Thermal status" to (if(Build.VERSION.SDK_INT>=29) context.getSystemService(PowerManager::class.java).currentThermalStatus.toString() else "Unavailable"),
            "Battery saver" to context.getSystemService(PowerManager::class.java).isPowerSaveMode.toString())
        rows+="Owned native runtime processes" to if(children.isEmpty())"None observed or proc access unavailable" else children.joinToString{"PID ${it.pid}: ${mb(it.rssBytes)} RSS, ${it.threadCount} threads"}
        val battery=context.registerReceiver(null,android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
        val level=battery?.getIntExtra(BatteryManager.EXTRA_LEVEL,-1) ?: -1;val scale=battery?.getIntExtra(BatteryManager.EXTRA_SCALE,-1) ?: -1
        rows+="Battery" to if(scale>0 && level>=0) "${level*100/scale}%" else "Unavailable"
        val temp=battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE)
        rows+="Battery temperature" to if(temp!=null && temp!=Int.MIN_VALUE) "${temp/10.0} °C" else "Unavailable"
        for(core in 0 until runtime.availableProcessors().coerceAtMost(128)) {
            val base=File("/sys/devices/system/cpu/cpu$core")
            val online=runCatching{File(base,"online").readText().trim()}.getOrNull() ?: if(core==0) "1" else "Unknown"
            val freq=runCatching{File(base,"cpufreq/scaling_cur_freq").readText().trim().toLong()/1000}.getOrNull()
            rows+="CPU $core" to "Online: $online · ${freq?.let{"$it MHz"} ?: "frequency unavailable"}"
        }
        val hz=android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK).toDouble()
        val current=File("/proc/self/task").listFiles().orEmpty().mapNotNull{task->runCatching {
            val stat=File(task,"stat").readText();val fields=stat.substringAfterLast(") ").split(' ')
            val ticks=fields[11].toLong()+fields[12].toLong()
            Triple(task.name,stat.substringAfter('(').substringBeforeLast(')'),ticks)
        }.getOrNull()}
        rows+="App thread count" to current.size.toString()
        current.map{(id,name,ticks)->Triple(id,name,threadTimes[id]?.let{(ticks-it).coerceAtLeast(0)} ?: 0)}
            .sortedByDescending{it.third}.take(12).forEach{(id,name,ticks)->rows+="Thread $id · $name" to "%.1f ms CPU since previous sample".format(ticks*1000/hz)}
        threadTimes=current.associate{it.first to it.third}
        return DeepSample(now,rows,equivalent?.div(runtime.availableProcessors())?.toFloat(),((memory.totalMem-memory.availMem).toDouble()/memory.totalMem).toFloat(),if(scale>0 && level>=0) level.toFloat()/scale else null)
    }
}
@Composable internal fun ResourceDeepDiveScreen(onBack:()->Unit) {
    val context=LocalContext.current;val owner=LocalLifecycleOwner.current
    val reader=remember{DeepReader(context)};val prefs=remember{AccelerationPreferences(context)}
    var sample by remember{mutableStateOf<DeepSample?>(null)};var problem by remember{mutableStateOf<String?>(null)}
    val history=remember{mutableStateListOf<DeepSample>()}
    var controls by remember{mutableStateOf(false)}
    if(controls){AccelerationScreen{controls=false};return}
    LaunchedEffect(owner,reader){owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED){
        while(isActive){try{sample=withContext(Dispatchers.IO){reader.read()};sample?.let{history.add(it);if(history.size>60)history.removeAt(0)};problem=null}catch(e:Exception){if(e is CancellationException)throw e;problem="Resource sample unavailable"};delay(2000)}
    }}
    LazyColumn(Modifier.fillMaxSize().safeDrawingPadding().testTag("resource-deep-dive"),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        item{TextButton(onClick=onBack){Text("Back")};Text("Live resource deep dive",style=MaterialTheme.typography.headlineSmall)
            Text("Live CPU, memory and battery · refreshes every 2 seconds while visible.")
            Button(onClick={controls=true}){Text("CPU modes and process budget")}
            if(prefs.cpuMode()==CpuMode.OVERDRIVE_PLUS) Text("WARNING: Overdrive+ CPU limit overrides active",color=MaterialTheme.colorScheme.error)
            Text("Last sample: ${sample?.at?.let{(SystemClock.elapsedRealtime()-it)/1000} ?: 0}s ago")
            problem?.let{Text(it,color=MaterialTheme.colorScheme.error)}
        }
        item{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)){
            ResourceGauge("Meshlit CPU",sample?.cpu,Modifier.weight(1f));ResourceGauge("System RAM",sample?.ram,Modifier.weight(1f));ResourceGauge("Battery",sample?.battery,Modifier.weight(1f))
        }}
        item{Text("Last 2 minutes · CPU (primary) and RAM (secondary)")
            val primary=MaterialTheme.colorScheme.primary;val secondary=MaterialTheme.colorScheme.tertiary
            Canvas(Modifier.fillMaxWidth().height(140.dp)){
                for(i in 0..4)drawLine(Color.Gray.copy(alpha=.25f),Offset(0f,size.height*i/4),Offset(size.width,size.height*i/4))
                for(index in 1 until history.size){
                    fun point(i:Int,value:Float)=Offset(size.width*i/59f,size.height*(1-value.coerceIn(0f,1f)))
                    val a=history[index-1];val b=history[index]
                    if(a.cpu!=null && b.cpu!=null)drawLine(primary,point(index-1,a.cpu),point(index,b.cpu),3f)
                    drawLine(secondary,point(index-1,a.ram),point(index,b.ram),3f)
                }
            };Text("0–100% scale. CPU includes Meshlit and readable owned native workers, divided by logical core capacity. Missing process readings stay unavailable.",style=MaterialTheme.typography.bodySmall)
        }
        item{CpuAlertSettings()}
        item{LiveSensorPanel()}
        item{DeviceResultsPanel()}
        items(sample?.rows.orEmpty()){(label,value)->Card(Modifier.fillMaxWidth()){Column(Modifier.padding(12.dp)){Text(label,style=MaterialTheme.typography.labelLarge);Text(value)}}}
    }
}

@Composable private fun ResourceGauge(label:String,value:Float?,modifier:Modifier=Modifier) {
    val color=MaterialTheme.colorScheme.primary;val track=MaterialTheme.colorScheme.surfaceVariant
    Column(modifier,horizontalAlignment=androidx.compose.ui.Alignment.CenterHorizontally){
        Box(Modifier.size(88.dp),contentAlignment=androidx.compose.ui.Alignment.Center){
            Canvas(Modifier.fillMaxSize().padding(6.dp)){
                drawArc(track,135f,270f,false,style=Stroke(10f,cap=StrokeCap.Round))
                value?.let{drawArc(color,135f,270f*it.coerceIn(0f,1f),false,style=Stroke(10f,cap=StrokeCap.Round))}
            };Text(value?.let{"%.0f%%".format(it*100)} ?: "—")
        };Text(label,Modifier.fillMaxWidth(),style=MaterialTheme.typography.labelMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center,maxLines=2)
    }
}
