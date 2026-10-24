package com.meshlit.desktop

internal enum class CpuMode(val label: String) { AUTO("Auto · verified CPU features"), BASELINE("Compatible CPU"), AVX2("AVX2 · requires CPU/OS support") }
internal fun selectCpuVariant(mode: CpuMode, features: String, fastAvailable: Boolean): String {
    require(features in setOf("baseline", "avx2-fma-f16c")) { "CPU/OS does not satisfy this engine's minimum requirements" }
    if (mode == CpuMode.AVX2) require(fastAvailable && features == "avx2-fma-f16c") { "Verified AVX2/FMA/F16C backend is unavailable" }
    return if (mode != CpuMode.BASELINE && fastAvailable && features == "avx2-fma-f16c") "llama-server-avx2" else "llama-server"
}
