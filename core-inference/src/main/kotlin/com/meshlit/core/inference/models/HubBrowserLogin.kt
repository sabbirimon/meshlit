package com.meshlit.core.inference.models

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import java.net.InetAddress
import java.net.ServerSocket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

internal const val HUB_CLIENT = "https://cdn.jsdelivr.net/gh/sabbirimon/meshlit@hf-oauth-v1/docs/oauth-client.json"

internal suspend fun browserGrant(open: suspend (String) -> Unit): Request = withTimeout(180_000) {
    withContext(Dispatchers.IO) {
        val encoder = Base64.getUrlEncoder().withoutPadding()
        val random = SecureRandom()
        fun nonce() = encoder.encodeToString(ByteArray(32).also(random::nextBytes))
        val state = nonce()
        val verifier = nonce()
        val challenge = encoder.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))
        ServerSocket(0, 4, InetAddress.getByName("127.0.0.1")).use { server ->
            server.soTimeout = 500
            val redirect = "http://127.0.0.1:${server.localPort}/hf/callback"
            val url = "https://huggingface.co/oauth/authorize".toHttpUrl().newBuilder()
                .addQueryParameter("client_id", HUB_CLIENT).addQueryParameter("redirect_uri", redirect)
                .addQueryParameter("response_type", "code").addQueryParameter("scope", "profile read-repos gated-repos")
                .addQueryParameter("state", state).addQueryParameter("code_challenge_method", "S256")
                .addQueryParameter("code_challenge", challenge).build()
            withContext(Dispatchers.Main) { open(url.toString()) }
            var code: String? = null
            while (code == null) {
                currentCoroutineContext().ensureActive()
                val socket = try { server.accept() } catch (_: SocketTimeoutException) { continue }
                socket.use {
                    it.soTimeout = 1000
                    val input = it.getInputStream().buffered()
                    val request = StringBuilder()
                    try {
                        while (request.length < 8192) {
                            val next = input.read()
                            if (next < 0 || next == 10) break
                            request.append(next.toChar())
                        }
                    } catch (_: SocketTimeoutException) { return@use }
                    val parts = request.toString().trimEnd().split(' ')
                    val target = parts.getOrNull(1)
                    val callback = if (parts.firstOrNull()=="GET" && target?.startsWith("/hf/callback?")==true)
                        redirect.toHttpUrl().resolve(target) else null
                    val valid = callback?.queryParameter("state")==state && callback.encodedPath=="/hf/callback"
                    val status = if (valid) "200 OK" else "400 Bad Request"
                    runCatching { it.getOutputStream().write("HTTP/1.1 $status\r\nConnection: close\r\nCache-Control: no-store\r\nContent-Type: text/plain\r\n\r\nReturn to Meshlit for the sign-in result.".toByteArray()) }
                    if (valid) {
                        require(callback?.queryParameter("error")==null) { "Hugging Face authorization declined" }
                        code = requireNotNull(callback?.queryParameter("code")?.takeIf { v -> v.length in 1..4096 }) { "Missing authorization code" }
                    }
                }
            }
            Request.Builder().url("https://huggingface.co/oauth/token").post(FormBody.Builder()
                .add("grant_type","authorization_code").add("client_id",HUB_CLIENT).add("code",checkNotNull(code))
                .add("redirect_uri",redirect).add("code_verifier",verifier).build()).build()
        }
    }
}
