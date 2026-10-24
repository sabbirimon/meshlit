package com.meshlit.core.gpu
import kotlinx.serialization.Serializable
@Serializable enum class AdapterLicenseKind { OPEN_SOURCE, PROPRIETARY, UNKNOWN }
@Serializable data class HyperLAdapterDescriptor(val id:String,val target:HyperLTarget,val artifactSha256:String,val abi:String,val os:String,
    val runtimeRevision:String,val licenseKind:AdapterLicenseKind=AdapterLicenseKind.UNKNOWN,val licenseReviewed:Boolean=false,
    val ownerApproved:Boolean=false,val signatureVerified:Boolean=false,val deviceQualified:Boolean=false) {
    /** Loading executable extensions remains a platform sandbox responsibility. This predicate
     * grants nothing by itself and never downloads or executes a plugin. */
    fun eligible(hostAbi:String,hostOs:String):Boolean = id.matches(Regex("[A-Za-z][A-Za-z0-9_.-]{0,63}")) && artifactSha256.matches(Regex("[a-f0-9]{64}")) &&
        abi.isNotBlank() && os.isNotBlank() && abi==hostAbi && os==hostOs && runtimeRevision.isNotBlank() && licenseKind!=AdapterLicenseKind.UNKNOWN && licenseReviewed && ownerApproved && signatureVerified && deviceQualified
}

