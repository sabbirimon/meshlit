package com.meshlit.inference

import com.meshlit.core.common.logger
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

/**
 * Owns the single shared [OkHttpClient] used by every peer-side
 * connection in the app. One engine per process; the FGS creates it
 * in `onCreate` and closes it in `onDestroy`. Constructing many
 * OkHttpClients is wasteful and can leak connection pools.
 *
 * Each call to [build] returns a fresh [RemoteInferenceClient] that
 * borrows the shared engine — clients are cheap and request-scoped.
 *
 * Why OkHttp and not Ktor client: Ktor 3.x's bytecode requires DEX
 * 040 (default from API 33) which would break the user-mandated
 * `minSdk = 23` floor. OkHttp is pure-Java and works on Android 6+.
 */
class RemoteInferenceClientFactory {

    private val log = logger("RemoteInferenceClientFactory")

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(CONNECT_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .writeTimeout(SOCKET_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .callTimeout(REQUEST_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun build(baseUrl: String): RemoteInferenceClient =
        RemoteInferenceClient(baseUrl = baseUrl, client = client)

    fun close() {
        try {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
            client.cache?.close()
            log.info("factory.close", "OkHttpClient closed")
        } catch (t: Throwable) {
            log.warn("factory.close.exception", "OkHttpClient close threw", mapOf("err" to (t.message ?: "")))
        }
    }

    companion object {
        private const val REQUEST_TIMEOUT_MS = 5L * 60_000L  // 5 min — SSE streams are long-lived
        private const val CONNECT_TIMEOUT_MS = 5_000L
        private const val SOCKET_TIMEOUT_MS = 60_000L
    }
}