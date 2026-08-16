package com.meshlit.core.inference.cluster

import kotlinx.serialization.Serializable

/**
 * Wire shape for `GET /v1/manifest/{modelId}` on the cluster-shard
 * surface. Reassembled shards across the cluster consult this so
 * each device knows the byte offsets + lengths of the slices it
 * should host (or fetch).
 *
 * The name distinguishes this from
 * `com.meshlit.core.inference.net.ShardManifest`, which is the
 * inference-side manifest with full schema-version / tokenizer /
 * kvCache fields. This cluster-side manifest is a strict subset —
 * just enough for peers to know where to read each shard.
 *
 * `shardSpecs` is in plan order — when the planner has assigned
 * shard ranges, this is `ShardAssignment` minus the peer ID; the
 * byte offset + length alone uniquely identify the slice.
 *
 * `sha256` may be blank when the underlying model has no published
 * digest (see `ShardAssembler.reassemble`). The field defaults to
 * "" so the JSON tolerates half-known manifests.
 */
@Serializable
data class ClusterShardManifest(
    val modelId: String,
    val totalBytes: Long,
    val sha256: String = "",
    val shardSpecs: List<ShardSpec>,
) {
    @Serializable
    data class ShardSpec(
        val shardId: String,
        val byteOffset: Long,
        val byteLength: Long,
    )
}

/** Shared codec for [ClusterShardManifest]. */
object ClusterShardManifestJson {
    val json = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(m: ClusterShardManifest): String = json.encodeToString(ClusterShardManifest.serializer(), m)

    fun decode(raw: String): ClusterShardManifest = json.decodeFromString(ClusterShardManifest.serializer(), raw)
}
