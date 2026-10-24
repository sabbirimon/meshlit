package com.meshlit.core.common.platform
import kotlinx.serialization.Serializable
@Serializable enum class PlatformFamily { ANDROID, LINUX, WINDOWS, MACOS, IOS, HARMONYOS, BSD, RTOS, BARE_METAL, OTHER }
@Serializable enum class FabricKind { WIFI_IP, ETHERNET_IP, USB_IP, INFINIBAND_IPOIB, INFINIBAND_VERBS, ROCE, HUAWEI_HCCS, FIBRE_CHANNEL, OTHER }
@Serializable data class FabricObservation(val id:String,val kind:FabricKind,val physicalMedium:String?=null,
    val observedAtMs:Long,val linkUp:Boolean?=null,val speedBitsPerSecond:Long?=null,
    val ipReachabilityVerified:Boolean=false,val nativeAdapterQualified:Boolean=false) {
    /** Optical describes a physical medium, not an authentication or RDMA guarantee. */
    fun usableForIp(nowMs:Long):Boolean = id.isNotBlank() && kind in setOf(FabricKind.WIFI_IP,FabricKind.ETHERNET_IP,FabricKind.USB_IP,FabricKind.INFINIBAND_IPOIB) &&
        linkUp==true && ipReachabilityVerified && nowMs>=observedAtMs && nowMs-observedAtMs<=30000
    fun usableForNativeFabric(nowMs:Long):Boolean = id.isNotBlank() && kind in setOf(FabricKind.INFINIBAND_VERBS,FabricKind.ROCE,FabricKind.HUAWEI_HCCS,FabricKind.FIBRE_CHANNEL) &&
        linkUp==true && nativeAdapterQualified && nowMs>=observedAtMs && nowMs-observedAtMs<=30000
}
@Serializable data class PlatformCapability(val name:String,val adapterRevision:String?,val tested:Boolean=false,val ownerEnabled:Boolean=false,val observedAtMs:Long) {
    fun available(nowMs:Long):Boolean = name.matches(Regex("[a-z][a-z0-9_.-]{0,63}")) && !adapterRevision.isNullOrBlank() && tested && ownerEnabled && nowMs>=observedAtMs && nowMs-observedAtMs<=30000
}
@Serializable data class PlatformAdvertisement(val schema:Int=1,val family:PlatformFamily,val osRevision:String,val abi:String,
    val rootAvailable:Boolean=false,val capabilities:List<PlatformCapability> = emptyList(),val fabrics:List<FabricObservation> = emptyList()) {
    fun enabledCapabilities(nowMs:Long):Set<String>{require(schema==1 && osRevision.isNotBlank() && abi.isNotBlank());require(capabilities.map{it.name}.distinct().size==capabilities.size);return capabilities.filter{it.available(nowMs)}.map{it.name}.toSet()}
}
