package com.meshlit.core.inference.cluster

import com.meshlit.core.common.logger
import fi.iki.elonen.NanoHTTPD
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Phase 11.2 — `/v1/cluster/...` HTTP surface for distributed training.
 *
 * Mounted alongside [ShardServer] on the same `InferenceHttpServer`
 * port so the existing transport + firewall + rate-limit code paths
 * apply unchanged. The pattern matches `ShardServer.route(session)` —
 * `InferenceHttpServer.serve()` calls our `route()` first; we return
 * null for non-cluster traffic so the regular routes pick it up.
 *
 * Endpoints (all v1):
 *  - `GET  /v1/cluster/peers`       → JSON list of cluster members
 *  - `GET  /v1/cluster/plan/{runId}` → JSON `ShardingPlan` for the run
 *  - `POST /v1/cluster/join`         → join a run
 *  - `POST /v1/cluster/leave`        → leave a run
 *  - `POST /v1/cluster/run`          → start a run
 *  - `GET  /v1/cluster/logs/{runId}`  → JSONL training events
 *  - `WS   /ws/cluster/{runId}`      → WebSocket status stream (Phase 11.2-stub)
 *
 * Stability:
 *  - Wire shape is v1. Additive fields are fine; renames / removals
 *    require a `/v2/cluster/...` prefix.
 *  - The schema-version field is `clusterWireVersion: Int = 1` on
 *    every reply. Clients that see an unknown version fall back to a
 *    typed error instead of silently decoding a future-only shape.
 *
 * Why a bridge to `:app`:
 *  `core-inference` doesn't depend on `:core-training` (the dep
 *  direction is the other way). So this class doesn't know about
 *  `ClusterTrainerRegistry` directly — instead the FGS passes a
 *  [Bridge] lambda that resolves names + reads state. The bridge
 *  surfaces typed results; the routes turn them into JSON.
 */
class ClusterRoutes(
    private val bridge: Bridge,
) {

    private val log = logger("ClusterRoutes")

    /**
     * Pre-filter mirror of [ShardServer.route]. Returns null for
     * non-cluster traffic so the existing `InferenceHttpServer.serve()`
     * routes handle it.
     */
    fun route(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response? {
        val uri = session.uri.removePrefix("/").trimEnd('/')
        return when {
            uri == "v1/cluster/peers" || uri.startsWith("v1/cluster/peers/") -> handlePeers(session)
            uri.startsWith("v1/cluster/plan/") || uri == "v1/cluster/plan" -> handlePlan(session, uri)
            uri == "v1/cluster/join" || uri.startsWith("v1/cluster/join/") -> handleJoin(session)
            uri == "v1/cluster/leave" || uri.startsWith("v1/cluster/leave/") -> handleLeave(session)
            uri == "v1/cluster/run" || uri.startsWith("v1/cluster/run/") -> handleRun(session)
            uri.startsWith("v1/cluster/logs/") || uri == "v1/cluster/logs" -> handleLogs(session, uri)
            else -> null
        }
    }

    // ── handlers ──────────────────────────────────────────────────────

    private fun handlePeers(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET) {
            return methodNotAllowed("use GET")
        }
        return try {
            val body = bridge.peers()
            ok(encodePeersResponse(body))
        } catch (t: Throwable) {
            log.warn("cluster.peers.fail", "peers() failed", mapOf("err" to (t.message ?: "")))
            internalError("cluster.peers.failed: ${t.message ?: ""}")
        }
    }

    private fun handlePlan(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET) {
            return methodNotAllowed("use GET")
        }
        // The matched URI is either "v1/cluster/plan" (no runId) or
        // "v1/cluster/plan/{runId}". Treat the no-runId form as 400.
        if (uri == "v1/cluster/plan") return badRequest("missing runId")
        val runId = uri.removePrefix("v1/cluster/plan/").sanitize()
        if (runId.isEmpty()) return badRequest("missing runId")
        return try {
            val plan = bridge.plan(runId) ?: return notFound("no plan for $runId")
            ok(encodePlanResponse(plan))
        } catch (t: Throwable) {
            log.warn("cluster.plan.fail", "plan() failed", mapOf("runId" to runId, "err" to (t.message ?: "")))
            internalError("cluster.plan.failed: ${t.message ?: ""}")
        }
    }

    private fun handleJoin(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.POST) {
            return methodNotAllowed("use POST")
        }
        val body = readJsonBody(session) ?: return badRequest("expected JSON body")
        val runId = body["runId"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        if (runId.isEmpty()) return badRequest("missing runId")
        return try {
            val result = bridge.join(runId)
            ok(encodeResultResponse(result))
        } catch (t: Throwable) {
            log.warn("cluster.join.fail", "join() failed", mapOf("runId" to runId, "err" to (t.message ?: "")))
            internalError("cluster.join.failed: ${t.message ?: ""}")
        }
    }

    private fun handleLeave(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.POST) {
            return methodNotAllowed("use POST")
        }
        val body = readJsonBody(session) ?: return badRequest("expected JSON body")
        val runId = body["runId"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        if (runId.isEmpty()) return badRequest("missing runId")
        return try {
            val result = bridge.leave(runId)
            ok(encodeResultResponse(result))
        } catch (t: Throwable) {
            log.warn("cluster.leave.fail", "leave() failed", mapOf("runId" to runId, "err" to (t.message ?: "")))
            internalError("cluster.leave.failed: ${t.message ?: ""}")
        }
    }

    private fun handleRun(session: NanoHTTPD.IHTTPSession): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.POST) {
            return methodNotAllowed("use POST")
        }
        val body = readJsonBody(session) ?: return badRequest("expected JSON body")
        val runId = body["runId"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        val strategy = body["strategy"]?.jsonPrimitive?.contentOrNullSafe().orEmpty()
        if (runId.isEmpty()) return badRequest("missing runId")
        return try {
            val result = bridge.run(runId, strategy)
            ok(encodeResultResponse(result))
        } catch (t: Throwable) {
            log.warn("cluster.run.fail", "run() failed", mapOf("runId" to runId, "strategy" to strategy, "err" to (t.message ?: "")))
            internalError("cluster.run.failed: ${t.message ?: ""}")
        }
    }

    private fun handleLogs(session: NanoHTTPD.IHTTPSession, uri: String): NanoHTTPD.Response {
        if (session.method != NanoHTTPD.Method.GET) {
            return methodNotAllowed("use GET")
        }
        if (uri == "v1/cluster/logs") return badRequest("missing runId")
        val runId = uri.removePrefix("v1/cluster/logs/").sanitize()
        if (runId.isEmpty()) return badRequest("missing runId")
        val limit = session.parameters["limit"]?.firstOrNull()?.toIntOrNull() ?: 100
        return try {
            val lines = bridge.logs(runId, limit)
            // JSONL — one TrainingEvent per line. Stable field order via
            // kotlinx.serialization defaults.
            val payload = lines.joinToString(separator = "\n") { ev ->
                ClusterEventJson.encode(ev)
            }
            ok(payload)
        } catch (t: Throwable) {
            log.warn("cluster.logs.fail", "logs() failed", mapOf("runId" to runId, "err" to (t.message ?: "")))
            internalError("cluster.logs.failed: ${t.message ?: ""}")
        }
    }

    // ── helpers ──────────────────────────────────────────────────────

    private fun readJsonBody(session: NanoHTTPD.IHTTPSession): JsonObject? {
        return try {
            val raw = String(session.parseBodySize(maxSize = 16 * 1024), Charsets.UTF_8)
            if (raw.isBlank()) null
            else ClusterEventJson.json.parseToJsonElement(raw).jsonObject
        } catch (t: Throwable) {
            null
        }
    }

    private fun ok(body: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.OK, "application/json", body)

    private fun methodNotAllowed(msg: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.METHOD_NOT_ALLOWED, "text/plain", msg)

    private fun badRequest(msg: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.BAD_REQUEST, "text/plain", msg)

    private fun notFound(msg: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.NOT_FOUND, "text/plain", msg)

    private fun internalError(msg: String): NanoHTTPD.Response =
        NanoHTTPD.newFixedLengthResponse(NanoHTTPD.Response.Status.INTERNAL_ERROR, "text/plain", msg)

    private fun String.sanitize(): String =
        this.filter { it.isLetterOrDigit() || it == '-' || it == '_' }.take(64)

    /**
     * Optional NanoHTTPD body-parsing helper. Mirrors the
     * [ShardServer.handleShard] pattern: returns the bytes for the
     * first body parameter. The session's `parameters` map holds
     * parsed form fields; for our JSON bodies the raw `parseBody`
     * array is what we want.
     */
    private fun NanoHTTPD.IHTTPSession.parseBodySize(maxSize: Int): ByteArray {
        val files = HashMap<String, String>()
        try {
            parseBody(files)
        } catch (t: Throwable) {
            // Some NanoHTTPD versions cap body size internally. If the
            // server replies 413 we'll surface that as 400 here.
        }
        val raw = files["postData"] ?: ""
        val bytes = raw.toByteArray(Charsets.UTF_8)
        if (bytes.size > maxSize) {
            throw IllegalArgumentException("body too large: ${bytes.size} > $maxSize")
        }
        return bytes
    }

    // ── wire shapes ──────────────────────────────────────────────────

    @Serializable
    data class PeersResponse(
        val clusterWireVersion: Int = 1,
        val members: List<PeerCapabilities>,
    )

    @Serializable
    data class PlanResponse(
        val clusterWireVersion: Int = 1,
        val runId: String,
        val assignments: List<PlanAssignment>,
        val strategy: String,
        val totalReservedMb: Long,
    )

    @Serializable
    data class PlanAssignment(
        val peerId: String,
        val layerStart: Int,
        val layerEndInclusive: Int,
        val role: String,
        val isCoordinator: Boolean,
    )

    @Serializable
    data class OperationResult(
        val clusterWireVersion: Int = 1,
        val accepted: Boolean,
        val message: String = "",
    )

    private fun encodePeersResponse(body: PeersResponse): String =
        ClusterEventJson.encode(body)

    private fun encodePlanResponse(body: PlanResponse): String =
        ClusterEventJson.encode(body)

    private fun encodeResultResponse(body: OperationResult): String =
        ClusterEventJson.encode(body)

    /**
     * The bridge the FGS implements. Each method maps directly to
     * one of the route handlers. Throwing from a method surfaces as
     * a 500 to the caller — the routes log the cause.
     *
     * The interface lives in `:core-inference` so we don't have to
     * depend on `:core-training` from this module (the dep is the
     * other way around). `:app` wires the real implementation via
     * [ClusterTrainerRegistry].
     */
    interface Bridge {
        /** Snapshot of cluster members for `/v1/cluster/peers`. */
        fun peers(): PeersResponse

        /** Plan for a runId, or null if unknown. */
        fun plan(runId: String): PlanResponse?

        /** Join an existing run. */
        fun join(runId: String): OperationResult

        /** Leave a run. Idempotent. */
        fun leave(runId: String): OperationResult

        /** Start a new run with the chosen strategy (P2P/DILOCO/ACCELERATE). */
        fun run(runId: String, strategy: String): OperationResult

        /** Recent training events for a run, newest-last. */
        fun logs(runId: String, limit: Int): List<TrainingEventDto>
    }

    /**
     * Wire DTO for `/v1/cluster/logs/{runId}`. Mirrors
     * `core-training.TrainingEvent` subtypes additively — unknown
     * fields are tolerated (`ignoreUnknownKeys`).
     */
    @Serializable
    data class TrainingEventDto(
        val type: String,
        val step: Long = 0,
        val jobId: String = "",
        val peerId: String = "",
        val reason: String = "",
        val strategy: String = "",
        val tempC: Double = 0.0,
        val rate: Float = 0f,
        val timestampMs: Long = 0,
        val payload: JsonObject? = null,
    )
}

/**
 * Shared codec for the `/v1/cluster/...` wire types. Conservative
 * defaults: `ignoreUnknownKeys` so older peers keep decoding future
 * payloads, `encodeDefaults` so new fields land cleanly.
 */
object ClusterEventJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(value: Any): String = when (value) {
        is ClusterRoutes.PeersResponse -> json.encodeToString(ClusterRoutes.PeersResponse.serializer(), value)
        is ClusterRoutes.PlanResponse -> json.encodeToString(ClusterRoutes.PlanResponse.serializer(), value)
        is ClusterRoutes.OperationResult -> json.encodeToString(ClusterRoutes.OperationResult.serializer(), value)
        is ClusterRoutes.TrainingEventDto -> json.encodeToString(ClusterRoutes.TrainingEventDto.serializer(), value)
        else -> throw IllegalArgumentException("unsupported wire type: ${value::class.simpleName}")
    }
}

private fun kotlinx.serialization.json.JsonPrimitive.contentOrNullSafe(): String? =
    runCatching { content }.getOrNull()