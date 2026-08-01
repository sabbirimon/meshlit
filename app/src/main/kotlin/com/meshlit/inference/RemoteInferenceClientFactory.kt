package com.meshlit.inference

import com.meshlit.core.common.logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

/**
 * Owns the single shared [HttpClient] used by every peer-side
 * connection in the app. One engine per process; the FGS creates it
 * in `onCreate` and closes it in `onDestroy`. Constructing many
 * HttpClients is wasteful and can leak OkHttp connection pools.
 *
 * Each call to [build] returns a fresh [RemoteInferenceClient] that
 * borrows the shared engine — clients are cheap and request-scoped.
 *
 * Why OkHttp and not CIO client:
 *  - OkHttp is the standard Android HTTP stack; system-level proxy
 *    support, DNS, and cache are wired by the platform.
 *  - CIO client is also fine but offers no advantage here.
 *
 * Logging:
 *  - [Logging] plugin at [LogLevel.INFO] so peer traffic shows up in
 *    `adb logcat`. Users can grep `RemoteInferenceClient` /
 *    `io.ktor.client` to see wire activity.
 */
class RemoteInferenceClientFactory {

    private val log = logger("RemoteInferenceClientFactory")

    private val client: HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                encodeDefaults = true
            })
        }
        install(HttpTimeout) {
            // SSE streams are long-lived. These are upper bounds so a
            // hung peer doesn't tie up a coroutine forever.
            requestTimeoutMillis = REQUEST_TIMEOUT_MS
            connectTimeoutMillis = CONNECT_TIMEOUT_MS
            socketTimeoutMillis = SOCKET_TIMEOUT_MS
        }
        install(Logging) {
            level = LogLevel.INFO
        }
        expectSuccess = false
    }

    /** Build a per-call client targeting [baseUrl] (e.g. `http://192.168.1.42:8080`). */
    fun build(baseUrl: String): RemoteInferenceClient =
        RemoteInferenceClient(baseUrl = baseUrl, client = client)

    /** Close the underlying engine. Called from FGS `onDestroy`. */
    fun close() {
        try {
            client.close()
            log.info("factory.close", "HttpClient closed")
        } catch (t: Throwable) {
            log.warn("factory.close.exception", "HttpClient close threw", mapOf("err" to (t.message ?: "")))
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 5L * 60_000L  // 5 min
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val SOCKET_TIMEOUT_MS = 60_000L
    }
}