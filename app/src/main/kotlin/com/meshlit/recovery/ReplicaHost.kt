package com.meshlit.recovery

import android.content.Context
import com.meshlit.core.federation.recovery.*
import com.meshlit.core.trust.EncryptedCredentialStore
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import com.meshlit.core.mcp.control.AgentJob
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.security.*
import java.security.spec.PKCS8EncodedKeySpec
import java.util.UUID

@Serializable data class ReplicaConfiguration(val group: ReplicaGroup, val localId: String,
    val port: Int = 18894, val endpoints: List<ReplicaEndpoint>, val lanAddress: String? = null)

/** Manual, screen-bound replica listener. Membership/secrets never enter configuration export,
 * audit logs or agent tools. This journal does not automatically resume the model/task controller. */
class ReplicaHost(private val context: Context, private val pipeline: com.meshlit.pipeline.PipelineHost) {
    private val operations=com.meshlit.operations.OperationsControl.get(context).gate
    private val store = EncryptedCredentialStore(context, "recovery-replica-v1")
    private val json = Json { ignoreUnknownKeys = false }
    private val _running = MutableStateFlow(false); val running = _running.asStateFlow()
    private val _paths = MutableStateFlow<List<ReplicaPathStatus>>(emptyList()); val paths = _paths.asStateFlow()
    private var server: NanoHTTPD? = null
    private var outgoing: ReplicaHttpTransport? = null
    private var outgoingEndpoints: List<ReplicaEndpoint>? = null
    private var transportEpoch = 0L
    private val lock = kotlinx.coroutines.sync.Mutex()
    private val inbound = java.util.concurrent.Semaphore(2)
    @Serializable private data class Identity(val publicKey: String, val privateKey: String)
    @Synchronized private fun identity(): Identity = store.get("identity")?.let { json.decodeFromString<Identity>(it) } ?: run {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair()
        Identity(pair.public.encoded.toByteString().base64(), pair.private.encoded.toByteString().base64()).also {
            store.putCommitted("identity", json.encodeToString(it))
        }
    }
    private fun key(): PrivateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(requireNotNull(identity().privateKey.decodeBase64()).toByteArray()))
    @Synchronized fun publicIdentity(): String = identity().publicKey
    fun certificateFingerprint(): String = pipeline.fingerprint()
    @Synchronized fun token(): String = store.get("token") ?: (UUID.randomUUID().toString() + UUID.randomUUID()).also { store.putCommitted("token", it) }
    fun saved(): String = store.get("configuration").orEmpty()
    private fun configuration(): ReplicaConfiguration = json.decodeFromString(saved())
    @Synchronized fun save(text: String) {
        require(text.toByteArray().size <= 32768)
        val config = json.decodeFromString<ReplicaConfiguration>(text)
        config.group.validate(); require(config.port in 1024..65535)
        require(config.lanAddress == null || isPrivateReplicaIpv4(config.lanAddress)) { "Use this device's private IPv4 address" }
        require(config.group.members.single { it.id == config.localId }.publicKey == publicIdentity()) { "Local public key must match enrolled membership" }
        require(config.endpoints.map { it.member }.toSet() == config.group.members.map { it.id }.toSet() && config.endpoints.size == config.group.members.size)
        config.endpoints.forEach { it.validate() }
        store.get("state")?.let { require(json.decodeFromString<ReplicaState>(it).groupFingerprint == config.group.fingerprint()) { "Existing journal membership cannot change" } }
        stop(); store.putCommitted("configuration", json.encodeToString(config))
    }
    private fun acceptor(config: ReplicaConfiguration) = ReplicaAcceptor(config.group, config.localId, key(), object : ReplicaPersistence {
        override fun load() = store.get("state")?.let { json.decodeFromString<ReplicaState>(it) }
        override fun save(state: ReplicaState) { store.putCommitted("state", json.encodeToString(state)) }
    })
    @Synchronized fun start(): String {
        operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY)
        check(!com.meshlit.BuildConfig.PLAY_REVIEW)
        stop(); val config = configuration(); val node = acceptor(config); val secret = token()
        val lan = config.lanAddress
        if (lan != null) {
            require(isPrivateReplicaIpv4(lan) && java.net.NetworkInterface.getByInetAddress(java.net.InetAddress.getByName(lan)) != null) {
                "The selected private address is not assigned to this phone"
            }
        }
        val next = object : NanoHTTPD(lan ?: "127.0.0.1", config.port) {
            private fun reply(status: Response.Status, body: String = "{}"): Response =
                newFixedLengthResponse(status, "application/json", body).apply { closeConnection(true) }

            override fun serve(session: IHTTPSession): Response {
                if (session.headers["origin"] != null || session.uri != "/replica" || session.method != Method.POST)
                    return reply(Response.Status.FORBIDDEN)
                if (lan != null && (!isPrivateReplicaIpv4(session.remoteIpAddress.orEmpty()) ||
                        !(context.applicationContext as com.meshlit.MeshlitApplication).meshlitFirewall
                            .decide(session.remoteIpAddress, null, null, config.port).allowed)
                ) return reply(Response.Status.FORBIDDEN)
                if (!MessageDigest.isEqual(session.headers["authorization"].orEmpty().toByteArray(), "Bearer $secret".toByteArray()))
                    return reply(Response.Status.UNAUTHORIZED)
                if (!inbound.tryAcquire()) return reply(Response.Status.SERVICE_UNAVAILABLE)
                return try {
                    operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY)
                    val size = session.headers["content-length"]?.toIntOrNull()
                    require(size != null && size in 1..32768 && session.headers["transfer-encoding"] == null)
                    require(session.headers["content-type"].orEmpty().substringBefore(';') == "application/json")
                    val bytes = ByteArray(size); var offset = 0
                    while (offset < size) { val n = session.inputStream.read(bytes, offset, size - offset); require(n > 0); offset += n }
                    val outcome = synchronized(this@ReplicaHost) { check(server === this && _running.value); operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY); node.handle(json.decodeFromString<ReplicaRequest>(bytes.toString(Charsets.UTF_8))) }
                    reply(Response.Status.OK, json.encodeToString(outcome))
                } catch (_: Exception) { reply(Response.Status.CONFLICT, "{\"error\":\"Replica refused request; inspect quorum and ownership\"}") } finally { inbound.release() }
            }
        }
        next.setAsyncRunner(com.meshlit.util.BoundedHttpRunner(6, "meshlit-replica-http"))
        if (lan != null) {
            val tls = pipeline.controlTlsContext()
            val protocols = tls.supportedSSLParameters.protocols.filter { it == "TLSv1.2" || it == "TLSv1.3" }
            check(protocols.isNotEmpty()) { "TLS 1.2 or later is required" }
            next.makeSecure(tls.serverSocketFactory, protocols.toTypedArray())
        }
        try { next.start(5000, false); server = next; _running.value = true } catch (e: Exception) { next.stop(); throw e }
        return if (lan == null) "Replica listener running on loopback" else "Private TLS listener running at $lan:${config.port}"
    }
    @Synchronized fun stop() {
        server?.stop(); server = null; _running.value = false
        transportEpoch++
        outgoing?.close(); outgoing = null; outgoingEndpoints = null
        _paths.value = emptyList()
    }
    /** Bounded metadata only: no endpoint URLs, bearer tokens or journal contents. */
    @Synchronized fun pathStatus(): List<ReplicaPathStatus> = outgoing?.pathStatus().orEmpty()
    @Synchronized private fun transport(config: ReplicaConfiguration): ReplicaHttpTransport {
        if (outgoing == null || outgoingEndpoints != config.endpoints) {
            outgoing?.close()
            val expected = ++transportEpoch
            outgoing = ReplicaHttpTransport(config.endpoints, onStatus = { value ->
                synchronized(this@ReplicaHost) { if (transportEpoch == expected) _paths.value = value }
            })
            outgoingEndpoints = config.endpoints
            _paths.value = requireNotNull(outgoing).pathStatus()
        }
        return requireNotNull(outgoing)
    }
    @Synchronized fun rotateToken() { stop(); store.putCommitted("token", UUID.randomUUID().toString() + UUID.randomUUID()) }
    suspend fun read(): List<JournalCommit> = lock.withLockCompat {
        val config = configuration(); ReplicaJournal(config.group, transport(config)).read()
    }
    suspend fun append(entry: JournalEntry): JournalCommit = lock.withLockCompat {
        val config = configuration(); ReplicaJournal(config.group, transport(config)).append(entry)
    }
    internal suspend fun chatCheckpoints():List<ReplicaChatCheckpoint> = chatRecoveryCheckpoints(read())

    /** Human-confirmed text only. Acquisition and subsequent writes are fenced against the task head. */
    internal suspend fun publishChat(draft:ReplicaChatDraft):JournalCommit = lock.withLockCompat {
        requireChatRecoveryTask(draft.taskId)
        val text=draft.transcript.encode();val digest=recoveryDigest(text)
        require(draft.modelSha256.matches(Regex("[a-f0-9]{64}")) && draft.epoch>0)
        val config=configuration();val journal=ReplicaJournal(config.group,transport(config))
        val task=draft.taskId;val owner=config.localId
        val history=journal.read().filter{it.value.entry.taskId==task}
        val prior=history.lastOrNull()?.value?.entry
        if(prior!=null) require(prior.owner==owner && prior.epoch==draft.epoch){"Conversation ownership changed. Recover the current checkpoint before publishing again."}
        val last=history.lastOrNull{it.value.entry.content==JournalContent.CHAT_TRANSCRIPT}
        last?.value?.entry?.let { snapshot ->
            require(snapshot.modelSha256==draft.modelSha256){"This recovery lineage uses a different model. Start a separate chat checkpoint."}
            if(snapshot.checkpointSha256==digest)return@withLockCompat requireNotNull(last)
        }
        val head=prior ?: run {
            require(draft.epoch==1L){"This recovery lineage is missing from the configured group"}
            journal.append(JournalEntry(recoveryDigest("chat-acquire:$task:$owner"),task,owner,1,JournalAction.ACQUIRE)).value.entry
        }
        val next=(last?.value?.entry?.offset ?: 0L)+1
        journal.append(JournalEntry(recoveryDigest("chat-snapshot:$task:$owner:${head.epoch}:${head.requestId}:$digest"),
            task,owner,head.epoch,JournalAction.SNAPSHOT,next,draft.modelSha256,digest,text,
            JournalContent.CHAT_TRANSCRIPT,expectedTaskHead=head.requestId))
    }

    /** Transfer journal ownership only. Importing the text is a separate local step and never launches inference. */
    internal suspend fun claimChat(checkpoint:ReplicaChatCheckpoint):ReplicaChatBinding = lock.withLockCompat {
        requireChatRecoveryTask(checkpoint.taskId)
        val config=configuration();val journal=ReplicaJournal(config.group,transport(config))
        val commits=journal.read()
        val current=chatRecoveryCheckpoints(commits).singleOrNull{it.taskId==checkpoint.taskId}
        require(current==checkpoint){"Checkpoint or owner changed. Read quorum and review the latest transcript."}
        val head=commits.last{it.value.entry.taskId==checkpoint.taskId}.value.entry
        require(head.action!=JournalAction.COMPLETE){"Conversation journal is closed"}
        val owner=config.localId
        val epoch=if(head.owner==owner && head.action==JournalAction.ACQUIRE && head.expectedTaskHead!=null)head.epoch else {
            val next=Math.addExact(head.epoch,1)
            journal.append(JournalEntry(recoveryDigest("chat-takeover:${head.taskId}:$owner:$next:${head.requestId}"),
                head.taskId,owner,next,JournalAction.ACQUIRE,expectedTaskHead=head.requestId)).value.entry.epoch
        }
        ReplicaChatBinding(head.taskId,epoch,checkpoint.requestId)
    }

    /** Explicit human snapshot, never automatic upload/replay. Omit prompts, results, paths and credentials. */
    suspend fun publishJobMetadata(job: AgentJob): JournalCommit = lock.withLockCompat {
        val config = configuration(); val journal = ReplicaJournal(config.group, transport(config))
        fun id(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).toByteString().hex()
        val task = id("agent-job:" + job.command.requestId)
        val prior = journal.read().lastOrNull { it.value.entry.taskId == task }?.value?.entry
        if (prior?.action == JournalAction.COMPLETE) return@withLockCompat journal.read().last { it.value.entry.taskId == task }
        require(prior == null || prior.owner == config.localId) { "Another owner holds this job reference; explicit transfer required" }
        val epoch = prior?.epoch ?: 1
        if (prior == null) journal.append(JournalEntry(id("acquire:$task:${config.localId}"), task, config.localId, epoch, JournalAction.ACQUIRE))
        val metadata = buildJsonObject {
            put("schema", 1); put("requestId", job.command.requestId); put("operation", job.command.operation.name)
            put("phase", job.phase.name); put("updatedAtMs", job.updatedAtMs)
            put("automaticReplayAllowed", false)
        }.toString()
        journal.append(JournalEntry(id("snapshot:$task:${job.updatedAtMs}:${job.phase}"), task, config.localId, epoch,
            if (job.terminal) JournalAction.COMPLETE else JournalAction.SNAPSHOT, replayText = metadata, content = JournalContent.JOB_METADATA))
    }
    private suspend fun <T> kotlinx.coroutines.sync.Mutex.withLockCompat(block: suspend () -> T): T = operations.run(com.meshlit.core.common.control.ManagedFeature.RECOVERY) {
        lock(); try { withContext(Dispatchers.IO) { block() } } finally { unlock() }
    }
}
