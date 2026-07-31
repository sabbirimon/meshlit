package com.meshlit.core.common

/**
 * Apply an online [DeviceDatabaseSnapshot] on top of the local inference
 * to upgrade chipset / GPU / NPU detection with community-curated rules.
 *
 * The local [SocFamily] / [GpuFamily] enums stay the source of truth —
 * the snapshot *enhances* detection by adding patterns the local enum
 * doesn't know about yet, and by overriding inference-fit / NPU presence
 * for known chipsets when the local heuristic is wrong.
 *
 * Offline-first: when no snapshot is loaded, [inferSocFamily] /
 * [inferGpuFamily] / [inferNpuPresence] run as today.
 */
class DeviceDatabaseMatcher(private val snapshot: DeviceDatabaseSnapshot?) {

    /**
     * Run the database's chipset matchers first. If none match, fall
     * back to the local heuristic.
     */
    fun matchSocFamily(
        hardware: String,
        socManufacturer: String?,
        model: String,
        androidSdkInt: Int,
    ): SocFamily? {
        if (snapshot == null) return null
        val hw = hardware.lowercase()
        val sm = socManufacturer?.lowercase()
        val m = model.lowercase()
        for (def in snapshot.chipsets) {
            if (matches(def.matchOn, hw, sm, m, androidSdkInt)) {
                return SocFamily.fromTag(def.tag)
            }
        }
        return null
    }

    fun matchGpuFamily(rendererString: String): GpuFamily? {
        if (snapshot == null) return null
        val r = rendererString.lowercase()
        for (def in snapshot.gpus) {
            if (def.rendererContains.any { it.lowercase() in r }) {
                return GpuFamily.fromTag(def.tag)
            }
        }
        return null
    }

    fun matchNpu(socFamily: SocFamily): Pair<Boolean, String?>? {
        if (snapshot == null) return null
        for (def in snapshot.npus) {
            if (def.socFamily.any { it == socFamily.tag }) {
                return true to def.name
            }
        }
        return null
    }

    fun inferenceFitFor(socFamily: SocFamily): InferenceFit? {
        if (snapshot == null) return null
        return snapshot.chipsets.firstOrNull { it.tag == socFamily.tag }?.inferenceFit
    }

    private fun matches(
        rules: ChipsetMatchRules,
        hw: String,
        sm: String?,
        modelLower: String,
        androidSdkInt: Int,
    ): Boolean {
        if (rules.minAndroidSdk != null && androidSdkInt < rules.minAndroidSdk) return false
        if (rules.hardware != null && rules.hardware.none { hw.startsWith(it.lowercase()) }) return false
        if (rules.socManufacturer != null && (sm == null || rules.socManufacturer.none { sm == it.lowercase() })) return false
        if (rules.socModel != null && rules.socModel.none { prefix ->
                // Match socModel prefix case-insensitively. socModel typically empty here;
                // we can also match against hardware/model since the matcher fills in from those.
                hw.startsWith(prefix.lowercase()) || modelLower.contains(prefix.lowercase())
            }) return false
        if (rules.modelContains != null && rules.modelContains.none { modelLower.contains(it.lowercase()) }) return false
        return true
    }
}