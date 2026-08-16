package com.meshlit.core.discovery.beacon

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * **Phase Hivemind-1 — Beacon Envelope** — a discriminated
 * union of the messages that ride on the cluster's beacon
 * channel. The pre-Phase envelope (`MeshlitBeacon`) is preserved
 * as the `beacon` variant so older peers keep round-tripping.
 *
 * All variants share the same 512-byte size budget and the same
 * HMAC envelope, so the beacon channel doesn't need a parallel
 * transport. The `kind` discriminator is the JSON
 * `{"kind":"bye", ...}` key.
 *
 * Wire types:
 *  - `beacon` — periodic heartbeat (every 30 s). Same shape as
 *    [MeshlitBeacon] pre-Phase.
 *  - `bye` — graceful leave. Sent on `FGS.onDestroy()` and when
 *    the user explicitly turns off the cluster. Receivers delete
 *    the peer from `ClusterCoordinator.members` immediately.
 *  - `takeover` — host-baton handover. Sent by the outgoing host
 *    when the KubeScheduler picks a higher-scoring peer. The
 *    outgoing host awaits a `yield_ack` with the same token.
 *  - `yield_ack` — host accepted/rejected a handover. The
 *    outgoing host transitions to Relay on accept, stays Host
 *    on reject (logged with `errorCode`).
 *  - `peer_table_sync` — gossip push every 60 s. Carries the
 *    sender's full peer table so a new phone can populate its
 *    own table in ~1 min instead of waiting for 30 s beacons
 *    from each peer.
 */
@Serializable
sealed interface BeaconEnvelope {

    @Serializable
    @SerialName("beacon")
    data class Beacon(val snap: MeshlitBeacon) : BeaconEnvelope

    @Serializable
    @SerialName("bye")
    data class Bye(
        val nodeId: String,
        val reason: String = "graceful",
        val tsMs: Long = System.currentTimeMillis(),
    ) : BeaconEnvelope

    @Serializable
    @SerialName("takeover")
    data class Takeover(
        val fromNodeId: String,
        val toNodeId: String,
        val handoffToken: String,
        val scores: Map<String, Double> = emptyMap(),
        val tsMs: Long = System.currentTimeMillis(),
    ) : BeaconEnvelope

    @Serializable
    @SerialName("yield_ack")
    data class YieldAck(
        val fromNodeId: String,
        val toNodeId: String,
        val accepted: Boolean,
        val handoffToken: String,
        val errorCode: String? = null,
        val tsMs: Long = System.currentTimeMillis(),
    ) : BeaconEnvelope

    @Serializable
    @SerialName("peer_table_sync")
    data class PeerTableSync(
        val peerId: String,
        val knownPeers: List<PeerRef>,
        val tsMs: Long = System.currentTimeMillis(),
    ) : BeaconEnvelope

    @Serializable
    data class PeerRef(
        val nodeId: String,
        val ip: String,
        val tier: String,
        val lastSeenMs: Long,
        val kubeScore: Double = 0.0,
    )

    companion object {
        private val json = Json {
            ignoreUnknownKeys = true
            encodeDefaults = true
            classDiscriminator = "kind"
        }

        /** Encode a [BeaconEnvelope] to its canonical JSON wire form. */
        fun encode(env: BeaconEnvelope): String = json.encodeToString(serializer(), env)

        /** Decode a JSON payload into the discriminator variant.
         *  Returns null for malformed or unknown-kinded payloads so
         *  older peers that emit bare [MeshlitBeacon] JSON fall
         *  through to the [MeshlitBeacon.decode] path. */
        fun decode(payload: String): BeaconEnvelope? = runCatching {
            // Carve out the legacy MeshlitBeacon shape: no "kind" key
            // at the top level. Older peers emit `{ "v": 1, "id": "..." }`.
            val obj: JsonObject = json.parseToJsonElement(payload).jsonObject
            val kind = obj["kind"]?.jsonPrimitive?.content
            if (kind == null) {
                // Legacy wire form: re-encode as Beacon variant.
                MeshlitBeacon.decode(payload)?.let { Beacon(it) }
            } else {
                json.decodeFromString(serializer(), payload)
            }
        }.getOrNull()

        /** Helper used by the test suite to round-trip a single
         *  variant without going through the full event bus. */
        fun roundTrip(env: BeaconEnvelope): BeaconEnvelope? =
            decode(encode(env))
    }
}
