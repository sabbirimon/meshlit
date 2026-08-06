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
class FileBackedTrustStore(
    private val storeDir: File,
    private val fileName: String = "trust_store.json",
) : TrustStore {

    private val lock = Any()
    private val map: ConcurrentHashMap<String, DeviceTrustPolicy> = ConcurrentHashMap()

    init {
        storeDir.mkdirs()
        loadFromDisk()
    }

    override fun policyFor(nodeId: NodeId): DeviceTrustPolicy? = map[nodeId.value]

    override fun upsert(policy: DeviceTrustPolicy): MeshlitResult<Unit> = synchronized(lock) {
        map[policy.nodeId] = policy
        persist()
        MeshlitResult.Success(Unit)
    }

    override fun revoke(nodeId: NodeId): MeshlitResult<Unit> = synchronized(lock) {
        if (map.remove(nodeId.value) != null) {
            persist()
        }
        MeshlitResult.Success(Unit)
    }

    override fun list(): List<DeviceTrustPolicy> = map.values.toList()

    // ---- internals -----------------------------------------------------

    private fun persist() {
        val out = File(storeDir, fileName)
        val tmp = File(storeDir, "$fileName.tmp")
        try {
            tmp.writeText(JsonCodec.encodePolicies(map.values.toList()))
            if (out.exists()) out.delete()
            if (!tmp.renameTo(out)) {
                // Fallback: write to final directly.
                out.writeText(tmp.readText())
                tmp.delete()
            }
        } catch (t: Throwable) {
            tmp.delete()
            throw t
        }
    }

    private fun loadFromDisk() {
        val file = File(storeDir, fileName)
        if (!file.exists() || file.length() == 0L) return
        val text = runCatching { file.readText() }.getOrNull() ?: return
        val parsed = runCatching { JsonCodec.decodePolicies(text) }.getOrNull() ?: return
        map.clear()
        parsed.forEach { map[it.nodeId] = it }
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
