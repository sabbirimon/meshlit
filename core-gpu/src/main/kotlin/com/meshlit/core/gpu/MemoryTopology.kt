package com.meshlit.core.gpu
import kotlinx.serialization.Serializable
@Serializable enum class MemoryKind { SYSTEM_RAM, DDR, LPDDR, GDDR, HBM, SRAM, DISCRETE_VRAM, UNIFIED_RAM, NPU_LOCAL, FPGA_LOCAL, CXL_ATTACHED, PERSISTENT_MEMORY, UNKNOWN }
@Serializable data class MemoryTopologyObservation(val poolId:String,val sharingDomain:String,val kind:MemoryKind=MemoryKind.UNKNOWN,
    val observedAtMs:Long,val source:String,val numaNode:Int?=null,val capacityBytes:Long?=null,val allocatableBytes:Long?=null,
    val busWidthBits:Int?=null,val transferRateMTs:Double?=null,val measuredBandwidthBytesPerSecond:Double?=null,
    val measurementMethod:String?=null) {
    fun validate(){require(poolId.isNotBlank() && sharingDomain.isNotBlank() && source.isNotBlank() && observedAtMs>0);require(numaNode==null || numaNode>=0)
        require(capacityBytes==null || capacityBytes>=0);require(allocatableBytes==null || allocatableBytes>=0 && (capacityBytes==null || allocatableBytes<=capacityBytes))
        require(busWidthBits==null || busWidthBits>0);require(transferRateMTs==null || transferRateMTs.isFinite() && transferRateMTs>0)
        require(measuredBandwidthBytesPerSecond==null || measuredBandwidthBytesPerSecond.isFinite() && measuredBandwidthBytesPerSecond>0 && !measurementMethod.isNullOrBlank())}
}
@Serializable enum class StorageTier { NVME, SSD, HDD, REMOVABLE, NETWORK, UNKNOWN }
@Serializable enum class SpillPurpose { MODEL_WEIGHTS, CHECKPOINT, KV_CACHE, INTERMEDIATE }
@Serializable data class StorageSpillPlan(val purpose:SpillPurpose,val tier:StorageTier,val bytes:Long,
    val observedFreeBytes:Long?,val observedAtMs:Long,val ownerGranted:Boolean=false,
    val encrypted:Boolean=false,val integritySha256:String?=null,val adapterImplemented:Boolean=false) {
    /** A planning predicate, not a disk offload implementation. Disk space never contributes
     * to AcceleratorAdvertisement.knownAllocatableBytes(). Sensitive KV needs encryption. */
    fun eligible(nowMs:Long):Boolean = bytes in 1..(64L*1024*1024*1024) && observedFreeBytes!=null &&
        observedFreeBytes>=bytes && observedFreeBytes-bytes>=256L*1024*1024 && nowMs>=observedAtMs && nowMs-observedAtMs<=30000 &&
        ownerGranted && adapterImplemented && integritySha256?.matches(Regex("[a-f0-9]{64}"))==true &&
        (purpose !in setOf(SpillPurpose.KV_CACHE,SpillPurpose.CHECKPOINT,SpillPurpose.INTERMEDIATE) || encrypted)
}
