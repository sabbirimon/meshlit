package com.meshlit.inference

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.meshlit.core.common.NodeId
import com.meshlit.core.common.logger
import com.meshlit.core.trust.TrustStore
import com.meshlit.core.trust.TrustTier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

/**
 * A forwarding peer enriched with the Phase-3 trust posture.
 *
 * [nodeId] is the stable device id when the pairing handshake has
 * completed. For pre-Phase-3 / manually-added peers it falls back to
 * the IPv4 string so callers can still correlate the health cache.
 */
data class TrustedPeer(
    val nodeId: String,
    val ip: String,
    val tier: TrustTier,
)

/**
 * DataStore-backed list of forwarding peers (IPs).
 *
 * Persisted as a single comma-separated string under [KEY]. The list
 * is small (single digits in v1) so a flat string is simpler than
 * a JSON blob. Reads emit the current value as a [Flow] so the
 * embedded server's router can react to changes without restart.
 *
 * Phase 3 keeps the original [peers] API for source compatibility and
 * adds [trustedPeers] / [trustedSnapshot]. The latter consult the
 * injected [TrustStore]. Existing call sites can migrate one at a
 * time; no DataStore schema change is needed.
 *
 * Validation:
 *  - [add] trims whitespace, rejects empties, validates IPv4-ish
 *    dotted notation (`a.b.c.d`). v1 deliberately ignores IPv6 and
 *    hostnames — the wire is hardcoded IP for Phase 1.
 *  - [add] dedups; re-adding the same IP is a no-op.
 *
 * The DataStore lives in the FGS-owned scope (one DataStore per
 * process); the FGS creates the registry in `onCreate` and exposes
 * the [peers] flow to the router and the Settings UI.
 */
class PeerRegistry(
    private val dataStore: DataStore<Preferences>,
    private val trustStore: TrustStore? = null,
) {

    private val log = logger("PeerRegistry")

    /** Reactive list of peer IPv4 addresses (legacy / UI API). */
    val peers: Flow<List<String>> = dataStore.data.map { prefs ->
        parseList(prefs[KEY])
    }

    /**
     * Reactive list enriched with trust tiers. Pre-Phase-3 peers have
     * no policy and are therefore LOCAL_SANDBOXED (least privilege).
     */
    val trustedPeers: Flow<List<TrustedPeer>> = peers.map { ips ->
        ips.map { ip ->
            val policy = trustStore?.policyFor(NodeId(ip))
            TrustedPeer(
                nodeId = policy?.nodeId ?: ip,
                ip = ip,
                tier = policy?.trustTier ?: TrustTier.LOCAL_SANDBOXED,
            )
        }
    }

    /** Snapshot read (not flow). Returns the current legacy IP list. */
    suspend fun snapshot(): List<String> = peers.first()

    /** Snapshot read with Phase-3 trust posture. */
    suspend fun trustedSnapshot(): List<TrustedPeer> = trustedPeers.first()

    /** Add an IP. Dedups, validates, no-op on bad input. */
    suspend fun add(ip: String) {
        val normalized = normalize(ip) ?: run {
            log.warn("add.invalid", "rejected invalid peer IP", mapOf("ip" to ip))
            return
        }
        dataStore.edit { prefs ->
            val current = parseList(prefs[KEY]).toMutableList()
            if (!current.contains(normalized)) {
                current.add(normalized)
                prefs[KEY] = current.joinToString(SEPARATOR)
                log.info("add.ok", "peer added", mapOf("ip" to normalized, "size" to current.size))
            } else {
                log.info("add.dup", "peer already present", mapOf("ip" to normalized))
            }
        }
    }

    /** Remove an IP. No-op if absent. */
    suspend fun remove(ip: String) {
        val normalized = normalize(ip) ?: return
        dataStore.edit { prefs ->
            val current = parseList(prefs[KEY]).toMutableList()
            if (current.remove(normalized)) {
                prefs[KEY] = current.joinToString(SEPARATOR)
                log.info("remove.ok", "peer removed", mapOf("ip" to normalized, "size" to current.size))
            }
        }
    }

    /** Replace the entire peer list (used by Settings UI bulk edits). */
    suspend fun replaceAll(ips: List<String>) {
        val cleaned = ips.mapNotNull { normalize(it) }.distinct()
        dataStore.edit { prefs ->
            prefs[KEY] = cleaned.joinToString(SEPARATOR)
            log.info("replace.ok", "peer list replaced", mapOf("size" to cleaned.size))
        }
    }

    companion object {
        /** DataStore key. Stable string. */
        const val KEY_NAME = "forward_peers"
        val KEY = stringPreferencesKey(KEY_NAME)
        const val SEPARATOR = ","

        /**
         * Validate + normalize a peer IP. Returns null on bad input.
         * v1: dotted IPv4 only, no port suffix (port is the server
         * default `8080`).
         */
        fun normalize(raw: String): String? {
            val t = raw.trim().removePrefix("http://").removePrefix("https://")
                .substringBefore('/').substringBefore(':')
            if (t.isEmpty()) return null
            val parts = t.split('.')
            if (parts.size != 4) return null
            val valid = parts.all { part ->
                val n = part.toIntOrNull() ?: return@all false
                n in 0..255
            }
            return if (valid) t else null
        }

        /** Parse the persisted comma-separated string into a list. */
        fun parseList(raw: String?): List<String> = raw
            ?.takeIf { it.isNotBlank() }
            ?.split(SEPARATOR)
            ?.mapNotNull { normalize(it) }
            ?: emptyList()
    }
}