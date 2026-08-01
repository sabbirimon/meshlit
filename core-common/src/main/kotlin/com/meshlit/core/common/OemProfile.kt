package com.meshlit.core.common

import kotlinx.serialization.Serializable

/**
 * Detected OEM behavior profile. Meshlit runs on AOSP, HarmonyOS, and
 * a long tail of Chinese Android forks (MIUI / HyperOS, EMUI, ColorOS,
 * OriginOS, OneUI, FunTouchOS, MagicOS). Each one has its own quirks
 * around foreground services, autostart, push channels, and battery
 * whitelist. We detect the OEM at first launch and adapt.
 *
 * Why this matters: the user story differs across OEMs. A Samsung
 * Galaxy S24 with OneUI 6 just works as expected. A Xiaomi 14 with
 * HyperOS will silently kill our FGS after 30 minutes of idle unless
 * the user grants autostart permission. A Huawei P50 with HarmonyOS
 * NEXT has no GMS at all — we can't use FCM there.
 *
 * Each [OemProfile] carries:
 *  - display labels
 *  - the autostart setting deep link (where available)
 *  - the battery-optimization deep link
 *  - whether the OEM has its own push channel we should adopt
 *  - whether the FGS is lifecycle-equivalent to AOSP (or stricter)
 *
 * The "additional setup steps" list is what the [OemSetupWizard]
 * walks the user through. Some devices need 3 steps; some need 6.
 */
@Serializable
enum class OemProfile(
    val tag: String,
    val displayName: String,
    val osFamily: OsFamily,
    /** Whether the OEM kills FGS aggressively without whitelist. */
    val killsFgsAggressively: Boolean,
    /** Whether the OEM requires a separate "autostart" permission. */
    val requiresAutostartPermission: Boolean,
    /** Whether the OEM has its own push channel we should adopt. */
    val pushChannel: PushChannel,
    /** Whether the OEM ships Google Mobile Services. */
    val hasGms: Boolean,
    /** Whether the OEM ships Huawei Mobile Services. */
    val hasHms: Boolean,
    /**
     * Deep link to the OEM's autostart / protected apps screen.
     * `null` means AOSP — no special step needed.
     */
    val autostartSettingsIntent: String? = null,
    /**
     * Deep link to the OEM's battery whitelist / "do not optimize" screen.
     * Most OEMs use AOSP Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
     * but some (Xiaomi, Huawei) have an extra "protected apps" screen.
     */
    val batteryProtectedAppsIntent: String? = null,
    /** Order of setup steps the user has to complete on first launch. */
    val setupSteps: List<OemSetupStep>,
) {
    AOSP(
        tag = "aosp",
        displayName = "Stock Android",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = false,
        requiresAutostartPermission = false,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    PIXEL(
        tag = "pixel",
        displayName = "Google Pixel",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = false,
        requiresAutostartPermission = false,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    SAMSUNG_ONEUI(
        tag = "samsung_oneui",
        displayName = "Samsung One UI",
        osFamily = OsFamily.ANDROID,
        // OneUI 6 is well-behaved; older OneUI was less so.
        killsFgsAggressively = false,
        requiresAutostartPermission = false,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),

    // --- Chinese Android forks (each has its own quirks) ---
    XIAOMI_MIUI(
        tag = "xiaomi_miui",
        displayName = "Xiaomi MIUI / HyperOS",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.MI_PUSH,
        hasGms = true,
        hasHms = false,
        autostartSettingsIntent = "miui.intent.action.OP_AUTO_START",
        batteryProtectedAppsIntent = "miui.intent.action.POWER_HIDE_MODE_APP_LIST",
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
            OemSetupStep.BATTERY_SAVER_DISABLE,
            OemSetupStep.MI_PUSH_OPT_IN,
        ),
    ),
    HUAWEI_EMUI(
        tag = "huawei_emui",
        displayName = "Huawei EMUI",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.HMS_PUSH,
        hasGms = false,
        hasHms = true,
        autostartSettingsIntent = "huawei.intent.action.HSM_BOOTAPP_MANAGER",
        batteryProtectedAppsIntent = "huawei.intent.action.HSM_BATTERY_OPTIMIZATION",
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
            OemSetupStep.HMS_PUSH_OPT_IN,
        ),
    ),
    HONOR_MAGICOS(
        tag = "honor_magicos",
        displayName = "Honor MagicOS",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.HMS_PUSH,
        hasGms = false,
        hasHms = true,
        autostartSettingsIntent = "honor.intent.action.HONOR_BOOTAPP_MANAGER",
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
            OemSetupStep.HMS_PUSH_OPT_IN,
        ),
    ),
    OPPO_COLOROS(
        tag = "oppo_coloros",
        displayName = "Oppo ColorOS",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        autostartSettingsIntent = "com.coloros.safecenter",
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    VIVO_ORIGINOS(
        tag = "vivo_originos",
        displayName = "Vivo OriginOS",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        autostartSettingsIntent = "com.iqoo.secure",
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    ONEPLUS_OXYGENOS(
        tag = "oneplus_oxygenos",
        displayName = "OnePlus OxygenOS",
        osFamily = OsFamily.ANDROID,
        // Recent OxygenOS is closer to AOSP; older was heavier.
        killsFgsAggressively = false,
        requiresAutostartPermission = false,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    NUBIA_REDMAGIC(
        tag = "nubia_redmagic",
        displayName = "Nubia RedMagic",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),
    TRANSSION_XOS(
        tag = "transsion_xos",
        displayName = "Transsion XOS (Tecno / Infinix / itel)",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.AUTOSTART_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    ),

    // --- HarmonyOS NEXT (the hard one) ---
    HARMONYOS_NEXT(
        tag = "harmonyos_next",
        displayName = "HarmonyOS NEXT",
        osFamily = OsFamily.HARMONYOS,
        killsFgsAggressively = true,
        requiresAutostartPermission = true,
        pushChannel = PushChannel.HMS_PUSH,
        hasGms = false,
        hasHms = true,
        // HarmonyOS NEXT has no AOSP. Either we run via the AOSP
        // compatibility layer (best-effort) or we don't run at all.
        autostartSettingsIntent = null,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.HMS_PUSH_OPT_IN,
            OemSetupStep.HARMONYOS_COMPAT_LAYER_CHECK,
        ),
    ),

    // Catch-all for unrecognized OEMs
    UNKNOWN(
        tag = "unknown",
        displayName = "Unknown / other",
        osFamily = OsFamily.ANDROID,
        killsFgsAggressively = false,
        requiresAutostartPermission = false,
        pushChannel = PushChannel.FCM,
        hasGms = true,
        hasHms = false,
        setupSteps = listOf(
            OemSetupStep.NOTIFICATION_PERMISSION,
            OemSetupStep.BATTERY_WHITELIST,
        ),
    );

    companion object {
        fun fromTag(tag: String): OemProfile = entries.firstOrNull { it.tag == tag } ?: UNKNOWN
    }
}

@Serializable
enum class OsFamily(val tag: String) {
    ANDROID("android"),
    HARMONYOS("harmonyos"),
    OTHER("other");

    companion object {
        fun fromTag(tag: String): OsFamily = entries.firstOrNull { it.tag == tag } ?: OTHER
    }
}

@Serializable
enum class PushChannel(val tag: String, val displayName: String) {
    FCM("fcm", "Firebase Cloud Messaging"),
    HMS_PUSH("hms", "Huawei Push Kit"),
    MI_PUSH("mi_push", "Xiaomi Mi Push"),
    NONE("none", "No push channel");

    companion object {
        fun fromTag(tag: String): PushChannel = entries.firstOrNull { it.tag == tag } ?: NONE
    }
}

/**
 * Step in the OEM-specific setup wizard. Each step has a stable tag
 * so we can persist which steps the user has completed across app
 * reinstalls (we save to DataStore keyed by step).
 *
 * The rich label/description strings live in res/values/strings.xml
 * so they can be localized per OEM locale when we ship Chinese builds.
 */
enum class OemSetupStep(val tag: String) {
    /** Android 13+ POST_NOTIFICATIONS runtime permission. */
    NOTIFICATION_PERMISSION("notif_permission"),

    /** "Don't optimize battery" / unrestricted background. */
    BATTERY_WHITELIST("battery_whitelist"),

    /** OEM's "protected apps" list (extra step beyond AOSP). */
    BATTERY_SAVER_DISABLE("battery_saver_disable"),

    /** OEM's autostart toggle (MIUI / EMUI / ColorOS / OriginOS). */
    AUTOSTART_PERMISSION("autostart"),

    /** Mi Push opt-in for Xiaomi devices. */
    MI_PUSH_OPT_IN("mi_push"),

    /** HMS Push Kit opt-in for Huawei devices. */
    HMS_PUSH_OPT_IN("hms_push"),

    /** HarmonyOS NEXT compatibility layer check. */
    HARMONYOS_COMPAT_LAYER_CHECK("harmonyos_compat"),
}

/**
 * Per-detection result. The detector fills this and the wizard
 * consumes it. We persist the result to DataStore so the wizard
 * only shows once — and re-shows only if the user explicitly resets
 * the OEM setup from Settings.
 */
@Serializable
data class OemDetectionResult(
    val profile: OemProfile,
    val androidVersion: String,
    val osFamily: OsFamily,
    val requiresSetupWizard: Boolean,
    val completedSteps: Set<OemSetupStep> = emptySet(),
) {
    val remainingSteps: List<OemSetupStep>
        get() = profile.setupSteps.filter { it !in completedSteps }

    val isSetupComplete: Boolean
        get() = remainingSteps.isEmpty()
}