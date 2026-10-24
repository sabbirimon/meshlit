package com.meshlit.core.observability

import kotlinx.serialization.json.*
import java.security.MessageDigest
import java.util.UUID

/** Closed metadata schema: never accepts messages, arguments, paths, URLs or secrets. */
data class AuditRecord(
    val id: String = UUID.randomUUID().toString(),
    val timeMs: Long = System.currentTimeMillis(),
    val source: AuditSource,
    val action: String,
    val actor: AuditActor = AuditActor.SYSTEM,
    val outcome: AuditOutcome = AuditOutcome.OBSERVED,
    val targetHash: String? = null,
    val measurements: Map<String, Double> = emptyMap(),
) {
    fun safe(): AuditRecord = copy(
        id = id.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.nameUUIDFromBytes(id.toByteArray()).toString(),
        action = action.takeIf { it.matches(Regex("[a-zA-Z][a-zA-Z0-9_.-]{0,95}")) } ?: "event.redacted",
        targetHash = targetHash?.takeIf { it.matches(Regex("[a-f0-9]{64}")) },
        measurements = measurements.filter { (k,v) -> k in MEASUREMENTS && v.isFinite() },
    )
    fun jsonLine(): String = safe().let { r -> buildJsonObject {
        put("schema",1); put("id",r.id); put("timeMs",r.timeMs); put("source",r.source.name)
        put("action",r.action); put("actor",r.actor.name); put("outcome",r.outcome.name)
        r.targetHash?.let { put("targetHash",it) }
        put("measurements",buildJsonObject { r.measurements.toSortedMap().forEach { (k,v) -> put(k,v) } })
    }.toString() }
    companion object {
        val MEASUREMENTS = setOf("duration_ms","input_tokens","output_tokens","cache_tokens","bytes","total_bytes","ram_available_bytes","ram_total_bytes","heap_bytes","storage_free_bytes","battery_percent","battery_celsius","thermal_status","network_rx_bytes","network_tx_bytes","active_transfers","installed_models","active_jobs","task_count","worker_active","coordinator_active")
        fun hashTarget(value: String): String = MessageDigest.getInstance("SHA-256").digest(value.toByteArray()).joinToString("") { "%02x".format(it) }
        fun parse(line:String):AuditRecord {
            val o=Json.parseToJsonElement(line).jsonObject
            require(o["schema"]?.jsonPrimitive?.int==1)
            return AuditRecord(o.getValue("id").jsonPrimitive.content,o.getValue("timeMs").jsonPrimitive.long,
                AuditSource.valueOf(o.getValue("source").jsonPrimitive.content),o.getValue("action").jsonPrimitive.content,
                AuditActor.valueOf(o.getValue("actor").jsonPrimitive.content),AuditOutcome.valueOf(o.getValue("outcome").jsonPrimitive.content),
                o["targetHash"]?.jsonPrimitive?.content,
                o.getValue("measurements").jsonObject.mapValues { it.value.jsonPrimitive.double }).safe()
        }
    }
}
enum class AuditSource { APP, INFERENCE, MODELS, TASKS, AGENT, NETWORK, CLUSTER, DEVICE, SETTINGS }
enum class AuditActor { HUMAN, AGENT, SYSTEM }
enum class AuditOutcome { STARTED, SUCCEEDED, FAILED, CANCELLED, DENIED, OBSERVED }
