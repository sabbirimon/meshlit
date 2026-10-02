package com.meshlit.core.federation

import com.meshlit.core.common.Abi
import com.meshlit.core.common.CapabilityMatrix
import com.meshlit.core.common.FileFormat
import com.meshlit.core.common.GpuBackendAvailability
import com.meshlit.core.common.NpuAvailability
import com.meshlit.core.common.Quantization
import com.meshlit.core.common.RamTier
import kotlinx.serialization.Serializable

/**
 * Wire-level representation of [CapabilityMatrix]. Lives in
 * `:core-federation` (not in `:core-common`) to keep the leaf module
 * free of a hard dependency on the JSON codec — every consumer that
 * doesn't need to put the matrix on the wire can keep using the plain
 * Kotlin [CapabilityMatrix] without pulling in `kotlinx.serialization`.
 *
 * Conversion:
 * ```
 * CapabilityMatrix -> CapabilityMatrixDto  (via [toDto])
 * CapabilityMatrixDto -> CapabilityMatrix  (via [toDomain])
 * ```
 *
 * The wire shape is intentionally flat: the federation peers don't
 * care about the Kotlin class hierarchy, only the tag strings.
 */
@Serializable
data class CapabilityMatrixDto(
    val androidApi: Int,
    val abis: Set<String>,
    val ramTier: String,
    val gpu: String,
    val npu: String,
    val formats: Set<String>,
    val contextLimit: Int,
    val quants: Set<String>,
) {
    fun toDomain(): CapabilityMatrix = CapabilityMatrix(
        androidApi = androidApi,
        abis = abis.mapNotNullTo(mutableSetOf()) { tag ->
            Abi.entries.firstOrNull { it.tag == tag }
        },
        ramTier = RamTier.entries.firstOrNull { it.displayName == ramTier } ?: RamTier.LIGHT,
        gpu = GpuBackendAvailability.entries.firstOrNull { it.tag == gpu }
            ?: GpuBackendAvailability.NONE,
        npu = NpuAvailability.entries.firstOrNull { it.tag == npu }
            ?: NpuAvailability.UNAVAILABLE,
        formats = formats.mapNotNullTo(mutableSetOf()) { tag ->
            FileFormat.entries.firstOrNull { it.tag == tag }
        },
        contextLimit = contextLimit,
        quants = quants.mapNotNullTo(mutableSetOf()) { tag ->
            Quantization.entries.firstOrNull { it.name == tag }
        },
    )

    companion object {
        fun fromDomain(matrix: CapabilityMatrix): CapabilityMatrixDto = CapabilityMatrixDto(
            androidApi = matrix.androidApi,
            abis = matrix.abis.map { it.tag }.toSet(),
            ramTier = matrix.ramTier.displayName,
            gpu = matrix.gpu.tag,
            npu = matrix.npu.tag,
            formats = matrix.formats.map { it.tag }.toSet(),
            contextLimit = matrix.contextLimit,
            quants = matrix.quants.map { it.name }.toSet(),
        )
    }
}