package com.meshlit.colibri

import android.content.Context
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.inference.models.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.workspace.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable data class ColibriConfiguration(val endpoint: String = "", val model: String = "",
    val hostAccess: Boolean = false, val agentSwitching: Boolean = false, val preferHost: Boolean = false) {
    fun validate() {
        if (endpoint.isNotBlank()) ColibriClient.validateEndpoint(endpoint)
        if (model.isNotBlank()) ColibriClient.validModel(model)
        require(!hostAccess || endpoint.isNotBlank()) { "Configure the Colibri endpoint first" }
    }
}

/** Human-only persisted configuration; agent requests never modify it or expose keys. */
class ColibriBackend(context: Context) {
    private val store = EncryptedCredentialStore(context, "colibri-host")
    private val gate = com.meshlit.operations.OperationsControl.get(context).gate
    private val client = ColibriClient()
    private val lock = Any()
    private val _config = MutableStateFlow(runCatching { store.get("config")?.let { Json.decodeFromString<ColibriConfiguration>(it).also { c -> c.validate() } } }.getOrNull() ?: ColibriConfiguration())
    val config: StateFlow<ColibriConfiguration> = _config.asStateFlow()
    private val sessions = ColibriSessionModes { android.os.SystemClock.elapsedRealtime() }
    private val jobs = mutableSetOf<Job>()
    private var observed: List<String> = emptyList()
    private var observedAt: Long? = null

    fun hasToken(): Boolean = !store.get("token").isNullOrBlank()
    fun saveHuman(value: ColibriConfiguration, newToken: String = "") = synchronized(lock) {
        value.validate()
        require(newToken.isEmpty() || newToken.length in 32..4096 && newToken.none { it.code !in 33..126 }) { "Use a private bearer key of 32–4096 characters" }
        val endpointChanged = value.endpoint != _config.value.endpoint
        require(!value.hostAccess || newToken.isNotBlank() || !endpointChanged && hasToken()) { "Save a private key for this exact host" }
        val keepObservation = !endpointChanged && newToken.isEmpty() && value.hostAccess && _config.value.hostAccess
        if (endpointChanged) store.remove("token")
        if (newToken.isNotBlank()) store.putCommitted("token", newToken)
        store.putCommitted("config", Json.encodeToString(value))
        jobs.toList().forEach { it.cancel(CancellationException("Colibri permission or configuration changed")) }
        sessions.clear(); if (!keepObservation) { observed = emptyList(); observedAt = null }; _config.value = value
    }
    fun freshModel(): Boolean = synchronized(lock) {
        observedAt?.let { android.os.SystemClock.elapsedRealtime() - it in 0..300_000 } == true && _config.value.model in observed
    }
    fun decision(chat: String, humanMode: ColibriMode, chatAgentGrant: Boolean, localReady: Boolean): ColibriDecision = synchronized(lock) {
        colibriDecision(sessions.effective(chat, humanMode, _config.value.agentSwitching, chatAgentGrant),
            _config.value.hostAccess, freshModel(), localReady, _config.value.preferHost)
    }
    fun requestByAgent(chat: String, mode: ColibriMode, chatGrant: Boolean) = synchronized(lock) {
        check(!com.meshlit.BuildProfile.coreCandidate) { "Colibri is unavailable in Core candidate" }
        gate.requireAllowed(ManagedFeature.CLOUD, true)
        sessions.request(chat, mode, _config.value.hostAccess && _config.value.agentSwitching, chatGrant)
    }
    fun clearAgentModes() = synchronized(lock) { sessions.clear() }

    private suspend fun <T> authorized(action: suspend (ColibriConfiguration, String) -> T): T = gate.run(ManagedFeature.CLOUD) { coroutineScope {
        check(!com.meshlit.BuildProfile.coreCandidate) { "Colibri is unavailable in Core candidate" }
        val job = currentCoroutineContext().job
        val snapshot = synchronized(lock) {
            val c = _config.value; c.validate(); check(c.hostAccess) { "Colibri host access is off" }
            val token = store.get("token").orEmpty(); jobs.add(job); c to token
        }
        try { action(snapshot.first, snapshot.second).also { ensureActive(); check(_config.value == snapshot.first) { "Colibri authorization changed" } } }
        finally { synchronized(lock) { jobs.remove(job) } }
    } }
    suspend fun refreshModels(): List<String> = authorized { c, token ->
        client.models(c.endpoint, token).also { ids -> synchronized(lock) {
            check(_config.value == c); observed = ids; observedAt = android.os.SystemClock.elapsedRealtime()
        } }
    }
    suspend fun generate(messages: List<OnlineMessage>, system: String, maxTokens: Int, temperature: Float): Pair<String, OnlineReply> = authorized { c, token ->
        check(freshModel()) { "Refresh the selected Colibri model before sending" }
        c.model to client.generate(c.endpoint, token, c.model, messages, system, maxTokens, temperature)
    }
}
