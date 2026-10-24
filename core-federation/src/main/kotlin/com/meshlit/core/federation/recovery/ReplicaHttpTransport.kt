package com.meshlit.core.federation.recovery

import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Serializable data class ReplicaEndpoint(val member: String, val url: String, val token: String, val certificatePin: String = "") {
    fun validate() {
        val parsed = HttpUrl.parseCompat(url)
        require(parsed.encodedUsername.isEmpty() && parsed.encodedPassword.isEmpty() && parsed.query == null && parsed.fragment == null)
        require(parsed.encodedPath == "/replica" && token.length in 32..4096 && token.none { it.isWhitespace() })
        val loopback = parsed.host in setOf("127.0.0.1", "::1")
        require(parsed.scheme == "https" || loopback && parsed.scheme == "http") { "Replica transport requires HTTPS or an explicit loopback tunnel" }
        require(loopback || certificatePin.matches(Regex("sha256/[A-Za-z0-9+/]{43}="))) { "Verify the remote TLS certificate pin" }
    }
}
private fun HttpUrl.Companion.parseCompat(url: String): HttpUrl = with(HttpUrl.Companion) { url.toHttpUrl() }

class ReplicaHttpTransport(endpoints: List<ReplicaEndpoint>) : ReplicaTransport {
    private val profiles = endpoints.associateBy { it.member }
    private val json = Json { ignoreUnknownKeys = false }
    private val clients: Map<String, OkHttpClient>
    init {
        require(endpoints.size in 3..5 && profiles.size == endpoints.size)
        endpoints.forEach { it.validate() }
        clients = endpoints.associate { endpoint ->
            val builder = OkHttpClient.Builder().connectTimeout(3, TimeUnit.SECONDS).readTimeout(5, TimeUnit.SECONDS)
                .writeTimeout(5, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS)
                .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
            if (endpoint.certificatePin.isNotEmpty()) builder.certificatePinner(CertificatePinner.Builder().add(HttpUrl.parseCompat(endpoint.url).host, endpoint.certificatePin).build())
            endpoint.member to builder.build()
        }
    }
    override suspend fun call(member: ReplicaMember, request: ReplicaRequest): ReplicaReply {
        val profile = profiles.getValue(member.id)
        val call = clients.getValue(member.id).newCall(Request.Builder().url(profile.url)
            .header("Authorization", "Bearer ${profile.token}")
            .post(json.encodeToString(request).toRequestBody("application/json".toMediaType())).build())
        val response = suspendCancellableCoroutine<Response> { cont ->
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, e: java.io.IOException) { if (cont.isActive) cont.resumeWithException(e) }
                override fun onResponse(call: Call, response: Response) { cont.resume(response) { _, value, _ -> value.close() } }
            })
            cont.invokeOnCancellation { call.cancel() }
        }
        return withContext(Dispatchers.IO) { response.use {
            require(it.isSuccessful && it.header("Content-Type").orEmpty().startsWith("application/json")) { "Replica rejected the request" }
            val source = requireNotNull(it.body).source(); require(!source.request(2L * 1024 * 1024 + 1)) { "Replica reply exceeds budget" }
            json.decodeFromString<ReplicaReply>(source.readUtf8())
        } }
    }
}
