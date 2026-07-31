package com.meshlit.core.common

/**
 * Apply an online [EGpuDatabaseSnapshot] to upgrade a raw eGPU
 * detection into a fully-described [EGpuConnection]. Falls back to
 * local heuristics when no snapshot is loaded (offline-first).
 *
 * The local [EGpuKind] enum stays the source of truth for the
 * closed set of well-known vendors; the snapshot adds finer-grained
 * enclosure identifiers (specific VID/PIDs, specific OCuLink boxes,
 * upcoming MTT models, etc.).
 */
class EGpuMatcher(private val snapshot: EGpuDatabaseSnapshot?) {

    /**
     * Resolve a detected eGPU into the best [EGpuConnection] we can.
     * Returns `null` if the matcher can't identify the device and
     * there are no heuristics to fall back on.
     */
    fun resolve(
        vendorId: Int?,
        productId: Int?,
        productName: String?,
        glRenderer: String?,
        driVendor: String?,
    ): EGpuConnection? {
        if (snapshot != null) {
            for (def in snapshot.enclosures) {
                if (matches(def.matchOn, vendorId, productId, productName, glRenderer, driVendor)) {
                    return EGpuConnection(
                        kind = EGpuKind.fromTag(def.kind.tag),
                        displayName = def.displayName,
                        transport = def.transport,
                        vendorId = vendorId,
                        productId = productId,
                        vramMb = def.vramMb,
                        estimatedTflopsF16 = def.estimatedTflopsF16,
                        powerBudgetWatts = def.powerBudgetWatts,
                        pcieGeneration = def.pcieGeneration,
                        driverStatus = def.driverStatus,
                        preferredBackend = def.preferredBackend,
                    )
                }
            }
        }
        // No snapshot match. Try local heuristics by vendor name fragments.
        return localHeuristic(vendorId, productId, productName, glRenderer)
    }

    private fun matches(
        rules: EGpuMatchRules,
        vid: Int?,
        pid: Int?,
        productName: String?,
        glRenderer: String?,
        driVendor: String?,
    ): Boolean {
        if (rules.vendorId != null && vid != rules.vendorId) return false
        if (rules.productId != null && pid != rules.productId) return false
        if (rules.vendorIdAndProductNameContains != null) {
            val (wantVid, wantName) = rules.vendorIdAndProductNameContains
            if (vid != wantVid) return false
            val name = productName?.lowercase().orEmpty()
            if (wantName.none { it.lowercase() in name }) return false
        }
        if (rules.glRendererContains != null) {
            val r = glRenderer?.lowercase().orEmpty()
            if (rules.glRendererContains.none { it.lowercase() in r }) return false
        }
        if (rules.driVendorContains != null) {
            val d = driVendor?.lowercase().orEmpty()
            if (rules.driVendorContains.none { it.lowercase() in d }) return false
        }
        return true
    }

    /**
     * Fallback for when the snapshot is empty (cold cache, first run,
     * offline). Covers the most common eGPU enclosures by vendor.
     * Doesn't try to be exhaustive — just enough that the user sees
     * a sensible "AMD eGPU" or "NVIDIA eGPU" label instead of
     * "Unknown".
     */
    private fun localHeuristic(
        vid: Int?,
        pid: Int?,
        productName: String?,
        glRenderer: String?,
    ): EGpuConnection? {
        val name = productName?.lowercase().orEmpty()
        val renderer = glRenderer?.lowercase().orEmpty()
        val combined = "$name $renderer"

        return when {
            // ASUS ROG XG Mobile — VID 0x0b05, multiple SKUs
            vid == 0x0b05 && pid in listOf(0x18e5, 0x18e7, 0x18e9, 0x18ec) ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_AMD_RADEON,
                    displayName = "ASUS ROG XG Mobile",
                    transport = EGpuTransport.USB4,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // Razer Core (TB3) — VID 0x1de7
            vid == 0x1de7 ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_AMD_RADEON,
                    displayName = "Razer Core (TB3 eGPU)",
                    transport = EGpuTransport.THUNDERBOLT_3,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // GPD G1 — VID 0x2e95, classic 2024 mini-eGPU
            vid == 0x2e95 ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_AMD_RADEON,
                    displayName = "GPD G1 (AMD Radeon RX 7600M XT)",
                    transport = EGpuTransport.OCULINK,
                    vendorId = vid,
                    productId = pid,
                    vramMb = 8192,
                    powerBudgetWatts = 120,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // OneXGPU / One-Netbook — VID 0x1bcf
            vid == 0x1bcf ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_AMD_RADEON,
                    displayName = "OneXGPU",
                    transport = EGpuTransport.USB4,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // Minisforum DEG1 — VID 0x1e5f
            vid == 0x1e5f ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_AMD_RADEON,
                    displayName = "Minisforum DEG1",
                    transport = EGpuTransport.OCULINK,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // NVIDIA eGPU boxes (rare but exist — Sonnet Breakaway Box with RTX)
            combined.contains("nvidia") && renderer.contains("geforce") ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_NVIDIA_GEFORCE,
                    displayName = "NVIDIA GeForce (eGPU)",
                    transport = EGpuTransport.THUNDERBOLT_3,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.THROUGH_ZLUDA_OR_REMOTE,
                    preferredBackend = EGpuBackend.CUDA_OR_VULKAN,
                )
            // Intel Arc eGPU (TB4 enclosures from Lenovo, ASUS NUC)
            combined.contains("intel") && (renderer.contains("arc") || name.contains("arc")) ->
                EGpuConnection(
                    kind = EGpuKind.DISCRETE_INTEL_ARC,
                    displayName = "Intel Arc (eGPU)",
                    transport = EGpuTransport.THUNDERBOLT_4,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.VULKAN,
                )
            // Moore Threads MTT — Chinese, USB-IF shows up as 0x1ea7 or similar
            combined.contains("moore threads") || combined.contains("mtt") ->
                EGpuConnection(
                    kind = EGpuKind.MOORE_THREADS_MTT,
                    displayName = "Moore Threads MTT (eGPU)",
                    transport = EGpuTransport.USB4,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.LINUX_ONLY,
                    preferredBackend = EGpuBackend.MUSA_OR_OPENCL,
                )
            // Huawei Ascend — typical on Huawei laptops / Atlas servers
            combined.contains("huawei") && (combined.contains("ascend") || combined.contains("atlas")) ->
                EGpuConnection(
                    kind = EGpuKind.HUAWEI_ASCEND,
                    displayName = "Huawei Ascend (eGPU / peer)",
                    transport = EGpuTransport.LAN,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.LINUX_ONLY,
                    preferredBackend = EGpuBackend.CANN,
                )
            // Cambricon MLU — typical on Chinese edge servers
            combined.contains("cambricon") || combined.contains("mlu") ->
                EGpuConnection(
                    kind = EGpuKind.CAMBRICON_MLU,
                    displayName = "Cambricon MLU (eGPU / peer)",
                    transport = EGpuTransport.LAN,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.LINUX_ONLY,
                    preferredBackend = EGpuBackend.NEURON_RUNTIME,
                )
            // Tenstorrent — open source, will work on Android via tt-metal
            combined.contains("tenstorrent") || combined.contains("wormhole") || combined.contains("grayskull") ->
                EGpuConnection(
                    kind = EGpuKind.TENSTORRENT_WORMHOLE,
                    displayName = "Tenstorrent accelerator",
                    transport = EGpuTransport.USB4,
                    vendorId = vid,
                    productId = pid,
                    driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                    preferredBackend = EGpuBackend.TT_METAL,
                )
            else -> null
        }
    }
}