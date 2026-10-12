package com.meshlit.peering

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.discovery.PeerAdvertisement
import com.meshlit.core.trust.*
import com.meshlit.disco.PeerRepository
import com.meshlit.disco.isPrivateRFC1918
import com.meshlit.settings.SettingsRepository
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.security.SecureRandom
import java.util.concurrent.TimeUnit

@Serializable data class RelayJoin(val code: String, val name: String, val proof: IdentityProof)
@Serializable data class RelayMember(val nodeId: String, val name: String, val publicKey: String, val lastSeenMs: Long)
@Serializable data class RelayWelcome(val proof: IdentityProof, val token: String, val members: List<RelayMember>, val codeProof: String = "")
data class RelayState(val enabled: Boolean = false, val code: String = "", val expiresAtMs: Long = 0,
    val members: List<RelayMember> = emptyList(), val joinedUrl: String? = null, val error: String? = null)

/** Designated-node membership relay. Host is reachable through LAN, VPN or an HTTPS reverse proxy. */
class RelayManager(private val context: Context, private val identity: DeviceIdentity,
    private val peers: PeerRepository, private val settings: SettingsRepository) {
    private val app get() = context.applicationContext as MeshlitApplication
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutable = MutableStateFlow(RelayState())
    val state = mutable.asStateFlow()
    private var server: Server? = null
    private var heartbeat: Job? = null
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS).readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()
    private data class MemberSession(val member: RelayMember, val token: String)
    private val sessions = mutableMapOf<String, MemberSession>()
    private val attempts = mutableMapOf<String, Pair<Long, Int>>()
    private var codeAttempts = 0
    private val usedNonces = mutableMapOf<String, Long>()
    init { scope.launch { while (isActive) { delay(5000); if (server != null) synchronized(this@RelayManager) { publishMembers() } } } }
    fun invite(): String {
        val state = mutable.value; check(state.enabled)
        val key = identity.get(app.nodeIdHex)
        return ClusterInvite("http://${app.localIpAddress}:$port", state.code, key.nodeId, key.publicKey, state.expiresAtMs).encode()
    }
    private fun codeProof(code: String, welcome: RelayWelcome): String {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(javax.crypto.spec.SecretKeySpec(code.toByteArray(Charsets.UTF_8), "HmacSHA256"))
        return PeerCrypto.hex(mac.doFinal(PeerCrypto.json.encodeToString(RelayWelcome.serializer(), welcome.copy(codeProof = "")).toByteArray(Charsets.UTF_8)))
    }
    val port = 8081
    @Synchronized fun enable() {
        if (server != null) return
        identity.get(app.nodeIdHex)
        com.meshlit.inference.InferenceForegroundService.startForInference(context)
        val candidate = Server()
        try {
            candidate.start(10_000, false)
            server = candidate
            mutable.value = mutable.value.copy(enabled = true, error = null)
            rotateCode()
        } catch (e: Exception) { candidate.stop(); throw IllegalStateException("relay.start_failed: ${e.message}") }
    }
    @Synchronized fun disable() {
        server?.stop(); server = null; sessions.clear(); attempts.clear()
        mutable.value = mutable.value.copy(enabled = false, code = "", expiresAtMs = 0, members = emptyList())
    }
    @Synchronized fun rotateCode() {
        check(server != null)
        codeAttempts = 0
        mutable.value = mutable.value.copy(code = "%06d".format(java.util.Locale.ROOT, SecureRandom().nextInt(1_000_000)),
            expiresAtMs = System.currentTimeMillis() + 600_000)
    }
    @Synchronized fun revoke(node: String) { sessions.remove(node); publishMembers() }
    @Synchronized private fun publishMembers(): List<RelayMember> {
        val cutoff = System.currentTimeMillis() - 90_000
        sessions.entries.removeAll { it.value.member.lastSeenMs < cutoff }
        val members = sessions.values.map { it.member }
        mutable.value = mutable.value.copy(members = members)
        return members
    }
    fun leave() { heartbeat?.cancel(); heartbeat = null; mutable.value = mutable.value.copy(joinedUrl = null) }
    suspend fun join(raw: String, enteredCode: String) = withContext(Dispatchers.IO) {
        val invite = if (raw.trim().startsWith("meshlit://join")) ClusterInvite.decode(raw) else null
        val code = invite?.code ?: enteredCode
        val rawAddress = invite?.url ?: raw
        require(code.matches(Regex("[0-9]{6}"))) { "relay.invalid_code: Enter six digits" }
        val uri = URI(rawAddress.trim().trimEnd('/'))
        require(uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.userInfo == null && uri.rawQuery == null && uri.fragment == null) { "relay.invalid_endpoint" }
        require(uri.scheme == "https" || isPrivateRFC1918(uri.host) || uri.host in setOf("localhost", "127.0.0.1")) { "relay.https_required: Use HTTPS for internet enrollment" }
        val key = identity.get(app.nodeIdHex); val nonce = PeerCrypto.randomHex()
        val req = RelayJoin(code, app.displayName.take(128), PeerCrypto.prove(key, nonce))
        val base = rawAddress.trim().trimEnd('/')
        val response = request("$base/v1/relay/join", PeerCrypto.json.encodeToString(RelayJoin.serializer(), req))
        val welcome = PeerCrypto.json.decodeFromString(RelayWelcome.serializer(), response)
        require(welcome.proof.nodeId != key.nodeId) { "relay.self_join" }
        require(java.security.MessageDigest.isEqual(PeerCrypto.bytes(codeProof(code, welcome)), PeerCrypto.bytes(welcome.codeProof))) { "relay.invalid_code_proof" }
        require(welcome.token.matches(Regex("[a-f0-9]{64}"))) { "relay.invalid_session" }
        require(PeerCrypto.verify(welcome.proof, invite?.nodeId ?: welcome.proof.nodeId, invite?.publicKey ?: welcome.proof.publicKey, nonce)) { "relay.invalid_identity" }
        // Persist a discovered endpoint; remote inference trust needs its own explicit approval.
        settings.upsertEndpoint(com.meshlit.devices.PairingPayload("Relay ${welcome.proof.nodeId.take(8)}", base,
            welcome.proof.nodeId, "wan", welcome.proof.publicKey).toRemoteEndpoint())
        peers.ingest(PeerAdvertisement(welcome.proof.nodeId, uri.host, if (uri.port < 0) if (uri.scheme == "https") 443 else 80 else uri.port,
            "wan", PeerCrypto.fingerprint(welcome.proof.publicKey), ttlSec = 90, transport = "relay"))
        heartbeat?.cancel()
        mutable.value = mutable.value.copy(joinedUrl = base, error = null)
        heartbeat = scope.launch {
            try {
                while (isActive) {
                    delay(25_000)
                    request("$base/v1/relay/heartbeat", "{}", welcome.token)
                    peers.ingest(PeerAdvertisement(welcome.proof.nodeId, uri.host, if (uri.port < 0) if (uri.scheme == "https") 443 else 80 else uri.port,
                        "wan", PeerCrypto.fingerprint(welcome.proof.publicKey), ttlSec = 90, transport = "relay"))
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.value = mutable.value.copy(joinedUrl = null, error = "relay.disconnected: ${e.message}"); peers.evict(welcome.proof.nodeId) }
        }
    }
    private fun request(url: String, body: String, token: String? = null): String {
        val builder = Request.Builder().url(url).post(body.toRequestBody("application/json".toMediaType()))
        token?.let { builder.header("Authorization", "Bearer $it") }
        client.newCall(builder.build()).execute().use { response ->
            require(response.isSuccessful) { "relay.http_${response.code}: Enrollment or session rejected" }
            val source = response.body?.source() ?: error("relay.empty_response")
            require(!source.request(65537)) { "relay.response_too_large" }
            return source.readUtf8()
        }
    }
    private fun readBounded(session: NanoHTTPD.IHTTPSession, max: Int): String {
        val length = session.headers["content-length"]?.toIntOrNull() ?: 0
        require(length in 1..max)
        val data = ByteArray(length); var offset = 0
        while (offset < length) { val n = session.inputStream.read(data, offset, length - offset); require(n > 0); offset += n }
        return data.toString(Charsets.UTF_8)
    }
    private inner class Server : NanoHTTPD("0.0.0.0", port) {
        override fun serve(session: IHTTPSession): Response = synchronized(this@RelayManager) {
            // Enrollment/heartbeat may reject before consuming a POST body. Close the
            // connection so leftover bytes never become the next request's headers.
            fun reply(status: Response.Status, text: String) = newFixedLengthResponse(status, "application/json", text).apply { closeConnection(true) }
            try {
                val authority: PairingAuthority = com.meshlit.di.koinInject()
                if (session.method == Method.GET && session.uri == "/v1/identity") {
                    val nonce = session.parameters["nonce"]?.singleOrNull().orEmpty()
                    val proof = PeerCrypto.prove(identity.get(app.nodeIdHex), nonce)
                    return@synchronized reply(Response.Status.OK, PeerCrypto.json.encodeToString(IdentityProof.serializer(), proof))
                }
                if (session.method == Method.POST && session.uri == "/v1/handshake") {
                    val request = PeerCrypto.json.decodeFromString(HandshakeRequest.serializer(), readBounded(session, 8192))
                    return@synchronized when (val result = authority.handshake(request)) {
                        is com.meshlit.core.common.MeshlitResult.Success -> reply(Response.Status.OK, PeerCrypto.json.encodeToString(HandshakeResponse.serializer(), result.value))
                        is com.meshlit.core.common.MeshlitResult.Failure -> reply(Response.Status.FORBIDDEN, result.error.tag)
                    }
                }
                val readPath = session.uri in setOf("/v1/health", "/v1/model", "/v1/runtimes", "/v1/capabilities") && session.method == Method.GET
                val inferPath = session.uri == "/v1/infer" && session.method == Method.POST
                if (readPath || inferPath) {
                    if (inferPath && authority.authenticate(session.headers["authorization"]?.removePrefix("Bearer ").orEmpty()) == null)
                        return@synchronized reply(Response.Status.UNAUTHORIZED, "pairing.required")
                    val request = Request.Builder().url("http://127.0.0.1:${app.httpServerPort}${session.uri}")
                    if (inferPath) request.post(readBounded(session, 65536).toRequestBody("application/json".toMediaType()))
                    val response = client.newBuilder().readTimeout(120, TimeUnit.SECONDS).build().newCall(request.build()).execute()
                    val body = response.body
                    val stream = object : java.io.FilterInputStream(body.byteStream()) {
                        override fun close() { try { super.close() } finally { response.close() } }
                    }
                    val status = Response.Status.lookup(response.code) ?: Response.Status.INTERNAL_ERROR
                    return@synchronized newChunkedResponse(status, body.contentType()?.toString() ?: "application/json", stream)
                }
                if (session.method != Method.POST) return@synchronized reply(Response.Status.NOT_FOUND, "{}")
                if (session.uri == "/v1/relay/heartbeat") {
                    val token = session.headers["authorization"]?.removePrefix("Bearer ").orEmpty()
                    if (!token.matches(Regex("[a-f0-9]{64}"))) return@synchronized reply(Response.Status.UNAUTHORIZED, "{}")
                    publishMembers()
                    val found = sessions.entries.firstOrNull { PeerCrypto.bytes(it.value.token).let { stored ->
                        runCatching { java.security.MessageDigest.isEqual(stored, PeerCrypto.bytes(token)) }.getOrDefault(false)
                    } } ?: return@synchronized reply(Response.Status.UNAUTHORIZED, "{}")
                    found.setValue(found.value.copy(member = found.value.member.copy(lastSeenMs = System.currentTimeMillis())))
                    publishMembers(); return@synchronized reply(Response.Status.OK, "{}")
                }
                if (session.uri != "/v1/relay/join") return@synchronized reply(Response.Status.NOT_FOUND, "{}")
                val now = System.currentTimeMillis(); val ip = session.remoteIpAddress.orEmpty()
                val attempt = attempts[ip]?.takeIf { now - it.first < 60_000 } ?: (now to 0)
                if (attempt.second >= 5 || codeAttempts >= 20) return@synchronized reply(Response.Status.SERVICE_UNAVAILABLE, "{}")
                if (attempts.size > 256) attempts.entries.removeAll { now - it.value.first > 60_000 }
                if (attempts.size > 256) return@synchronized reply(Response.Status.SERVICE_UNAVAILABLE, "{}")
                attempts[ip] = attempt.first to attempt.second + 1; codeAttempts++
                val length = session.headers["content-length"]?.toIntOrNull() ?: 0
                if (length !in 1..8192) return@synchronized reply(Response.Status.BAD_REQUEST, "{}")
                val data = ByteArray(length); var offset = 0
                while (offset < length) { val n = session.inputStream.read(data, offset, length - offset); require(n > 0); offset += n }
                val join = PeerCrypto.json.decodeFromString(RelayJoin.serializer(), data.toString(Charsets.UTF_8))
                val state = mutable.value
                if (now > state.expiresAtMs || !java.security.MessageDigest.isEqual(join.code.toByteArray(), state.code.toByteArray()))
                    return@synchronized reply(Response.Status.UNAUTHORIZED, "{}")
                require(join.name.length <= 128 && join.proof.nodeId.length in 1..128)
                require(PeerCrypto.verify(join.proof, join.proof.nodeId, join.proof.publicKey, join.proof.nonce, now))
                require(join.proof.nodeId != app.nodeIdHex)
                usedNonces.entries.removeAll { it.value < now - 120_000 }
                require(!usedNonces.containsKey(join.proof.nonce) && usedNonces.size < 512) { "relay.replayed_join" }
                usedNonces[join.proof.nonce] = now
                require(sessions.size < 64 || sessions.containsKey(join.proof.nodeId))
                val key = identity.get(app.nodeIdHex)
                val token = PeerCrypto.randomHex()
                sessions[join.proof.nodeId] = MemberSession(RelayMember(join.proof.nodeId, join.name, join.proof.publicKey, now), token)
                val unsigned = RelayWelcome(PeerCrypto.prove(key, join.proof.nonce), token, publishMembers())
                val welcome = unsigned.copy(codeProof = codeProof(state.code, unsigned))
                reply(Response.Status.OK, PeerCrypto.json.encodeToString(RelayWelcome.serializer(), welcome))
            } catch (e: Exception) { reply(Response.Status.BAD_REQUEST, "{}") }
        }
    }
}
