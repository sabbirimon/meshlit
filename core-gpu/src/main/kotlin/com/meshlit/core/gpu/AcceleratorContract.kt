package com.meshlit.core.gpu

import kotlinx.serialization.Serializable

/** Host SDK integrations are distinct from the Android Vulkan wrapper. Never infer readiness
 * from a product name, USB descriptor, installed compiler or advertised shared memory. */
@Serializable enum class ChipVendor { NVIDIA, AMD, HUAWEI_ASCEND, QUALCOMM, HUAWEI_KIRIN, XIAOMI_XRING, MEDIATEK, SAMSUNG, GOOGLE, ARM, OTHER }
@Serializable enum class AcceleratorLink { HTTPS, SSH, LOCAL_SDK, USB_BRIDGE }
@Serializable data class AcceleratorMemoryPool(val id: String, val sharedDomain: String,
    val allocatableBytes: Long? = null, val observedAtMs: Long) {
    fun validate() { require(id.isNotBlank() && sharedDomain.isNotBlank() && observedAtMs > 0); require(allocatableBytes == null || allocatableBytes >= 0) }
}
@Serializable data class AcceleratorQualification(val deviceId: String, val runtimeSha256: String,
    val modelSha256: String, val driverRevision: String, val sdkRevision: String,
    val observedAtMs: Long, val generationSucceeded: Boolean, val peakResidentBytes: Long? = null) {
    fun validate() {
        require(deviceId.isNotBlank() && runtimeSha256.matches(SHA) && modelSha256.matches(SHA))
        require(driverRevision.isNotBlank() && sdkRevision.isNotBlank() && observedAtMs > 0)
        require(peakResidentBytes == null || peakResidentBytes >= 0)
    }
}
@Serializable data class AcceleratorAdvertisement(val deviceId: String, val vendor: ChipVendor,
    val family: String, val hostOs: String, val abi: String, val link: AcceleratorLink,
    val ownerApproved: Boolean = false, val endpointVerified: Boolean = false,
    val driverRevision: String? = null, val sdkRevision: String? = null,
    val runtimeSha256: String? = null, val memory: List<AcceleratorMemoryPool> = emptyList(),
    val qualification: AcceleratorQualification? = null) {
    /** Qualification is model/device/runtime-specific and expires. This admission helper does
     * not create evidence or add a vendor runtime to the scheduler. */
    fun inferenceReady(modelSha256: String, nowMs: Long): Boolean {
        val proof = qualification ?: return false
        if (runCatching { proof.validate(); memory.forEach { it.validate() } }.isFailure) return false
        return ownerApproved && endpointVerified && deviceId == proof.deviceId && runtimeSha256 == proof.runtimeSha256 &&
            modelSha256 == proof.modelSha256 && driverRevision == proof.driverRevision && sdkRevision == proof.sdkRevision &&
            proof.generationSucceeded && nowMs >= proof.observedAtMs && nowMs - proof.observedAtMs <= 86_400_000 &&
            family.isNotBlank() && hostOs.isNotBlank() && abi.isNotBlank()
    }
    /** UMA CPU/GPU views sharing a domain are counted once, conservatively. Unknown stays unknown. */
    fun knownAllocatableBytes(nowMs: Long): Long? {
        if (memory.isEmpty() || memory.map { it.id }.distinct().size != memory.size || memory.any { runCatching { it.validate() }.isFailure || it.allocatableBytes == null || nowMs < it.observedAtMs || nowMs - it.observedAtMs > 30_000 }) return null
        return runCatching { memory.groupBy { it.sharedDomain }.values.fold(0L) { total, domain -> Math.addExact(total, domain.minOf { it.allocatableBytes!! }) } }.getOrNull()
    }
}
private val SHA = Regex("[a-f0-9]{64}")

data class VendorSdkIntegration(val vendor: ChipVendor, val families: String, val sdk: String,
    val profiler: String, val officialUrl: String, val adapterReady: Boolean = false)
object AcceleratorCatalog {
    val integrations = listOf(
        VendorSdkIntegration(ChipVendor.NVIDIA, "Grace/Blackwell GB10/GB200/GB300, supported CUDA GPUs", "CUDA / TensorRT", "Nsight Systems / Nsight Compute", "https://docs.nvidia.com/dgx/dgx-spark/"),
        VendorSdkIntegration(ChipVendor.AMD, "Supported Instinct/Radeon/Ryzen AI hardware", "ROCm / HIP; Ryzen AI requires its own runtime", "ROCprofiler-SDK / rocprofv3", "https://rocm.docs.amd.com/"),
        VendorSdkIntegration(ChipVendor.HUAWEI_ASCEND, "Supported Ascend/Atlas products", "CANN / AscendCL", "MindStudio msProf / npu-smi", "https://www.hiascend.com/"),
        VendorSdkIntegration(ChipVendor.QUALCOMM, "Supported Snapdragon / Adreno / Hexagon products", "Qualcomm AI Runtime: QNN / SNPE", "QNN profiling / Snapdragon Profiler", "https://www.qualcomm.com/developer/software/qualcomm-ai-engine-direct-sdk"),
        VendorSdkIntegration(ChipVendor.HUAWEI_KIRIN, "Supported Kirin devices; distinct from Ascend", "HiAI / OEM runtime where available", "OEM-supported HiAI profiling", "https://developer.huawei.com/consumer/en/hiai/"),
        VendorSdkIntegration(ChipVendor.XIAOMI_XRING, "XRING family; exact device driver required", "Verified Vulkan/OpenCL/LiteRT or future OEM plugin", "Arm/OEM graphics tools where supported", "https://ir.mi.com/"),
        VendorSdkIntegration(ChipVendor.MEDIATEK, "Supported Dimensity / Helio / Genio products", "NeuroPilot / Neuron SDK (access may be gated)", "SDK-provided profiling", "https://neuropilot.mediatek.com/"),
        VendorSdkIntegration(ChipVendor.SAMSUNG, "Supported Exynos/Xclipse products", "OEM/LiteRT/Vulkan backend when qualified", "OEM / Arm tools when supported", "https://developer.samsung.com/"),
        VendorSdkIntegration(ChipVendor.GOOGLE, "Supported Tensor devices", "LiteRT / vendor delegate when available", "Android system trace / runtime metrics", "https://ai.google.dev/edge/litert"),
        VendorSdkIntegration(ChipVendor.ARM, "Generic Cortex/Neoverse/Mali/Immortalis", "Arm Compute Library / KleidiAI / verified graphics backend", "Arm performance tools / system trace", "https://developer.arm.com/"),
    )
}
