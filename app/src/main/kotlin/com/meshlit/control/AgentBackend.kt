package com.meshlit.control

import android.content.Context
import android.net.Uri
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.core.mcp.*
import com.meshlit.core.mcp.control.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.models.ModelLibrary
import com.meshlit.pipeline.PipelineHost
import com.meshlit.settings.SettingsRepository
import com.meshlit.ui.theme.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*

/** Shared typed facade. Human UI may call executeHuman; agents must use delegated submit.
 * Credentials and admitted parameters live in encrypted storage, never log export. */
class AgentBackend(private val context:Context,private val settings:SettingsRepository,private val library:ModelLibrary,
    private val inference:InferenceCoordinator,private val cluster:PipelineHost,private val scope:CoroutineScope,
    val taskBoard:TaskBoard,private val workspace:CodeWorkspace,private val online:com.meshlit.providers.OnlineProviders,private val cloud:com.meshlit.cloud.CloudManagement,private val browser:com.meshlit.browser.BrowserSessionBroker) {
    enum class Scope { SETTINGS, MODELS, CLUSTER, RECOVERY, TASKS, WORKSPACE, SSH, CLOUD, BROWSER, AUDIT }
    private val operations=com.meshlit.operations.OperationsControl.get(context).gate
    private val policy=context.getSharedPreferences("typed-agent-scopes",0)
    private val json=Json{ignoreUnknownKeys=false}
    private val credentials by lazy{EncryptedCredentialStore(context,"agent-job-journal")}
    val controller=AgentCommandController(object:AgentJobStore{
        override suspend fun load():List<AgentJob> = withContext(Dispatchers.IO){
            credentials.get("jobs")?.let{json.decodeFromString<List<AgentJob>>(it)} ?: emptyList()
        }
        override suspend fun save(jobs:List<AgentJob>)=withContext(Dispatchers.IO){credentials.putCommitted("jobs",json.encodeToString(jobs));Unit}
    },scope,::executeAgent)
    val humanController=AgentCommandController(object:AgentJobStore{
        override suspend fun load():List<AgentJob> = withContext(Dispatchers.IO){credentials.get("human-jobs")?.let{json.decodeFromString<List<AgentJob>>(it)} ?: emptyList()}
        override suspend fun save(jobs:List<AgentJob>)=withContext(Dispatchers.IO){credentials.putCommitted("human-jobs",json.encodeToString(jobs));Unit}
    },scope,::executeHuman)
    fun delegated(scope:Scope)=policy.getBoolean(scope.name,false)
    /** Human-only UI entry. No command may enlarge its own delegation. */
    fun setDelegated(permission:Scope,enabled:Boolean){
        check(policy.edit().putBoolean(permission.name,enabled).commit()){ "Delegation could not be saved" }
        audit?.invoke(com.meshlit.core.observability.AuditRecord(source=com.meshlit.core.observability.AuditSource.SETTINGS,action="agent.delegation.updated",actor=com.meshlit.core.observability.AuditActor.HUMAN,outcome=com.meshlit.core.observability.AuditOutcome.SUCCEEDED))
        if(!enabled) scope.launch{controller.ready.await();controller.jobs.value.filter{!it.terminal && required(it.command)==permission}.forEach{controller.cancel(it.command.requestId)}}
    }
    private fun commandFeature(command:AgentCommand):com.meshlit.core.common.control.ManagedFeature = when(required(command)) {
        Scope.MODELS -> if(command.operation in setOf(AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_ADD_URL,AgentOperation.MODEL_IMPORT)) com.meshlit.core.common.control.ManagedFeature.MODEL_TRANSFERS else com.meshlit.core.common.control.ManagedFeature.INFERENCE
        Scope.CLUSTER -> com.meshlit.core.common.control.ManagedFeature.CLUSTER
        Scope.RECOVERY -> com.meshlit.core.common.control.ManagedFeature.RECOVERY
        Scope.CLOUD -> com.meshlit.core.common.control.ManagedFeature.CLOUD
        Scope.BROWSER -> com.meshlit.core.common.control.ManagedFeature.BROWSER
        Scope.WORKSPACE -> com.meshlit.core.common.control.ManagedFeature.FILES
        else -> com.meshlit.core.common.control.ManagedFeature.AUTOMATION
    }
    private fun required(command:AgentCommand):Scope?=when(command.operation){
        AgentOperation.BROWSER_STATUS,AgentOperation.BROWSER_AUTONOMOUS_RUN,AgentOperation.BROWSER_STOP->Scope.BROWSER
        AgentOperation.CLOUD_PROFILES,AgentOperation.ENVIRONMENT_PROFILES,AgentOperation.CLOUD_EXECUTE->Scope.CLOUD
        AgentOperation.CHECKPOINT_LIST,AgentOperation.CHECKPOINT_SAVE,AgentOperation.CHECKPOINT_RESTORE,AgentOperation.CHECKPOINT_DELETE->Scope.RECOVERY
        AgentOperation.SETTINGS_PATCH->Scope.SETTINGS
        AgentOperation.MODEL_OPTIONS_SET,AgentOperation.MODEL_IMPORT_SOURCES,AgentOperation.MODEL_STARTUP_SET,AgentOperation.MODEL_DOWNLOAD,AgentOperation.MODEL_ADD_URL,AgentOperation.MODEL_IMPORT,AgentOperation.MODEL_LOAD,AgentOperation.MODEL_GENERATE,AgentOperation.MODEL_UNLOAD,AgentOperation.MODEL_DELETE->Scope.MODELS
        AgentOperation.CLUSTER_PLAN,AgentOperation.CLUSTER_START,AgentOperation.CLUSTER_WORKER_START,AgentOperation.CLUSTER_STOP->Scope.CLUSTER
        AgentOperation.TASK_LIST,AgentOperation.TASK_CREATE,AgentOperation.TASK_UPDATE,AgentOperation.TASK_BATCH_UPDATE,AgentOperation.TASK_DELETE->Scope.TASKS
        AgentOperation.WORKSPACE_LIST,AgentOperation.WORKSPACE_READ,AgentOperation.WORKSPACE_WRITE->Scope.WORKSPACE
        else->null
    }
    @Volatile var audit:((com.meshlit.core.observability.AuditRecord)->Unit)?=null
    private suspend fun audited(command:AgentCommand,actor:com.meshlit.core.observability.AuditActor,block:suspend()->JsonElement):JsonElement {
        val start=android.os.SystemClock.elapsedRealtime()
        fun report(outcome:com.meshlit.core.observability.AuditOutcome){audit?.invoke(com.meshlit.core.observability.AuditRecord(
            source=com.meshlit.core.observability.AuditSource.AGENT,action="command.${command.operation.name.lowercase()}",actor=actor,outcome=outcome,
            targetHash=com.meshlit.core.observability.AuditRecord.hashTarget(command.requestId),measurements=mapOf("duration_ms" to (android.os.SystemClock.elapsedRealtime()-start).toDouble())))}
        report(com.meshlit.core.observability.AuditOutcome.STARTED)
        try{return block().also{report(com.meshlit.core.observability.AuditOutcome.SUCCEEDED)}}
        catch(e:CancellationException){report(com.meshlit.core.observability.AuditOutcome.CANCELLED);throw e}
        catch(e:Exception){report(if(e is AgentCommandFailure && e.code=="permission_denied")com.meshlit.core.observability.AuditOutcome.DENIED else com.meshlit.core.observability.AuditOutcome.FAILED);throw e}
    }
    var remoteAuthorizer:(suspend(AgentCommand)->Unit)?=null
    private suspend fun executeAgent(command:AgentCommand):JsonElement = operations.run(commandFeature(command),true) { executeAgentManaged(command) }
    private suspend fun executeAgentManaged(command:AgentCommand):JsonElement = audited(command,com.meshlit.core.observability.AuditActor.AGENT) {
        remoteAuthorizer?.invoke(command)
        required(command)?.let{if(!delegated(it)) throw AgentCommandFailure("permission_denied","Enable saved ${it.name.lowercase()} delegation first")}
        if(command.operation==AgentOperation.MODEL_GENERATE && command.modelId?.startsWith("cloud:")==true) {
            require(online.profiles.value.firstOrNull{it.id==command.modelId!!.removePrefix("cloud:")}?.agentAllowed==true){"Selected online profile does not allow agents"}
        }
        if(command.operation in setOf(AgentOperation.CLOUD_PROFILES,AgentOperation.ENVIRONMENT_PROFILES,AgentOperation.CLOUD_EXECUTE)) return@audited executeCloud(command,com.meshlit.core.cloudmcp.management.CloudActor.AGENT)
        executeCommand(command,true)
    }
    suspend fun executeHuman(command:AgentCommand):JsonElement = if(command.operation in setOf(AgentOperation.CLUSTER_STOP,AgentOperation.BROWSER_STOP,AgentOperation.MODEL_UNLOAD)) executeHumanManaged(command) else operations.run(commandFeature(command)) { executeHumanManaged(command) }
    private suspend fun executeHumanManaged(command:AgentCommand):JsonElement=audited(command,com.meshlit.core.observability.AuditActor.HUMAN){executeCommand(command,false)}
    private suspend fun executeCommand(command:AgentCommand,agent:Boolean):JsonElement {
        command.validate();library.ready.await()
        return when(command.operation){
            AgentOperation.BROWSER_STATUS->browser.status()
            AgentOperation.BROWSER_STOP->{withContext(Dispatchers.Main.immediate){browser.stop()};buildJsonObject{put("stopRequested",true)}}
            AgentOperation.BROWSER_AUTONOMOUS_RUN->browser.run(command.prompt!!,command.browserMaxSteps,agent){
                if(agent){remoteAuthorizer?.invoke(command);if(!delegated(Scope.BROWSER)) throw AgentCommandFailure("permission_denied","Browser delegation revoked")}
            }
            AgentOperation.CLOUD_PROFILES,AgentOperation.ENVIRONMENT_PROFILES,AgentOperation.CLOUD_EXECUTE->executeCloud(command,com.meshlit.core.cloudmcp.management.CloudActor.HUMAN)
            AgentOperation.SETTINGS_READ -> settings.flow.first().let{config ->buildJsonObject{
                put("themeMode",config.themeMode.name);put("accentHue",config.accentHue.name);put("dynamicColors",config.dynamicColors)
                put("uiFont",config.uiFont.name);put("surfaceStyle",config.surfaceStyle.name);put("animationsEnabled",config.animationsEnabled);put("fontScale",config.fontScale);put("startupModelEnabled",library.startupEnabled.value);put("startupModelId",library.startupId.value);put("startupModelStatus",library.startupStatus.value)
                put("delegation",buildJsonObject{Scope.entries.forEach{put(it.name,delegated(it))}})
            }}
            AgentOperation.SETTINGS_PATCH -> {
                val patch=command.appearance!!
                // Validate all fields before the first write.
                val mode=patch.themeMode?.let{value ->ThemeMode.entries.firstOrNull{it.name==value} ?: throw AgentCommandFailure("invalid_args","Unknown themeMode")}
                val accent=patch.accentHue?.let{value ->AccentHue.entries.firstOrNull{it.name==value} ?: throw AgentCommandFailure("invalid_args","Unknown accentHue")}
                val font=patch.uiFont?.let{value->UiFont.entries.firstOrNull{it.name==value} ?: throw AgentCommandFailure("invalid_args","Unknown UI font")}
                val surface=patch.surfaceStyle?.let{value->SurfaceStyle.entries.firstOrNull{it.name==value} ?: throw AgentCommandFailure("invalid_args","Unknown surface style")}
                patch.fontScale?.let{if(!it.isFinite() || it !in 0.85f..1.5f) throw AgentCommandFailure("invalid_args","fontScale outside 0.85–1.5")}
                font?.let{settings.setUiFont(it)};surface?.let{settings.setSurfaceStyle(it)};mode?.let{settings.setThemeMode(it)};accent?.let{settings.setAccentHue(it)}
                patch.dynamicColors?.let{settings.setDynamicColors(it)};patch.animationsEnabled?.let{settings.setAnimationsEnabled(it)};patch.fontScale?.let{settings.setFontScale(it)}
                executeHuman(command.copy(operation=AgentOperation.SETTINGS_READ))
            }
            AgentOperation.MODELS_LIST -> buildJsonObject{put("models",buildJsonArray{library.models.value.forEach{entry ->add(buildJsonObject{
                put("modelId",entry.id);put("name",entry.name);put("installed",entry.installed);put("phase",entry.phase);put("bytes",entry.bytes)
                put("runtimeOptions",Json.encodeToJsonElement(entry.runtimeOptions));entry.metadata?.let{put("metadata",Json.encodeToJsonElement(it))};put("bundled",entry.bundled);put("downloadBackend",entry.downloadBackend);put("totalBytes",entry.total);put("sizeBytes",entry.sizeBytes);put("loaded",inference.loadedModel()?.modelPath==entry.path && entry.installed)
            })};online.profiles.value.forEach{profile->add(buildJsonObject{put("modelId","cloud:${profile.id}");put("name",profile.name);put("model",profile.model);put("online",true);put("enabled",profile.enabled);put("agentAllowed",profile.agentAllowed)})}})}
            AgentOperation.DEVICE_RUNTIME_STATUS -> buildJsonObject {
                put("observed",Json.encodeToJsonElement(library.deviceSnapshot()));put("plan",Json.encodeToJsonElement(library.devicePlan()))
            }
            AgentOperation.MODEL_OPTIONS_SET -> {
                library.setRuntimeOptions(command.modelId!!,com.meshlit.core.inference.models.ModelRuntimeOptions(
                    com.meshlit.core.inference.models.LocalModelBackend.valueOf(command.runtimeBackend),command.contextSize,command.keyCacheType))
                buildJsonObject{put("saved",true);put("appliedToLoadedModel",false);put("reloadRequired",true)}
            }
            AgentOperation.MODEL_IMPORT_SOURCES -> buildJsonObject{put("sources",buildJsonArray{
                context.contentResolver.persistedUriPermissions.filter{it.isReadPermission}.forEach{add(it.uri.toString())}
            })}
            AgentOperation.MODEL_DOWNLOAD -> {val task=library.download(command.modelId!!,com.meshlit.models.ModelDownloadBackend.valueOf(command.downloadBackend));modelResult(library.awaitTransfer(command.modelId!!,cancelOwned=task))}
            AgentOperation.MODEL_ADD_URL -> {val id=library.addUrl(command.url!!,command.name.orEmpty(),startDownload=false);val task=library.download(id,com.meshlit.models.ModelDownloadBackend.valueOf(command.downloadBackend));modelResult(library.awaitTransfer(id,cancelOwned=task))}
            AgentOperation.MODEL_IMPORT -> {
                val uri=Uri.parse(command.importUri!!)
                if(uri.scheme!="content" || context.contentResolver.persistedUriPermissions.none{it.isReadPermission && it.uri==uri})
                    throw AgentCommandFailure("permission_denied","Use a previously granted content URI; file paths are not accepted")
                val id=library.importFiles(listOf(uri)).single();modelResult(library.awaitTransfer(id,cancelImport=true))
            }
            AgentOperation.MODEL_LOAD -> {library.load(command.modelId!!);buildJsonObject{put("loaded",inference.loadedModel()!=null);put("modelId",command.modelId)}}
            AgentOperation.MODEL_GENERATE -> {
                if(command.modelId!!.startsWith("cloud:")) {
                    val (profile,reply)=online.generate(command.modelId!!.removePrefix("cloud:"),listOf(com.meshlit.core.inference.models.OnlineMessage("user",command.prompt!!)),maxTokens=command.maxTokens,temperature=command.temperature,agent=agent)
                    buildJsonObject{put("text",reply.text);put("online",true);reply.inputTokens?.let{put("promptTokens",it)};reply.outputTokens?.let{put("generatedTokens",it)};reply.estimatedCost(profile)?.let{put("estimatedCost",it);put("currency",profile.currency)}}
                } else {
                val path=modelPath(command.modelId!!)
                val result=inference.infer(com.meshlit.core.inference.InferenceRequest(command.prompt!!,maxTokens=command.maxTokens,
                    temperature=command.temperature,onToken={},expectedModelPath=path))
                if(result !is com.meshlit.core.common.MeshlitResult.Success) throw AgentCommandFailure("inference_failed","Generation failed or the selected model changed")
                buildJsonObject{put("text",result.value.finalText);put("promptTokens",result.value.promptTokens);put("generatedTokens",result.value.generatedTokens)
                    put("finishReason",result.value.finishReason.tag);put("durationMs",result.value.totalDurationMs)}
                }
            }
            AgentOperation.MODEL_UNLOAD -> {inference.unloadModel();buildJsonObject{put("loaded",false)}}
            AgentOperation.MODEL_DELETE -> {library.delete(command.modelId!!);buildJsonObject{put("deleted",true);put("modelId",command.modelId)}}
            AgentOperation.CLUSTER_STATUS -> buildJsonObject{
                put("worker",cluster.status.value.worker);put("coordinator",cluster.status.value.coordinator);put("starting",cluster.status.value.starting)
                put("pairedWorkers",cluster.peers().size);put("nativeAvailable",cluster.available());put("memoryBudgetBytes",cluster.status.value.memoryBudgetBytes)
                cluster.status.value.coordinatorId?.let{put("coordinatorId",it)}
            }
            AgentOperation.CLUSTER_PLAN -> {
                val options=library.models.value.first{it.id==command.modelId}.runtimeOptions
                val peers=cluster.negotiate(modelPath(command.modelId!!),options.contextSize,options.keyCacheType)
                buildJsonObject{put("workers",buildJsonArray{peers.forEachIndexed{index,peer ->add(buildJsonObject{put("index",index);put("weight",peer.weight)})}})
                    put("coordinatorId",cluster.status.value.coordinatorId.orEmpty());put("memoryBudgetBytes",cluster.status.value.memoryBudgetBytes)
                    put("estimatedKvBytes",cluster.status.value.estimatedKvBytes);put("estimatedWorkerBytes",Json.encodeToJsonElement(cluster.status.value.estimatedWorkerBytes))
                    put("placement","layer");put("memoryReserved",false);put("remoteCoordinatorActivation",false)}
            }
            AgentOperation.CLUSTER_START -> {cluster.startPipeline(modelPath(command.modelId!!),library.models.value.first{it.id==command.modelId}.runtimeOptions.contextSize,library.models.value.first{it.id==command.modelId}.runtimeOptions.keyCacheType);executeHuman(command.copy(operation=AgentOperation.CLUSTER_STATUS))}
            AgentOperation.CLUSTER_WORKER_START -> {cluster.startWorker();executeHuman(command.copy(operation=AgentOperation.CLUSTER_STATUS))}
            AgentOperation.CLUSTER_STOP -> {cluster.stopAll();executeHuman(command.copy(operation=AgentOperation.CLUSTER_STATUS))}
            AgentOperation.MODEL_STARTUP_SET -> {command.modelId?.let{library.setStartupModel(it)};command.startupEnabled?.let{library.setStartupEnabled(it)};executeHuman(command.copy(operation=AgentOperation.SETTINGS_READ))}
            AgentOperation.TASK_LIST -> {taskBoard.ready.await();json.encodeToJsonElement(taskBoard.tasks.value)}
            AgentOperation.TASK_CREATE -> json.encodeToJsonElement(taskBoard.create(command.task!!))
            AgentOperation.TASK_UPDATE -> json.encodeToJsonElement(taskBoard.update(command.task!!))
            AgentOperation.TASK_BATCH_UPDATE -> command.task!!.let{mutation ->json.encodeToJsonElement(taskBoard.batch(mutation.ids,mutation.phase ?: throw IllegalArgumentException("phase required")))}
            AgentOperation.TASK_DELETE -> command.task!!.let{mutation ->taskBoard.delete(mutation.id ?: error("id required"),mutation.expectedRevision ?: error("revision required"));buildJsonObject{put("deleted",true)}}
            AgentOperation.WORKSPACE_LIST -> withContext(Dispatchers.IO){json.encodeToJsonElement(workspace.list())}
            AgentOperation.WORKSPACE_READ -> withContext(Dispatchers.IO){json.encodeToJsonElement(workspace.read(command.fileName!!))}
            AgentOperation.WORKSPACE_WRITE -> withContext(Dispatchers.IO){json.encodeToJsonElement(workspace.write(command.fileName!!,command.fileText!!,command.expectedSha256))}
            AgentOperation.CHECKPOINT_LIST->json.encodeToJsonElement(cluster.checkpointList())
            AgentOperation.CHECKPOINT_SAVE->json.encodeToJsonElement(cluster.saveCheckpoint())
            AgentOperation.CHECKPOINT_RESTORE->json.encodeToJsonElement(cluster.restoreCheckpoint(command.checkpointId!!))
            AgentOperation.CHECKPOINT_DELETE->{cluster.deleteCheckpoint(command.checkpointId!!);buildJsonObject{put("deleted",true)}}
            AgentOperation.RECOVERY_STATUS -> buildJsonObject{
                put("localJobJournal",true);put("replayPolicy","explicit-new-id-after-live-state-check")
                put("replicatedTaskJournal",false);put("manualReplicaJournalAvailable",true);put("automaticTaskReplication",false);put("automaticCoordinatorFailover",false);put("nativeLocalKvCheckpoints",true);put("checkpointManagement","CHECKPOINT_LIST/SAVE/RESTORE/DELETE; saved recovery delegation required");put("portableKvRecovery",false)
            }
        }
    }
    private suspend fun executeCloud(command:AgentCommand,actor:com.meshlit.core.cloudmcp.management.CloudActor):JsonElement {
        command.validate()
        return when(command.operation){
            AgentOperation.CLOUD_PROFILES->json.encodeToJsonElement(cloud.descriptions(actor).profiles)
            AgentOperation.ENVIRONMENT_PROFILES->json.encodeToJsonElement(cloud.descriptions(actor).environments)
            AgentOperation.CLOUD_EXECUTE->json.encodeToJsonElement(cloud.execute(command.cloudProfileId!!,command.cloudAction!!,actor,command.cloudPage))
            else->error("Unknown cloud command")
        }
    }
    private fun modelPath(id:String)=library.models.value.firstOrNull{it.id==id && it.installed}?.path
        ?: throw AgentCommandFailure("invalid_args","Choose an installed modelId")
    private fun modelResult(entry:com.meshlit.models.LibraryModel)=buildJsonObject{put("modelId",entry.id);put("installed",entry.installed);put("sizeBytes",entry.sizeBytes)}
    fun specs()=listOf(
        McpToolSpec("agent_command_submit","Submit a typed durable command. Returns a job ID; poll agent_job_status for completion. Stable requestId is idempotent. Mutations require saved delegation.",
            objectSchema(mapOf("command" to AgentCommandSchema.describe()),listOf("command"))){args ->
            val command=json.decodeFromJsonElement<AgentCommand>((args as JsonObject)["command"]!!)
            val job=controller.submit(command);McpToolResult.Json(publicJob(job))
        },
        McpToolSpec("agent_job_status","List observable command jobs or inspect one requestId. Parameters/URLs/credentials are omitted.",objectSchema(mapOf("requestId" to stringProp()))){args ->
            controller.ready.await();val id=(args as? JsonObject)?.get("requestId")?.jsonPrimitive?.contentOrNull
            McpToolResult.Json(buildJsonObject{put("jobs",buildJsonArray{controller.jobs.value.filter{(id==null || it.command.requestId==id) && (required(it.command)?.let{permission->delegated(permission)}!=false)}.forEach{add(publicJob(it))}})})
        },
        McpToolSpec("agent_job_cancel","Cancel a queued/running command. Completed OS actions are not rolled back.",objectSchema(mapOf("requestId" to stringProp()),listOf("requestId"))){args ->
            val id=(args as JsonObject)["requestId"]!!.jsonPrimitive.content
            val found=controller.jobs.value.firstOrNull{it.command.requestId==id}
            if(found!=null && required(found.command)?.let{!delegated(it)}==true)
                return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,"Required delegation was revoked")
            controller.cancel(id)?.let{McpToolResult.Json(publicJob(it))} ?: McpToolResult.Error(McpToolResult.ErrorCode.NOT_FOUND,"Job not found")
        },
        McpToolSpec("agent_job_retry","Explicitly retry a failed/interrupted/cancelled job using a new requestId. Inspect live state first; delegation is rechecked.",
            objectSchema(mapOf("sourceId" to stringProp(),"requestId" to stringProp()),listOf("sourceId","requestId"))){args ->
            if(!delegated(Scope.RECOVERY)) return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.PERMISSION_DENIED,"Enable recovery delegation")
            val values=args as JsonObject;McpToolResult.Json(publicJob(controller.retry(values["sourceId"]!!.jsonPrimitive.content,values["requestId"]!!.jsonPrimitive.content)))
        },
        McpToolSpec("agent_command_schema","Discover typed command operations and scope requirements without navigating the UI."){
            McpToolResult.Json(buildJsonObject{put("version",1);put("schema",AgentCommandSchema.describe());put("operations",buildJsonArray{AgentOperation.entries.forEach{op ->add(buildJsonObject{put("operation",op.name)
                put("delegation",required(AgentCommand("schema",op))?.name ?: "READ_ONLY")})}})
                put("commandFields",buildJsonArray{listOf("requestId","operation","modelId","url","name","importUri","appearance","prompt","maxTokens","temperature","startupEnabled","task","fileName","fileText","expectedSha256","checkpointId","cloudProfileId","cloudAction","cloudPage","browserMaxSteps").forEach{add(it)}})})
        }
    )
    fun publicJob(job:AgentJob)=buildJsonObject{
        put("requestId",job.command.requestId);put("operation",job.command.operation.name);put("phase",job.phase.name)
        put("createdAtMs",job.createdAtMs);put("updatedAtMs",job.updatedAtMs)
        job.result?.let{put("result",it)};job.errorCode?.let{put("errorCode",it)};job.error?.let{put("error",it)}
    }
}
