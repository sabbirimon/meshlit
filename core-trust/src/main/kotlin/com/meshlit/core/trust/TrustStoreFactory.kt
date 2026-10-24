package com.meshlit.core.trust

import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.NodeId
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory [TrustStore] backed by a JSON file under a configurable
 * directory (typically `filesDir`). The JSON shape is a `Map<String,
 * DeviceTrustPolicy>` keyed by `nodeId`.
 *
 * Why filesDir-JSON instead of Preferences DataStore:
 *  - `core-trust` is a pure-JVM library (no Android-specific deps).
 *  - DataStore-Preferences forces Robolectric for unit tests; a plain
 *    file is testable in plain JUnit.
 *  - The trust table is small (≤ 16 peers for v1) and changes are
 *    rare (a pairing event, not a hot path). One fsync per write is
 *    acceptable.
 *
 * Thread-safety: backed by a [ConcurrentHashMap]; the file is rewritten
 * under a per-instance lock so concurrent upserts don't interleave.
 */
class FileBackedTrustStore(private val storeDir: File, private val fileName: String = "trust_store.json") : TrustStore {
    private val lock = Any()
    private val current = java.util.concurrent.atomic.AtomicReference<Map<String, DeviceTrustPolicy>>(emptyMap())
    init {
        storeDir.mkdirs()
        val file = File(storeDir, fileName)
        if (file.exists() && file.length() <= 1024 * 1024) {
            runCatching { JsonCodec.decodePolicies(file.readText()) }.getOrNull()?.let { policies ->
                current.set(policies.associateBy { it.nodeId })
            }
        }
    }
    override fun policyFor(nodeId: NodeId) = current.get()[nodeId.value]
    override fun list() = current.get().values.toList()
    override fun upsert(policy: DeviceTrustPolicy): MeshlitResult<Unit> = synchronized(lock) {
        save(current.get() + (policy.nodeId to policy))
    }
    override fun revoke(nodeId: NodeId): MeshlitResult<Unit> = synchronized(lock) {
        save(current.get() - nodeId.value)
    }
    private fun save(next: Map<String, DeviceTrustPolicy>): MeshlitResult<Unit> {
        val out = File(storeDir, fileName); val tmp = File(storeDir, "$fileName.tmp")
        return try {
            java.io.FileOutputStream(tmp).use { stream ->
                stream.write(JsonCodec.encodePolicies(next.values.toList()).toByteArray(Charsets.UTF_8))
                stream.fd.sync()
            }
            check(tmp.renameTo(out)) { "Could not replace trust store" }
            current.set(next)
            MeshlitResult.Success(Unit)
        } catch (e: Exception) {
            tmp.delete()
            MeshlitResult.Failure(com.meshlit.core.common.MeshlitError.Resource("trust.persist_failed", e))
        }
    }
}

/** Pure in-memory store for tests / ephemeral contexts. */
class InMemoryTrustStore : TrustStore {
    private val map = ConcurrentHashMap<String, DeviceTrustPolicy>()
    override fun policyFor(nodeId: NodeId): DeviceTrustPolicy? = map[nodeId.value]
    override fun upsert(policy: DeviceTrustPolicy): MeshlitResult<Unit> {
        map[policy.nodeId] = policy
        return MeshlitResult.Success(Unit)
    }
    override fun revoke(nodeId: NodeId): MeshlitResult<Unit> {
        map.remove(nodeId.value)
        return MeshlitResult.Success(Unit)
    }
    override fun list(): List<DeviceTrustPolicy> = map.values.toList()
}

/** @Serializable shape for the on-disk JSON. Internal. */
private object JsonCodec {
    private val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    fun encodePolicies(policies: List<DeviceTrustPolicy>): String {
        val list = kotlinx.serialization.json.JsonArray(
            policies.map { kotlinx.serialization.json.Json.encodeToJsonElement(DeviceTrustPolicy.serializer(), it) }
        )
        return json.encodeToString(kotlinx.serialization.json.JsonArray.serializer(), list)
    }

    fun decodePolicies(text: String): List<DeviceTrustPolicy> {
        val element = json.parseToJsonElement(text)
        if (element !is kotlinx.serialization.json.JsonArray) return emptyList()
        return element.map {
            kotlinx.serialization.json.Json.decodeFromJsonElement(DeviceTrustPolicy.serializer(), it)
        }
    }
}
