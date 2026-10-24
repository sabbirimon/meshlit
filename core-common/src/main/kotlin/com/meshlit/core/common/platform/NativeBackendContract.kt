package com.meshlit.core.common.platform

import kotlinx.serialization.Serializable

@Serializable enum class ExecutionLayer { USER_RUNTIME, DEVICE_DRIVER, KERNEL_EXTENSION, BARE_METAL }
@Serializable enum class NativePrivilege { DEVICE_QUEUE, PINNED_MEMORY, RAW_NETWORK, BPF_LOAD, KERNEL_MODULE, DMA, CPU_AFFINITY }
/** Qualification describes an exact host/backend revision. Root and hardware names do
 * not establish this proof, and this contract never loads modules or grants privileges. */
@Serializable data class NativeBackendQualification(
    val hostIdentity:String, val osRevision:String, val abi:String, val backendSha256:String,
    val testedAtMs:Long, val correctnessPassed:Boolean, val isolationPassed:Boolean,
    val cancellationPassed:Boolean
) {
    fun valid(host:String,os:String,targetAbi:String,digest:String,nowMs:Long):Boolean =
        hostIdentity.isNotBlank() && osRevision.isNotBlank() && abi.isNotBlank() &&
        backendSha256.matches(Regex("[0-9a-f]{64}")) && hostIdentity==host && osRevision==os &&
        abi==targetAbi && backendSha256==digest && correctnessPassed && isolationPassed &&
        cancellationPassed && testedAtMs>0 && nowMs>=testedAtMs && nowMs-testedAtMs<=86_400_000
}
@Serializable data class NativeBackendDescriptor(
    val schema:Int=1,val id:String,val layer:ExecutionLayer,val hostIdentity:String,
    val osRevision:String,val abi:String,val backendSha256:String,
    val requiredPrivileges:Set<NativePrivilege> = emptySet(),val ownerEnabled:Boolean=false,
    val implementationInstalled:Boolean=false,val qualification:NativeBackendQualification?=null
) {
    fun eligible(nowMs:Long,ownerGranted:Set<NativePrivilege>):Boolean = schema==1 &&
        id.matches(Regex("[a-z][a-z0-9_.-]{0,63}")) && ownerEnabled && implementationInstalled &&
        ownerGranted.containsAll(requiredPrivileges) &&
        qualification?.valid(hostIdentity,osRevision,abi,backendSha256,nowMs)==true
}
