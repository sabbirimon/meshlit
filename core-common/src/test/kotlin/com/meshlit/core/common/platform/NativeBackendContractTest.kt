package com.meshlit.core.common.platform
import org.junit.Assert.*
import org.junit.Test
class NativeBackendContractTest {
    @Test fun kernelBackendNeedsExactProofAndSeparatePrivilegeGrant(){
        val sha="a".repeat(64)
        val proof=NativeBackendQualification("owned-host","linux-observed","x86_64",sha,100,true,true,true)
        val backend=NativeBackendDescriptor(id="ai.network.xdp",layer=ExecutionLayer.KERNEL_EXTENSION,
            hostIdentity="owned-host",osRevision="linux-observed",abi="x86_64",backendSha256=sha,
            requiredPrivileges=setOf(NativePrivilege.BPF_LOAD,NativePrivilege.RAW_NETWORK),
            ownerEnabled=true,implementationInstalled=true,qualification=proof)
        assertFalse(backend.eligible(200,emptySet()))
        assertTrue(backend.eligible(200,backend.requiredPrivileges))
        assertFalse(backend.copy(osRevision="kernel-updated").eligible(200,backend.requiredPrivileges))
        assertFalse(backend.copy(qualification=proof.copy(cancellationPassed=false)).eligible(200,backend.requiredPrivileges))
        assertFalse(backend.eligible(86_400_201,backend.requiredPrivileges))
        assertFalse(backend.copy(implementationInstalled=false).eligible(200,backend.requiredPrivileges))
    }
}
