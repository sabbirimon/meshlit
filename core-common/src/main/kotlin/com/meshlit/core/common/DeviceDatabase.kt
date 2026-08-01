package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * An open-source chipset database snapshot, fetched from a remote
 * (e.g. a GitHub-hosted JSON file). Merged on top of the built-in
 * [SocFamily] enum at runtime so new chipsets ship without an app
 * update.
 *
 * Schema is intentionally simple — just enough to drive better
 * inference-fit and role-suggestion decisions. New fields are
 * added backward-compatibly (existing fields keep meaning).
 *
 * The remote URL is configured in `:app` (per-platform). Default
 * source is a curated JSON file on GitHub.
 */
@Serializable
data class DeviceDatabaseSnapshot(
    val version: String,                       // semver "1.2.0"
    val generatedAt: String,                   // ISO-8601
    val sourceUrl: String,                     // where this was fetched from
    val signatureSha256: String,               // integrity hash for tamper detection
    val chipsets: List<ChipsetDefinition>,
    val gpus: List<GpuDefinition>,
    val npus: List<NpuDefinition>,
)

@Serializable
data class ChipsetDefinition(
    /** Pattern matched against Build.HARDWARE + Build.SOC_MODEL + Build.MODEL. */
    val matchOn: ChipsetMatchRules,
    val tag: String,                            // matches SocFamily.tag
    val displayName: String,
    val inferenceFit: InferenceFit,
    val gpu: String? = null,                    // GpuFamily.tag
    val npu: String? = null,                    // free-form: "Hexagon", "APU 790"
    val notes: String? = null,
)

@Serializable
data class ChipsetMatchRules(
    val hardware: List<String>? = null,         // case-insensitive prefixes
    val socManufacturer: List<String>? = null,
    val socModel: List<String>? = null,         // prefixes (e.g. "SM8" matches SM8550, SM8650)
    val modelContains: List<String>? = null,    // substrings in Build.MODEL
    val minAndroidSdk: Int? = null,
)

@Serializable
data class GpuDefinition(
    val rendererContains: List<String>,
    val tag: String,                            // GpuFamily.tag
    val displayName: String,
)

@Serializable
data class NpuDefinition(
    val socFamily: List<String>,
    val name: String,
    val vendor: String,
    val apiLevel: Int? = null,
)

/**
 * Local cache of the latest fetched snapshot, persisted to DataStore.
 * The cache is checked on probe; if a newer remote snapshot has been
 * published and we have network, we fetch it. User can pin a version
 * (no auto-update) — useful for reproducible clusters.
 */
interface DeviceDatabaseCache {
    suspend fun load(): DeviceDatabaseSnapshot?
    suspend fun store(snapshot: DeviceDatabaseSnapshot)
    suspend fun pinned(): String?
    suspend fun pinTo(version: String)
}
