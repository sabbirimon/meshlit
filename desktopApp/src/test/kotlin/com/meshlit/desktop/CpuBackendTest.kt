package com.meshlit.desktop

import org.junit.Test
import kotlin.test.*
class CpuBackendTest {
    @Test fun optimizedCodeRequiresBothVerifiedCpuOsFeaturesAndPackagedBackend() {
        assertEquals("llama-server-avx2",selectCpuVariant(CpuMode.AUTO,"avx2-fma-f16c",true))
        assertEquals("llama-server",selectCpuVariant(CpuMode.AUTO,"baseline",true))
        assertEquals("llama-server",selectCpuVariant(CpuMode.AUTO,"avx2-fma-f16c",false))
        assertEquals("llama-server",selectCpuVariant(CpuMode.BASELINE,"avx2-fma-f16c",true))
        assertFailsWith<IllegalArgumentException> {selectCpuVariant(CpuMode.AVX2,"baseline",true)}
        assertFailsWith<IllegalArgumentException> {selectCpuVariant(CpuMode.AUTO,"unsupported",true)}
        assertFailsWith<IllegalArgumentException> {selectCpuVariant(CpuMode.AVX2,"avx2-fma-f16c",false)}
    }
}
