package com.meshlit.core.federation.recovery

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Explicitly approved path to the same enrolled replica, not another quorum member. */
@Serializable data class ReplicaPath(val url: String, val token: String, val certificatePin: String = "") {
    fun validate() {
        val parsed = url.toHttpUrl()
        require(parsed.encodedUsername.isEmpty() && parsed.encodedPassword.isEmpty() && parsed.query == null && parsed.fragment == null)
        require(parsed.encodedPath == "/replica" && token.length in 32..4096 && token.none { it.isWhitespace() })
        val loopback = parsed.host in setOf("127.0.0.1", "::1")
        require(parsed.scheme == "https" || loopback && parsed.scheme == "http") { "Replica transport requires HTTPS or an explicit loopback tunnel" }
        require(certificatePin.isEmpty() && loopback || certificatePin.matches(Regex("sha256/[A-Za-z0-9+/]{43}="))) { "Verify the remote TLS certificate pin" }
    }
    override fun toString() = "ReplicaPath(url=REDACTED, token=REDACTED)"
}

@Serializable enum class ReplicaPathSelection { PRIORITY, ADAPTIVE }
@Serializable data class ReplicaEndpoint(val member: String, val url: String, val token: String,
    val certificatePin: String = "", val alternatives: List<ReplicaPath> = emptyList(),
    val selection: ReplicaPathSelection = ReplicaPathSelection.ADAPTIVE) {
    fun paths() = listOf(ReplicaPath(url, token, certificatePin)) + alternatives
    fun validate() {
        require(member.matches(Regex("[A-Za-z0-9_-]{1,80}")) && alternatives.size <= 2)
        val paths = paths(); paths.forEach { it.validate() }
        require(paths.map { it.url.toHttpUrl() }.distinct().size == paths.size) { "Duplicate replica path" }
    }
    override fun toString() = "ReplicaEndpoint(member=$member, paths=${1 + alternatives.size}, credentials=REDACTED)"
}

data class ReplicaPathStatus(val member: String, val pathIndex: Int, val lastSucceeded: Boolean?,
    val latencyMs: Long?, val cooldownRemainingMs: Long, val identityRejected: Boolean,
    val attempts: Long, val successfulRequests: Long, val failedRequests: Long,
    val cancelledRequests: Long, val lastSelected: Boolean)

/** Only status reads may fail over. A mutation is sent once, after a matching member/group
 * response on that path. Failed mutation acknowledgement can be ambiguous; the journal reconciles.
 * Status exploration learns alternative latency; observations expire and never add quorum votes. */
class ReplicaHttpTransport(endpoints: List<ReplicaEndpoint>,
    private val requestTimeoutMs: Long = 4500, private val pathTimeoutMs: Long = 1500,
    private val failureCooldownMs: Long = 30000, private val observationTtlMs: Long = 60000,
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
    private val onStatus: (List<ReplicaPathStatus>) -> Unit = {},
) : ReplicaTransport, Closeable {
    private data class PathKey(val member: String, val index: Int)
    private data class Observation(val at: Long, val elapsed: Long, val success: Boolean,
        val groupFingerprint: String, val identityRejected: Boolean = false)
    private class IdentityMismatch : IOException("Replica path returned a different member or group")
    private data class Counters(var attempts: Long = 0, var succeeded: Long = 0, var failed: Long = 0, var cancelled: Long = 0)
    private val profiles: Map<String, List<ReplicaPath>>
    private val selections = endpoints.associate { it.member to it.selection }
    private val clients: Map<PathKey, OkHttpClient>
    private val observations = mutableMapOf<PathKey, Observation>()
    private val counters = mutableMapOf<PathKey, Counters>()
    private val selected = mutableMapOf<String, Int>()
    private val json = Json { ignoreUnknownKeys = false }
    @Volatile private var closed = false

    init {
        require(endpoints.size in setOf(3, 5) && endpoints.map { it.member }.distinct().size == endpoints.size)
        require(requestTimeoutMs in 100..4500 && pathTimeoutMs in 50..requestTimeoutMs)
        require(failureCooldownMs in 100..300000 && observationTtlMs in 100..300000)
        endpoints.forEach { it.validate() }
        profiles = endpoints.associate { it.member to it.paths() }
        clients = profiles.flatMap { (member, paths) -> paths.mapIndexed { index, path ->
            val builder = OkHttpClient.Builder().connectTimeout(pathTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(pathTimeoutMs, TimeUnit.MILLISECONDS).writeTimeout(pathTimeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(pathTimeoutMs, TimeUnit.MILLISECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            if (path.certificatePin.isNotEmpty()) builder.certificatePinner(
                CertificatePinner.Builder().add(path.url.toHttpUrl().host, path.certificatePin).build())
            PathKey(member, index) to builder.build()
        } }.toMap()
    }

    private fun fresh(observation: Observation?, fingerprint: String, now: Long) = observation != null &&
        observation.success && observation.groupFingerprint == fingerprint && now >= observation.at && now - observation.at <= observationTtlMs
    private fun cooldown(observation: Observation?, now: Long): Long =
        if (observation == null || observation.success) 0 else (failureCooldownMs - (now - observation.at)).coerceIn(0, failureCooldownMs)

    @Synchronized fun pathStatus(): List<ReplicaPathStatus> {
        val now = clock()
        return clients.keys.map { key ->
            val observed = observations[key]
            val count = counters[key] ?: Counters()
            ReplicaPathStatus(key.member, key.index, observed?.success, observed?.takeIf { it.success }?.elapsed,
                cooldown(observed, now), observed?.identityRejected == true,
                count.attempts, count.succeeded, count.failed, count.cancelled, selected[key.member] == key.index)
        }
    }
    private fun publishStatus() { runCatching { onStatus(pathStatus()) } }

    @Synchronized private fun ordered(member: String, fingerprint: String, explore: Boolean): List<PathKey> {
        check(!closed) { "Replica transport is stopped" }
        require(profiles.containsKey(member)) { "Replica member is not configured" }
        val now = clock()
        val eligible = clients.keys.filter { it.member == member && observations[it]?.identityRejected != true && cooldown(observations[it], now) == 0L }
        if (selections.getValue(member) == ReplicaPathSelection.PRIORITY) return eligible.sortedBy { it.index }
        return eligible.sortedWith(compareBy<PathKey> {
                val measured = fresh(observations[it], fingerprint, now)
                if (if (explore) !measured else measured) 0 else 1
            }.thenBy { observations[it]?.takeIf { item -> fresh(item, fingerprint, now) }?.elapsed ?: Long.MAX_VALUE }
                .thenBy { it.index })
    }

    override suspend fun call(member: ReplicaMember, request: ReplicaRequest): ReplicaReply = withTimeout(requestTimeoutMs) {
        require(request.method in setOf("status", "prepare", "accept", "commit")) { "Unknown replica method" }
        require(json.encodeToString(request).toByteArray().size <= 32768) { "Replica request exceeds budget" }
        val readOnly = request.method == "status"
        val choices = ordered(member.id, request.groupFingerprint, explore = readOnly)
        check(choices.isNotEmpty()) { "Replica paths unavailable or cooling down" }
        for (path in choices) {
            val verified = synchronized(this@ReplicaHttpTransport) { fresh(observations[path], request.groupFingerprint, clock()) }
            // Check an unobserved/stale path before sending a mutation to it.
            if (readOnly || !verified) {
                try {
                    val reply = exchange(path, ReplicaRequest(request.groupFingerprint, "status"))
                    if (readOnly) return@withTimeout reply
                } catch (cancelled: CancellationException) { throw cancelled }
                catch (identity: IdentityMismatch) { throw identity }
                catch (_: Exception) { continue }
            }
            // Outside the retry catch: a missing acknowledgement can still mean a durable write.
            return@withTimeout exchange(path, request)
        }
        throw IOException("No configured replica path answered a verified status read")
    }

    private suspend fun exchange(key: PathKey, request: ReplicaRequest): ReplicaReply {
        val path = profiles.getValue(key.member)[key.index]
        val started = clock()
        var dispatched = false
        try {
            val call = clients.getValue(key).newCall(Request.Builder().url(path.url)
                .header("Authorization", "Bearer ${path.token}")
                .post(json.encodeToString(request).toRequestBody("application/json".toMediaType())).build())
            val reply = suspendCancellableCoroutine<ReplicaReply> { cont ->
                // Cancellation remains attached while the bounded response body is being read.
                cont.invokeOnCancellation { call.cancel() }
                synchronized(this@ReplicaHttpTransport) {
                    check(!closed) { "Replica transport is stopped" }
                    counters.getOrPut(key) { Counters() }.attempts++
                    selected[key.member] = key.index
                    dispatched = true
                    call.enqueue(object : Callback {
                        override fun onFailure(call: Call, e: IOException) {
                            if (cont.isActive) cont.resumeWithException(IOException("Replica path did not acknowledge the request"))
                        }
                        override fun onResponse(call: Call, response: Response) {
                            try {
                                val value = response.use {
                                    require(it.isSuccessful && it.header("Content-Type").orEmpty().substringBefore(';').trim() == "application/json")
                                    val source = requireNotNull(it.body).source()
                                    require(!source.request(2L * 1024 * 1024 + 1))
                                    json.decodeFromString<ReplicaReply>(source.readUtf8())
                                }
                                if (value.member != key.member || value.state.groupFingerprint != request.groupFingerprint) throw IdentityMismatch()
                                if (cont.isActive) cont.resume(value)
                            } catch (e: Exception) {
                                if (cont.isActive) cont.resumeWithException(if (e is IdentityMismatch) e else IOException("Replica reply invalid, rejected or over budget"))
                            }
                        }
                    })
                }
                publishStatus()
            }
            synchronized(this) {
                observations[key] = Observation(clock(), (clock() - started).coerceAtLeast(0), true, request.groupFingerprint)
                counters.getValue(key).succeeded++
            }
            return reply
        } catch (cancelled: CancellationException) {
            if (dispatched) synchronized(this) { counters.getValue(key).cancelled++ }
            throw cancelled
        }
        catch (e: Exception) {
            if (dispatched) synchronized(this) {
                observations[key] = Observation(clock(), (clock() - started).coerceAtLeast(0), false, request.groupFingerprint, e is IdentityMismatch)
                counters.getValue(key).failed++
            }
            throw e
        } finally { publishStatus() }
    }

    @Synchronized override fun close() {
        if (closed) return
        closed = true
        clients.values.forEach { it.dispatcher.cancelAll(); it.connectionPool.evictAll(); it.dispatcher.executorService.shutdown() }
    }
}
