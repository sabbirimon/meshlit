package com.meshlit.pipeline

import android.content.Context
import android.content.Intent
import android.app.ActivityManager
import android.os.Build
import android.os.PowerManager
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.inference.*
import com.meshlit.core.inference.models.*
import com.meshlit.core.inference.pipeline.*
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.*
import com.meshlit.recovery.*
import java.io.File
import java.math.BigInteger
import java.net.ServerSocket
import java.net.InetAddress
import java.security.*
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.util.Date
import javax.net.ssl.*
import javax.security.auth.x500.X500Principal

@Serializable data class PipelinePeer(val host:String,val port:Int,val fingerprint:String,val token:String,val weight:Int=1,val group:String="default")
data class PipelineStatus(val worker:Boolean=false,val coordinator:Boolean=false,val starting:Boolean=false,
    val error:String?=null,val log:List<String> = emptyList(),val coordinatorId:String?=null,val memoryBudgetBytes:Long=0,val local:Boolean=false,val estimatedKvBytes:Long=0,val estimatedWorkerBytes:List<Long> = emptyList())
data class WorkerAssessment(val peer:PipelinePeer,val offer:NodeOffer?,val latencyMs:Long?,val riskScore:Int,val reason:String)

internal fun requirePairableWorker(remote:NodeOffer,localNodeId:String) {
    require(remote.nodeId.matches(Regex("[A-Za-z0-9_-]{1,80}")) && remote.nodeId!=localNodeId) { "Invalid or local worker identity" }
    require(remote.runtimeRevision==ClusterNegotiation.REVISION && remote.workerAllowed) { "Worker runtime or permission is unavailable" }
}

/** Explicit opt-in layer offload. Native RPC is private to each app process;
 * only the certificate-pinned, authenticated TLS tunnel listens on the LAN. */
class PipelineHost(private val context:Context,private val inference:InferenceCoordinator,private val scope:CoroutineScope) {
    private val lock=Mutex()
    private val wireJson=Json { ignoreUnknownKeys=true }
    private val credentials by lazy { EncryptedCredentialStore(context,"pipeline-pairing") }
    private val _status=MutableStateFlow(PipelineStatus())
    val status=_status.asStateFlow()
    private data class NativeSession(val port:Int,val token:String,val path:String,val options:ModelRuntimeOptions,val local:Boolean)
    private var nativeSession:NativeSession?=null
    private val slotDirectory=File(context.filesDir,"native-slot-staging").apply{
        check(mkdirs() || isDirectory);listFiles()?.forEach{check(it.delete()){"Cannot clear old unencrypted slot staging"}}
    }
    private val checkpoints by lazy{NativeCheckpointStore(context)}
    private var worker:Process?=null
    @Volatile private var workerStartedAtMs:Long?=null
    private var server:Process?=null
    private var workerTunnel:RpcTunnel?=null
    private val bridges=mutableListOf<RpcTunnel>()
    private var keyFile:File?=null
    private var negotiatedPeers:List<PipelinePeer> = emptyList()
    val publicPort=50551
    private fun executable(name:String):File=File(context.applicationInfo.nativeLibraryDir,"libmeshlit_pipeline_$name.so").also {
        require(it.isFile && it.canExecute()) {"Pipeline native runtime is unavailable for this ABI; build and install it first"}
    }
    fun available()=listOf("worker","server").all { name ->
        File(context.applicationInfo.nativeLibraryDir,"libmeshlit_pipeline_$name.so").canExecute()
    }
    fun agentControlAllowed()=context.getSharedPreferences("pipeline-policy",0).getBoolean("agent-control",false)
    fun setAgentControlAllowed(allowed:Boolean){context.getSharedPreferences("pipeline-policy",0).edit().putBoolean("agent-control",allowed).apply()}
    fun peers():List<PipelinePeer> = credentials.get("peers")?.let { runCatching{Json.decodeFromString<List<PipelinePeer>>(it)}.getOrNull() }.orEmpty()
    fun activeGroup():String=credentials.get("active-group")?.takeIf(::validGroupName) ?: "default"
    fun groups():List<String> = (peers().map{it.group}+activeGroup()+"default").distinct().sorted()
    fun groupPeers():List<PipelinePeer> = peers().filter{it.group==activeGroup()}
    fun setActiveGroup(name:String):List<PipelinePeer> {
        val selected=name.trim();require(validGroupName(selected)) { "Group name must be 1–32 letters, numbers, spaces, underscores or hyphens" }
        credentials.put("active-group",selected)
        return groupPeers()
    }
    private fun validGroupName(name:String)=name.length in 1..32 && name.all{it.isLetterOrDigit() || it in " _-"}
    fun savePeers(peers:List<PipelinePeer>) {
        val groupLimit=com.meshlit.operations.ClusterControls.get(context).state.value.pipelineWorkerLimit
        require(peers.size<=64 && peers.groupBy{it.group}.values.all{it.size<=groupLimit}) { "A cluster group exceeds its worker limit" }
        peers.forEach {
            require(it.host.isNotBlank() && it.host.length<=253 && it.host.none{char->char.isWhitespace() || char in "/?#@"})
            require(it.port in 1024..65535 && it.weight in 1..100)
            require(validGroupName(it.group)) { "Invalid cluster group" }
            require(it.fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
            require(it.token.length in 32..128 && it.token.all { c -> c.code in 33..126 })
        }
        require(peers.map { "${it.host}:${it.port}" }.distinct().size==peers.size) {"Duplicate workers"}
        credentials.put("peers",Json.encodeToString(peers))
    }
    /** Approve only a live worker that proves possession of the pinned TLS key and pairing token. */
    suspend fun verifyAndSavePeer(peer:PipelinePeer):List<PipelinePeer> = withContext(Dispatchers.IO) {
        require(peer.host.isNotBlank() && peer.host.length<=253 && peer.host.none{it.isWhitespace() || it in "/?#@"})
        val response=RpcTunnel.queryCapabilities(peer.host,peer.port,peer.fingerprint,peer.token)
        val remote=wireJson.decodeFromString<NodeOffer>(response)
        requirePairableWorker(remote,offer().nodeId)
        require(peers().none { existing ->
            existing.host==peer.host && existing.port==peer.port
        }) { "This worker is already paired" }
        val updated=peers()+peer.copy(group=activeGroup())
        savePeers(updated)
        groupPeers()
    }
    /** Live, verified snapshots. A score is a conservative operational heuristic, not a security certification. */
    suspend fun assessGroup():List<WorkerAssessment> = withContext(Dispatchers.IO) {
        val localId=offer().nodeId
        groupPeers().map { peer ->
            val started=android.os.SystemClock.elapsedRealtime()
            val result=runCatching {
                wireJson.decodeFromString<NodeOffer>(RpcTunnel.queryCapabilities(peer.host,peer.port,peer.fingerprint,peer.token))
                    .copy(observedAtMs=System.currentTimeMillis())
            }
            val latency=android.os.SystemClock.elapsedRealtime()-started
            result.fold(
                onSuccess={remote -> assessWorker(peer,remote,localId,latency)},
                onFailure={WorkerAssessment(peer,null,null,100,"Unreachable or identity/token verification failed: ${it.javaClass.simpleName}")}
            )
        }
    }
    fun removePeer(peer:PipelinePeer):List<PipelinePeer> {
        // A lowered capacity must never prevent removing old members until the group is compliant.
        credentials.put("peers",Json.encodeToString(peers().filterNot{it.host==peer.host && it.port==peer.port}))
        return groupPeers()
    }
    fun pairingToken():String=credentials.get("token") ?: randomToken().also {credentials.put("token",it)}
    fun rotatePairingToken() {
        check(worker==null) { "Stop the worker before rotating its pairing token" }
        credentials.put("token",randomToken())
    }
    private fun randomToken():String=ByteArray(32).also{SecureRandom().nextBytes(it)}.joinToString(""){"%02x".format(it)}
    private fun certificate():Pair<SSLContext,X509Certificate> {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        // Conscrypt uses raw ECDSA for TLS: DIGEST_NONE must be permitted.
        // A new alias avoids reusing the incompatible v1 key. Old peer pins must be reapproved.
        val alias="meshlit-pipeline-tls-v2"
        if(!store.containsAlias(alias)) KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_EC,"AndroidKeyStore").apply {
            initialize(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY)
                .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1")).setDigests(KeyProperties.DIGEST_NONE,KeyProperties.DIGEST_SHA256)
                .setCertificateSubject(X500Principal("CN=Meshlit layer worker"))
                .setCertificateSerialNumber(BigInteger.valueOf(System.currentTimeMillis()))
                .setCertificateNotBefore(Date(System.currentTimeMillis()-86400000))
                .setCertificateNotAfter(Date(System.currentTimeMillis()+5L*365*86400000)).build())
            generateKeyPair()
        }
        val certificate=store.getCertificate(alias) as X509Certificate
        val manager=object:X509ExtendedKeyManager(){
            override fun getCertificateChain(name:String?)=if(name==alias) arrayOf(certificate) else null
            override fun getPrivateKey(name:String?)=if(name==alias) store.getKey(alias,null) as PrivateKey else null
            override fun getServerAliases(type:String?,issuers:Array<java.security.Principal>?)=if(type?.startsWith("EC")==true) arrayOf(alias) else null
            override fun chooseServerAlias(type:String?,issuers:Array<java.security.Principal>?,socket:java.net.Socket?)=getServerAliases(type,issuers)?.firstOrNull()
            override fun chooseEngineServerAlias(type:String?,issuers:Array<java.security.Principal>?,engine:SSLEngine?)=getServerAliases(type,issuers)?.firstOrNull()
            override fun getClientAliases(type:String?,issuers:Array<java.security.Principal>?)=null
            override fun chooseClientAlias(types:Array<String>?,issuers:Array<java.security.Principal>?,socket:java.net.Socket?)=null
        }
        return SSLContext.getInstance("TLS").apply{init(arrayOf(manager),null,null)} to certificate
    }
    fun controlTlsContext():SSLContext=certificate().first
    fun fingerprint():String=RpcTunnel.fingerprint(certificate().second)
    private fun port():Int=ServerSocket(0,1,InetAddress.getByName("127.0.0.1")).use {it.localPort}
    private fun running(process:Process):Boolean=try {process.exitValue();false} catch(_:IllegalThreadStateException){true}
    private fun log(line:String) { com.meshlit.observability.AppLoggerFactory.appLogger("pipeline").info("pipeline.native",line.take(1000))
        _status.update { it.copy(log=(it.log+line.take(1000)).takeLast(100)) } }
    private fun launch(name:String,args:List<String>):Process {
        val process=ProcessBuilder(listOf(executable(name).absolutePath)+args).directory(context.filesDir)
            .redirectErrorStream(true).start()
        scope.launch(Dispatchers.IO) {
            try { process.inputStream.bufferedReader().useLines { lines -> lines.forEach { log(it) } } }
            catch(cancelled:CancellationException){throw cancelled}
            catch(_:java.io.IOException){if(running(process)) log("Native log stream closed while process was active")}
        }
        return process
    }
    private val serviceState=Any()
    private var serviceRequested=false
    private fun service()=synchronized(serviceState){
        androidx.core.content.ContextCompat.startForegroundService(context,Intent(context,PipelineService::class.java)).also{serviceRequested=true}
    }
    fun serviceDestroyed(){synchronized(serviceState){serviceRequested=false}}
    suspend fun startWorker()=lock.withLock { withContext(Dispatchers.IO) {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.CLUSTER)
        if(worker!=null) return@withContext
        _status.update{it.copy(starting=true,error=null)}
        try {
            service()
            val nativePort=port()
            worker=launch("worker",listOf("-H","127.0.0.1","-p","$nativePort","--device","CPU","-t","2"))
            delay(500);check(running(worker!!)){"Native worker exited"}
            workerTunnel=RpcTunnel.server(certificate().first,publicPort,nativePort,pairingToken(),::log,capabilities={Json.encodeToString(offer())},networkAllowed={remote->(context.applicationContext as com.meshlit.MeshlitApplication).meshlitFirewall.decide(remote,null,null,publicPort).allowed})
            workerStartedAtMs=System.currentTimeMillis()
            _status.update{it.copy(worker=true)}
            val process=worker!!
            scope.launch(Dispatchers.IO) {
                process.waitFor()
                if(worker===process) {workerTunnel?.close();worker=null;workerStartedAtMs=null;_status.update{it.copy(worker=false,error="Native worker stopped")}}
            }
        } catch(cancelled:CancellationException) {stopWorkerInternal();throw cancelled}
        catch(e:Exception) {stopWorkerInternal();_status.update{it.copy(error=e.message)};throw e}
        finally {_status.update{it.copy(starting=false)};stopServiceIfIdle()}
    } }
    private fun stopWorkerInternal(){workerTunnel?.close();workerTunnel=null;val process=worker;worker=null;workerStartedAtMs=null;process?.destroy();_status.update{it.copy(worker=false)}}
    suspend fun stopWorker()=lock.withLock {stopWorkerInternal();stopServiceIfIdle()}
    private fun offer():NodeOffer {
        val memory=ActivityManager.MemoryInfo().also{context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}
        val id=credentials.get("node-id") ?: java.util.UUID.randomUUID().toString().also{credentials.put("node-id",it)}
        return NodeOffer(id,Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown",memory.availMem,context.filesDir.usableSpace,
            ClusterNegotiation.REVISION,workerAllowed=worker!=null,
            thermalStatus=if(Build.VERSION.SDK_INT>=29) context.getSystemService(PowerManager::class.java).currentThermalStatus else 0,
            deviceClass=if(context.resources.configuration.smallestScreenWidthDp>=600) "tablet" else "phone",
            backend="CPU",cpuThreads=2,workerStartedAtMs=workerStartedAtMs)
    }
    /** Query only explicitly paired workers. Local model ownership limits current
     * activation to this coordinator; the election contract also supports remote owners. */
    suspend fun negotiate(path:String,contextSize:Int=2048,keyCacheType:String="f16")=withContext(Dispatchers.IO) {
        val file=File(path);ModelFiles.validateGguf(file)
        val metadata=GgufMetadata.read(file)
        ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,contextSize,keyCacheType).validate(metadata.maxContext)
        val kv=metadata.kvBytes(contextSize,keyCacheType) ?: error("KV memory cannot be estimated for this architecture; cluster admission is unavailable")
        val hash=ModelFiles.sha256(file)
        require(groupPeers().size<=com.meshlit.operations.ClusterControls.get(context).state.value.pipelineWorkerLimit){"Lowered worker capacity; edit the paired workers first"}
        val peerOffers=groupPeers().map { peer -> peer to wireJson.decodeFromString<NodeOffer>(
            RpcTunnel.queryCapabilities(peer.host,peer.port,peer.fingerprint,peer.token)).copy(observedAtMs=System.currentTimeMillis()) }
        val local=offer().copy(coordinatorAllowed=true,coordinatorModelSha256=hash,workerAllowed=false)
        val plan=ClusterNegotiation.plan(peerOffers.map{it.second}+local,hash,file.length(),kvBytes=kv,blocks=metadata.blocks)
        negotiatedPeers=plan.workers.mapIndexed { index,node -> peerOffers.first{it.second.nodeId==node.nodeId}.first.copy(weight=plan.weights[index]) }
        _status.update{it.copy(coordinatorId=plan.coordinatorId,memoryBudgetBytes=plan.memoryBudgetBytes,estimatedKvBytes=plan.estimatedKvBytes,estimatedWorkerBytes=plan.estimatedWorkerBytes,error=null)}
        log("Negotiated ${plan.workers.size} workers; capacity estimate ${plan.memoryBudgetBytes} bytes")
        negotiatedPeers
    }
    suspend fun startPipeline(path:String,contextSize:Int=2048,keyCacheType:String="f16") {
        com.meshlit.operations.OperationsControl.get(context).gate.requireAllowed(com.meshlit.core.common.control.ManagedFeature.CLUSTER)
        val paired=negotiate(path,contextSize,keyCacheType)
        require(paired.size>=2){"Pair at least two approved workers"}
        startNative(path,ModelRuntimeOptions(LocalModelBackend.NATIVE_LOCAL,contextSize,keyCacheType),paired)
    }
    suspend fun startLocal(path:String,options:ModelRuntimeOptions) {
        require(options.backend==LocalModelBackend.NATIVE_LOCAL)
        startNative(path,options,emptyList())
    }
    private suspend fun startNative(path:String,options:ModelRuntimeOptions,paired:List<PipelinePeer>) {
        options.validate(GgufMetadata.read(File(path)).maxContext)
        val contextSize=options.contextSize
        ModelFiles.validateGguf(File(path))
        _status.update{it.copy(starting=true,error=null)}
        try {
            val result=inference.loadExternalEngine(path,contextSize) {
                lock.withLock { withContext(Dispatchers.IO) {
                    stopServerInternal();service()
                    try {
                        paired.forEach { bridges+=RpcTunnel.client(it.host,it.port,it.fingerprint,it.token,::log) }
                        val endpoints=bridges.joinToString(","){"127.0.0.1:${it.port}"}
                        val httpPort=port();val token=randomToken()
                        keyFile=File(context.filesDir,"pipeline-api-key").apply{writeText(token);setReadable(false,false);setReadable(true,true)}
                        com.meshlit.power.PowerRepository(context).loadBlockReason()?.let{error(it)}
                        val args=mutableListOf("-m",path,"-c","$contextSize","-t","${com.meshlit.models.AccelerationPreferences(context).effectiveThreads()}","--parallel","1",
                            "--host","127.0.0.1","--port","$httpPort","--api-key-file",keyFile!!.absolutePath,
                            "--cache-type-k",options.keyCacheType,"--cache-type-v","f16",
                            "--no-webui","--no-warmup","--fit","off","--slots","--slot-save-path",slotDirectory.absolutePath+"/")
                        if(paired.isEmpty()) args+=listOf("--device","none","-ngl","0")
                        else args+=listOf("--rpc",endpoints,"--device",paired.indices.joinToString(","){"RPC$it"},
                            "--split-mode","layer","--tensor-split",paired.joinToString(","){"${it.weight}"},"-ngl","99")
                        server=launch("server",args)
                        nativeSession=NativeSession(httpPort,token,path,options,paired.isEmpty())
                        val client=OkHttpClient.Builder().callTimeout(2,java.util.concurrent.TimeUnit.SECONDS).build()
                        withTimeout(180000) {
                            while(true) {
                                ensureActive();check(running(server!!)){"Native pipeline server exited; see runtime log"}
                                val ready=runCatching {client.newCall(Request.Builder().url("http://127.0.0.1:$httpPort/health")
                                    .header("Authorization","Bearer $token").build()).execute().use{it.code==200}}.getOrDefault(false)
                                if(ready) break
                                delay(500)
                            }
                        }
                        _status.update{it.copy(coordinator=paired.isNotEmpty(),local=paired.isEmpty())}
                        val selected=RpcPipelineEngine(httpPort,token,path,if(paired.isEmpty()) "llama-native-local" else "llama-rpc-layer") {lock.withLock {stopServerInternal();stopServiceIfIdle()}}
                        val process=server!!
                        scope.launch(Dispatchers.IO) {
                            process.waitFor()
                            if(server===process) {
                                _status.update{it.copy(coordinator=false,local=false,error="Native model process stopped; load again")}
                                inference.reportExternalFailure(selected,"Native pipeline stopped; recovery replay is not yet implemented")
                            }
                        }
                        selected
                    } catch(e:Throwable) {stopServerInternal();stopServiceIfIdle();throw e}
                } }
            }
            if(result is MeshlitResult.Failure) error(result.error.tag)
        } catch(cancelled:CancellationException){throw cancelled}
        catch(e:Exception){_status.update{it.copy(error=e.message)};throw e}
        finally {_status.update{it.copy(starting=false)};stopServiceIfIdle()}
    }
    private fun stopServerInternal(){val process=server;server=null;nativeSession=null;process?.destroy();bridges.forEach{it.close()};bridges.clear();keyFile?.delete();keyFile=null;_status.update{it.copy(coordinator=false,local=false)}}
    suspend fun checkpointList()=lock.withLock{withContext(Dispatchers.IO){checkpoints.list()}}
    suspend fun deleteCheckpoint(id:String)=lock.withLock{withContext(Dispatchers.IO){checkpoints.delete(id)}}
    suspend fun saveCheckpoint():NativeCheckpoint=inference.withExternalEngine("llama-native-local") {
        lock.withLock{withContext(Dispatchers.IO){
            val session=requireNotNull(nativeSession);check(session.local){"RPC/portable KV snapshots are not supported"}
            val estimate=GgufMetadata.read(File(session.path)).kvBytes(session.options.contextSize,session.options.keyCacheType)
                ?: error("Cannot budget a native KV snapshot for this architecture")
            require(estimate<=NativeCheckpointStore.MAX_BYTES){"Native KV snapshot estimate exceeds 128 MiB"}
            checkpointResources(estimate)
            val raw=File(slotDirectory,"checkpoint.bin")
            try {
                val response=slotCommand(session,"save",raw.name)
                val tokens=response["n_saved"]?.jsonPrimitive?.intOrNull ?: error("Native runtime did not confirm saved tokens")
                require(tokens in 1..session.options.contextSize && raw.length()==response["n_written"]?.jsonPrimitive?.longOrNull){"Native snapshot was not verified"}
                currentCoroutineContext().ensureActive()
                checkpoints.save(raw,ModelFiles.sha256(File(session.path)),ModelFiles.sha256(executable("server")),session.options,tokens)
            } finally{check(!raw.exists() || raw.delete()){"Cannot remove unencrypted checkpoint staging"}}
        }}
    }
    suspend fun restoreCheckpoint(id:String):NativeCheckpoint=inference.withExternalEngine("llama-native-local") {
        lock.withLock{withContext(Dispatchers.IO){
            val session=requireNotNull(nativeSession);check(session.local){"RPC/portable KV restore is unavailable"}
            checkpointResources(checkpoints.list().single{it.id==id}.bytes)
            val raw=File(slotDirectory,"checkpoint.bin")
            try {
                val item=checkpoints.restore(id,raw,ModelFiles.sha256(File(session.path)),ModelFiles.sha256(executable("server")),session.options)
                currentCoroutineContext().ensureActive()
                val response=slotCommand(session,"restore",raw.name)
                require(response["n_restored"]?.jsonPrimitive?.intOrNull==item.tokens && response["n_read"]?.jsonPrimitive?.longOrNull==item.bytes){"Native runtime did not confirm checkpoint restoration"}
                item
            } finally{check(!raw.exists() || raw.delete()){"Cannot remove unencrypted checkpoint staging"}}
        }}
    }
    private fun checkpointResources(bytes:Long){
        val memory=ActivityManager.MemoryInfo().also{context.getSystemService(ActivityManager::class.java).getMemoryInfo(it)}
        require(!memory.lowMemory && memory.availMem>bytes*3+128L*1024*1024){"Insufficient free memory for bounded checkpoint encryption/restore"}
        require(slotDirectory.usableSpace>bytes*2+32L*1024*1024){"Insufficient free disk space for checkpoint staging"}
    }
    private suspend fun slotCommand(session:NativeSession,action:String,filename:String):JsonObject=coroutineScope {
        require(action in setOf("save","restore") && filename=="checkpoint.bin")
        val client=OkHttpClient.Builder().callTimeout(60,java.util.concurrent.TimeUnit.SECONDS).followRedirects(false).build()
        val call=client.newCall(Request.Builder().url("http://127.0.0.1:${session.port}/slots/0?action=$action")
            .header("Authorization","Bearer ${session.token}")
            .post(buildJsonObject{put("filename",filename)}.toString().toRequestBody("application/json".toMediaType())).build())
        val watcher=launch(Dispatchers.IO){try{awaitCancellation()}finally{call.cancel()}}
        try {call.execute().use{response ->
            require(response.code==200){"Native checkpoint operation failed (HTTP ${response.code})"}
            val source=requireNotNull(response.body).source();require(!source.request(65537)){"Native checkpoint response exceeds limit"}
            Json.parseToJsonElement(source.readUtf8()).jsonObject
        }} finally{watcher.cancel();client.connectionPool.evictAll();client.dispatcher.executorService.shutdown()}
    }
    suspend fun stopPipeline(){inference.unloadModel();lock.withLock{stopServerInternal();stopServiceIfIdle()}}
    suspend fun stopAll(){inference.cancel();stopPipeline();stopWorker()}
    fun active()=worker!=null || server!=null || status.value.starting
    private fun stopServiceIfIdle()=synchronized(serviceState){
        if(!active() && serviceRequested){
            // The service checks live state and startId before stopping. A delayed
            // stop request must not destroy a newly requested model runtime.
            context.startService(Intent(context,PipelineService::class.java).setAction("stop-if-idle"))
            serviceRequested=false
        }
    }
}
