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
    val port: Int = 18894, val endpoints: List<ReplicaEndpoint>)

/** Manual, screen-bound replica listener. Membership/secrets never enter configuration export,
 * audit logs or agent tools. This journal does not automatically resume the model/task controller. */
class ReplicaHost(context: Context) {
    private val operations=com.meshlit.operations.OperationsControl.get(context).gate
    private val store = EncryptedCredentialStore(context, "recovery-replica-v1")
    private val json = Json { ignoreUnknownKeys = false }
    private val _running = MutableStateFlow(false); val running = _running.asStateFlow()
    private var server: NanoHTTPD? = null
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
    @Synchronized fun token(): String = store.get("token") ?: (UUID.randomUUID().toString() + UUID.randomUUID()).also { store.putCommitted("token", it) }
    fun saved(): String = store.get("configuration").orEmpty()
    private fun configuration(): ReplicaConfiguration = json.decodeFromString(saved())
    @Synchronized fun save(text: String) {
        require(text.toByteArray().size <= 32768)
        val config = json.decodeFromString<ReplicaConfiguration>(text)
        config.group.validate(); require(config.port in 1024..65535)
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
    @Synchronized fun start() {
        operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY)
        check(!com.meshlit.BuildConfig.PLAY_REVIEW)
        stop(); val config = configuration(); val node = acceptor(config); val secret = token()
        val next = object : NanoHTTPD("127.0.0.1", config.port) {
            override fun serve(session: IHTTPSession): Response {
                if (session.headers["origin"] != null || session.uri != "/replica" || session.method != Method.POST)
                    return newFixedLengthResponse(Response.Status.FORBIDDEN, "application/json", "{}")
                if (!MessageDigest.isEqual(session.headers["authorization"].orEmpty().toByteArray(), "Bearer $secret".toByteArray()))
                    return newFixedLengthResponse(Response.Status.UNAUTHORIZED, "application/json", "{}")
                if (!inbound.tryAcquire()) return newFixedLengthResponse(Response.Status.SERVICE_UNAVAILABLE, "application/json", "{}")
                return try {
                    operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY)
                    val size = session.headers["content-length"]?.toIntOrNull()
                    require(size != null && size in 1..32768 && session.headers["transfer-encoding"] == null)
                    require(session.headers["content-type"].orEmpty().substringBefore(';') == "application/json")
                    val bytes = ByteArray(size); var offset = 0
                    while (offset < size) { val n = session.inputStream.read(bytes, offset, size - offset); require(n > 0); offset += n }
                    val reply = synchronized(this@ReplicaHost) { check(server === this && _running.value); operations.requireAllowed(com.meshlit.core.common.control.ManagedFeature.RECOVERY); node.handle(json.decodeFromString<ReplicaRequest>(bytes.toString(Charsets.UTF_8))) }
                    newFixedLengthResponse(Response.Status.OK, "application/json", json.encodeToString(reply))
                } catch (_: Exception) { newFixedLengthResponse(Response.Status.CONFLICT, "application/json", "{\"error\":\"Replica refused request; inspect quorum and ownership\"}") } finally { inbound.release() }
            }
        }
        try { next.start(5000, false); server = next; _running.value = true } catch (e: Exception) { next.stop(); throw e }
    }
    @Synchronized fun stop() { server?.stop(); server = null; _running.value = false }
    @Synchronized fun rotateToken() { stop(); store.putCommitted("token", UUID.randomUUID().toString() + UUID.randomUUID()) }
    suspend fun read(): List<JournalCommit> = lock.withLockCompat {
        val config = configuration(); ReplicaJournal(config.group, ReplicaHttpTransport(config.endpoints)).read()
    }
    suspend fun append(entry: JournalEntry): JournalCommit = lock.withLockCompat {
        val config = configuration(); ReplicaJournal(config.group, ReplicaHttpTransport(config.endpoints)).append(entry)
    }
    /** Explicit human snapshot, never automatic upload/replay. Omit prompts, results, paths and credentials. */
    suspend fun publishJobMetadata(job: AgentJob): JournalCommit = lock.withLockCompat {
        val config = configuration(); val journal = ReplicaJournal(config.group, ReplicaHttpTransport(config.endpoints))
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
