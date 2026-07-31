package com.meshlit.diagnostics

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.meshlit.core.common.OemDetectionResult
import com.meshlit.core.common.OemProfile
import com.meshlit.core.common.OsFamily
import com.meshlit.core.common.logger

/**
 * Detects the device's OEM behavior profile from `Build.*` fields
 * and well-known package / system property fingerprints. The
 * returned [OemDetectionResult] drives the first-launch OEM setup
 * wizard.
 *
 * Detection is **deliberately conservative**. When in doubt, we
 * fall back to `OemProfile.AOSP` or `UNKNOWN` — claiming a device
 * is "MIUI with aggressive FGS killing" when it's actually a
 * well-behaved AOSP device would surface confusing "give us
 * autostart" steps to users who don't need them.
 *
 * We use three signals:
 *  1. `Build.MANUFACTURER` and `Build.BRAND` (most reliable)
 *  2. Presence of OEM system packages (e.g. `com.miui.securitycenter`)
 *  3. System properties (`ro.build.version.emui`, `ro.build.version.miui`,
 *     `ro.build.version.harmony`)
 *
 * If the build fingerprint includes `HarmonyOS` or the system
 * property `ro.build.version.harmony` is present, we mark the OS
 * family as `HARMONYOS` — even if `Build.MANUFACTURER` says
 * `HUAWEI`, because HarmonyOS NEXT removes AOSP entirely and our
 * behavior must change.
 */
class AndroidOemDetector(private val context: Context) {

    private val log = logger("AndroidOemDetector")

    fun detect(): OemDetectionResult {
        val manufacturer = (Build.MANUFACTURER ?: "").lowercase()
        val brand = (Build.BRAND ?: "").lowercase()
        val model = (Build.MODEL ?: "").lowercase()
        val osFamily = detectOsFamily()
        val profile = detectProfile(manufacturer, brand, model, osFamily)
        log.info("oem.detect", "detected OEM profile", mapOf(
            "manufacturer" to manufacturer,
            "brand" to brand,
            "model" to model,
            "osFamily" to osFamily.tag,
            "profile" to profile.tag,
        ))
        return OemDetectionResult(
            profile = profile,
            androidVersion = Build.VERSION.RELEASE ?: "unknown",
            osFamily = osFamily,
            requiresSetupWizard = profile.setupSteps.size > 1,
        )
    }

    private fun detectOsFamily(): OsFamily {
        // HarmonyOS NEXT removes AOSP. We detect by:
        //  1. System property ro.build.version.harmony
        //  2. Harmony system packages present
        //  3. Build.VERSION.RELEASE returning a Harmony build
        val harmonyProp = readSystemProperty("ro.build.version.harmony")
        if (!harmonyProp.isNullOrBlank()) return OsFamily.HARMONYOS
        if (isPackageInstalled("com.huawei.hms")) {
            // HMS Core is present even on EMUI; OS family is still Android
            // unless we see the HarmonyOS property.
        }
        val harmonyPackages = listOf(
            "com.huawei.systemservice",
            "com.huawei.hwdetect",
        )
        if (harmonyPackages.any { isPackageInstalled(it) } && harmonyProp != null) {
            return OsFamily.HARMONYOS
        }
        return OsFamily.ANDROID
    }

    private fun detectProfile(
        manufacturer: String,
        brand: String,
        model: String,
        osFamily: OsFamily,
    ): OemProfile {
        // 1. Honor HarmonyOS NEXT first — it changes everything.
        if (osFamily == OsFamily.HARMONYOS) return OemProfile.HARMONYOS_NEXT

        // 2. Manufacturer-specific detection.
        return when {
            // Pixel / AOSP-flavored devices — Build.BRAND is "google" or "android"
            brand == "google" || brand == "android" -> OemProfile.PIXEL

            // Samsung One UI
            manufacturer == "samsung" || brand == "samsung" -> OemProfile.SAMSUNG_ONEUI

            // Xiaomi — MIUI / HyperOS. Brand is "xiaomi" or "redmi"; check for
            // MIUI system package to confirm.
            manufacturer == "xiaomi" || manufacturer == "redmi" ||
                brand == "xiaomi" || brand == "redmi" ||
                isPackageInstalled("com.miui.securitycenter") ||
                isPackageInstalled("com.miui.system") -> OemProfile.XIAOMI_MIUI

            // Huawei — older EMUI. Modern Huawei may have switched to
            // HarmonyOS NEXT, which we already returned above.
            manufacturer == "huawei" || brand == "huawei" -> OemProfile.HUAWEI_EMUI

            // Honor — split off from Huawei. MagicOS.
            manufacturer == "honor" || brand == "honor" -> OemProfile.HONOR_MAGICOS

            // Oppo — ColorOS
            manufacturer == "oppo" || brand == "oppo" ||
                isPackageInstalled("com.coloros.safecenter") -> OemProfile.OPPO_COLOROS

            // Vivo — OriginOS / FunTouchOS
            manufacturer == "vivo" || brand == "vivo" ||
                isPackageInstalled("com.iqoo.secure") -> OemProfile.VIVO_ORIGINOS

            // OnePlus — OxygenOS
            manufacturer == "oneplus" || brand == "oneplus" -> OemProfile.ONEPLUS_OXYGENOS

            // Nubia / RedMagic gaming phones
            manufacturer == "nubia" || brand == "nubia" ||
                manufacturer == "zte" -> OemProfile.NUBIA_REDMAGIC

            // Transsion group — Tecno, Infinix, itel (Africa / SEA / LATAM)
            manufacturer == "tecno" || brand == "tecno" ||
                manufacturer == "infinix" || brand == "infinix" -> OemProfile.TRANSSION_XOS

            else -> OemProfile.UNKNOWN
        }
    }

    private fun isPackageInstalled(packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        } catch (_: Throwable) {
            false
        }
    }

    private fun readSystemProperty(key: String): String? {
        return try {
            @Suppress("PrivateApi")
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)
            getMethod.invoke(null, key) as? String
        } catch (_: Throwable) {
            null
        }
    }
}