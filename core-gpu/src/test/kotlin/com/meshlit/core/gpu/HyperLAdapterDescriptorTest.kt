package com.meshlit.core.gpu
import org.junit.Assert.*
import org.junit.Test
class HyperLAdapterDescriptorTest {
    @Test fun proprietaryAndOpenPluginsNeedSameQualification(){
        val adapter=HyperLAdapterDescriptor("owned",HyperLTarget.CUDA,"a".repeat(64),"aarch64","Linux","1",AdapterLicenseKind.PROPRIETARY,true,true,true,true)
        assertTrue(adapter.eligible("aarch64","Linux"));assertFalse(adapter.copy(abi="",os="").eligible("",""));assertFalse(adapter.eligible("x86_64","Linux"));assertFalse(adapter.copy(signatureVerified=false).eligible("aarch64","Linux"));assertFalse(adapter.copy(licenseKind=AdapterLicenseKind.OPEN_SOURCE,deviceQualified=false).eligible("aarch64","Linux"))
    }
}
