package com.meshlit.observability

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.TrafficStats
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import com.meshlit.core.observability.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.core.inference.*
import com.meshlit.core.common.MeshlitResult
import com.meshlit.control.AgentBackend
import com.meshlit.models.ModelLibrary
import com.meshlit.pipeline.PipelineHost
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.parseOtelHeaders
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Process-lifetime sampling and a bounded, batched encrypted audit journal. No content ingestion. */
data class AuditPolicy(val enabled:Boolean=false,val days:Int=7,val limit:Int=2000,val intervalSeconds:Int=30)
class AuditTelemetry(private val context:Context,private val settings:SettingsRepository,private val tracing:TracingController,
    private val logs:LogBuffer,private val library:ModelLibrary,private val inference:InferenceCoordinator,
    private val backend:AgentBackend,private val pipeline:PipelineHost,private val scope:CoroutineScope) {
    private val prefs=context.getSharedPreferences("audit-policy-v1",0)
    private val _policy=MutableStateFlow(AuditPolicy(prefs.getBoolean("enabled",false),prefs.getInt("days",7).coerceIn(1,90),prefs.getInt("limit",2000).coerceIn(100,5000),prefs.getInt("interval",30).coerceIn(15,300)))
    val policy=_policy.asStateFlow()
    private val secrets by lazy { EncryptedCredentialStore(context,"audit-telemetry-journal") }
    val journal=AuditJournal(object:AuditStorage {
        override suspend fun read()=withContext(Dispatchers.IO){secrets.get("records")}
        override suspend fun write(jsonl:String)=withContext(Dispatchers.IO){secrets.putCommitted("records",jsonl)}
    })
    private val started=AtomicBoolean()
    private val channel=Channel<AuditRecord>(512)
    private val lost=AtomicLong()
    private val _dropped=MutableStateFlow(0L);val dropped=_dropped.asStateFlow()
    private val _state=MutableStateFlow("Starting audit storage");val state=_state.asStateFlow()
    val ready=CompletableDeferred<Unit>()
    private val _sample=MutableStateFlow<Map<String,Double>>(emptyMap());val sample=_sample.asStateFlow()
    fun record(record:AuditRecord) {
        if(!policy.value.enabled)return
        if(!channel.trySend(record.safe()).isSuccess)_dropped.value=lost.incrementAndGet()
    }
    suspend fun configure(value:AuditPolicy) {
        require(value.days in 1..90 && value.limit in 100..5000 && value.intervalSeconds in 15..300)
        ready.await()
        withContext(Dispatchers.IO){check(prefs.edit().putBoolean("enabled",value.enabled).putInt("days",value.days).putInt("limit",value.limit).putInt("interval",value.intervalSeconds).commit()){"Audit policy could not be saved"}}
        _policy.value=value
        journal.configure(value.limit,value.days)
        _state.value=if(value.enabled)"Local collection enabled" else "Local collection off; retained history available"
        record(AuditRecord(source=AuditSource.SETTINGS,action="audit.policy.updated",actor=AuditActor.HUMAN,outcome=AuditOutcome.SUCCEEDED))
    }
    fun start() {
        if(!started.compareAndSet(false,true))return
        logs.auditObserver={level,source->if(source!=LogSource.SYSTEM || level!=LogBuffer.Level.INFO)record(AuditRecord(
            source=when(source){LogSource.NETWORK->AuditSource.NETWORK;LogSource.AGENT->AuditSource.AGENT;LogSource.INFERENCE->AuditSource.INFERENCE;else->AuditSource.APP},
            action="log.${level.name.lowercase()}",outcome=if(level==LogBuffer.Level.ERROR)AuditOutcome.FAILED else AuditOutcome.OBSERVED))}
        backend.audit=::record
        scope.launch(Dispatchers.IO) {
            try{journal.open(policy.value.limit,policy.value.days);ready.complete(Unit);_state.value="Audit storage ready"}
            catch(e:CancellationException){throw e}catch(_:Exception){_state.value="Audit storage unavailable; monitoring cannot persist";ready.completeExceptionally(IllegalStateException("Audit storage unavailable"));return@launch}
            record(AuditRecord(source=AuditSource.APP,action="app.audit.started"))
            while(isActive){
                val first=channel.receive();val batch=mutableListOf(first);delay(150)
                while(batch.size<128){val next=channel.tryReceive().getOrNull() ?: break;batch.add(next)}
                // A disabled policy drops queued work before it reaches disk or network.
                if(!policy.value.enabled)continue
                try{journal.append(batch)}
                catch(e:CancellationException){throw e}catch(_:Exception){_state.value="Audit batch failed; records lost";_dropped.value=lost.addAndGet(batch.size.toLong());continue}
                var emitted=true
                batch.forEach{if(runCatching{tracing.record(it)}.isFailure)emitted=false}
                _state.value=if(!policy.value.enabled)"Local collection off; retained history available" else if(emitted)"Auditing active" else "Saved locally; telemetry emission failed"
            }
        }
        // Reconfiguration is independent of model/bootstrap failures and runs on IO.
        scope.launch(Dispatchers.IO) {
            combine(settings.tracingModeFlow,settings.tracingOtelEndpointFlow,settings.tracingOtelHeadersFlow){m,e,h->Triple(m,e,h)}.collect{(m,e,h)->
                try{tracing.reconfigure(TracingMode.valueOf(m.name),e,parseOtelHeaders(h))}
                catch(ex:CancellationException){throw ex}catch(_:Exception){tracing.reconfigure(TracingMode.Local);_state.value="Collector configuration rejected; local tracing only"}
            }
        }
        scope.launch {
            inference.events.collect { e->when(e){
                is InferenceEvent.LoadStarted->record(AuditRecord(source=AuditSource.INFERENCE,action="model.load",outcome=AuditOutcome.STARTED,targetHash=AuditRecord.hashTarget(e.modelPath)))
                is InferenceEvent.LoadSucceeded->record(AuditRecord(source=AuditSource.INFERENCE,action="model.load",outcome=AuditOutcome.SUCCEEDED,targetHash=AuditRecord.hashTarget(e.model.modelPath)))
                is InferenceEvent.LoadFailed->record(AuditRecord(source=AuditSource.INFERENCE,action="model.load",outcome=AuditOutcome.FAILED))
                InferenceEvent.Unloaded->record(AuditRecord(source=AuditSource.INFERENCE,action="model.unload",outcome=AuditOutcome.SUCCEEDED))
                is InferenceEvent.GenerationStarted->record(AuditRecord(source=AuditSource.INFERENCE,action="generation",outcome=AuditOutcome.STARTED)) // Never read e.prompt.
                is InferenceEvent.GenerationFinished->{val r=(e.result as? MeshlitResult.Success)?.value
                    record(AuditRecord(source=AuditSource.INFERENCE,action="generation",outcome=if(r==null)AuditOutcome.FAILED else AuditOutcome.SUCCEEDED,
                        measurements=buildMap{r?.let{put("duration_ms",it.totalDurationMs.toDouble());it.promptTokens?.let{n->put("input_tokens",n.toDouble())};it.generatedTokens?.let{n->put("output_tokens",n.toDouble())}}}))}
            }}
        }
        scope.launch {
            library.ready.await();var previous=library.models.value.associateBy{it.id}
            library.models.collect{models->models.forEach { m->if(previous[m.id]?.phase!=m.phase)record(AuditRecord(source=AuditSource.MODELS,action="model.phase.changed",targetHash=AuditRecord.hashTarget(m.id),
                outcome=when{m.active->AuditOutcome.STARTED;m.installed->AuditOutcome.SUCCEEDED;m.error!=null->AuditOutcome.FAILED;else->AuditOutcome.OBSERVED},measurements=buildMap{put("bytes",m.bytes.toDouble());if(m.total>=0)put("total_bytes",m.total.toDouble())})) };previous=models.associateBy{it.id}}
        }
        scope.launch {
            backend.taskBoard.ready.await();var previous=backend.taskBoard.tasks.value.associateBy{it.id}
            backend.taskBoard.tasks.collect{tasks->val current=tasks.associateBy{it.id}
                tasks.filter{previous[it.id]?.revision!=it.revision}.forEach{record(AuditRecord(source=AuditSource.TASKS,action=if(it.id in previous)"task.updated" else "task.created",targetHash=AuditRecord.hashTarget(it.id),outcome=AuditOutcome.SUCCEEDED))}
                (previous.keys-current.keys).forEach{record(AuditRecord(source=AuditSource.TASKS,action="task.deleted",targetHash=AuditRecord.hashTarget(it),outcome=AuditOutcome.SUCCEEDED))};previous=current}
        }
        scope.launch {
            var previous=pipeline.status.value
            pipeline.status.collect{s->if(s.worker!=previous.worker || s.coordinator!=previous.coordinator || s.starting!=previous.starting || s.error!=previous.error){
                record(AuditRecord(source=AuditSource.CLUSTER,action="pipeline.state.changed",outcome=if(s.error==null)AuditOutcome.OBSERVED else AuditOutcome.FAILED,
                    measurements=mapOf("worker_active" to if(s.worker)1.0 else 0.0,"coordinator_active" to if(s.coordinator)1.0 else 0.0)))};previous=s}
        }
        scope.launch(Dispatchers.IO) {
            policy.collectLatest { config->if(!config.enabled){_sample.value=emptyMap();return@collectLatest}
                while(isActive){try{val m=readDevice();_sample.value=m;record(AuditRecord(source=AuditSource.DEVICE,action="device.sample",measurements=m))}
                    catch(e:CancellationException){throw e}catch(_:Exception){_state.value="Device sample unavailable"};delay(config.intervalSeconds*1000L)}
            }
        }
    }
    private fun readDevice():Map<String,Double> = buildMap {
        val info=ActivityManager.MemoryInfo();context.getSystemService(ActivityManager::class.java).getMemoryInfo(info)
        put("ram_available_bytes",info.availMem.toDouble());put("ram_total_bytes",info.totalMem.toDouble())
        val rt=Runtime.getRuntime();put("heap_bytes",(rt.totalMemory()-rt.freeMemory()).toDouble());put("storage_free_bytes",context.filesDir.usableSpace.toDouble())
        context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { b->
            val level=b.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=b.getIntExtra(BatteryManager.EXTRA_SCALE,-1)
            if(level>=0 && scale>0)put("battery_percent",level*100.0/scale)
            b.getIntExtra(BatteryManager.EXTRA_TEMPERATURE,Int.MIN_VALUE).takeIf{it!=Int.MIN_VALUE}?.let{put("battery_celsius",it/10.0)}
        }
        if(Build.VERSION.SDK_INT>=29)put("thermal_status",context.getSystemService(PowerManager::class.java).currentThermalStatus.toDouble())
        TrafficStats.getUidRxBytes(android.os.Process.myUid()).takeIf{it>=0}?.let{put("network_rx_bytes",it.toDouble())}
        TrafficStats.getUidTxBytes(android.os.Process.myUid()).takeIf{it>=0}?.let{put("network_tx_bytes",it.toDouble())}
        put("active_transfers",library.models.value.count{it.active}.toDouble());put("installed_models",library.models.value.count{it.installed}.toDouble())
        put("active_jobs",(backend.controller.jobs.value+backend.humanController.jobs.value).count{!it.terminal}.toDouble())
        put("task_count",backend.taskBoard.tasks.value.size.toDouble())
    }
}
