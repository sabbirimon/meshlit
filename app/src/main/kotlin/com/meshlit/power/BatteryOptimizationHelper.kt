package com.meshlit.power

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import com.meshlit.core.common.logger

/**
 * Battery-optimization whitelist helper.
 *
 * Many OEMs (Huawei EMUI / HarmonyOS, Xiaomi MIUI, OPPO ColorOS, Samsung
 * One UI) apply aggressive battery saving that kills even foreground
 * services. The standard mitigation is to ask the user to add the
 * app to the "don't optimize" / "unrestricted" list.
 *
 * Why this isn't done automatically:
 *  - Google Play policy forbids automatic requests for
 *    [android.Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS]
 *    for everything except VoIP / alarm-clock apps. We surface the
 *    flow as a manual action from Settings.
 *  - On some OEMs there is no public Settings screen and the intent
 *    falls back to the App Info page. We try several known targets
 *    in order before giving up.
 *
 * All actions in this helper are best-effort. Failures are logged via
 * the structured logger so the UI can show a fallback hint.
 */
class BatteryOptimizationHelper(
    private val context: Context,
) {

    private val log = logger("BatteryOptimizationHelper")

    private val powerManager: PowerManager
        get() = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    /** True if the app is on the whitelist. */
    fun isIgnoringBatteryOptimizations(): Boolean {
        return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Open the system "ignore battery optimizations" dialog. The
     * single Activity result returns SUCCESS / CANCEL via the launcher
     * — callers can re-query [isIgnoringBatteryOptimizations] to
     * confirm.
     */
    fun requestIgnoreBatteryOptimizations() {
        val intent = Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        try {
            context.startActivity(intent)
            log.info("power.battery.req", "requested battery optimization whitelist")
        } catch (e: ActivityNotFoundException) {
            log.warn("power.battery.req.missing", "no whitelist activity", mapOf("err" to (e.message ?: "")))
            openAppInfoFallback()
        }
    }

    /**
     * Open the OEM-specific power management screen. Falls back to
     * the standard App Info page when the OEM doesn't expose a
     * public settings target.
     */
    fun openBatteryOptimizationSettings() {
        val candidates: List<Intent> = listOf(
            // Stock Android
            Intent(android.provider.Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            // Huawei (EMUI / HarmonyOS 4)
            Intent().setComponent(
                ComponentName(
                    "com.huawei.systemmanager",
                    "com.huawei.systemmanager.optimize.process.ProtectActivity",
                ),
            ),
            // Honor (MagicUI)
            Intent().setComponent(
                ComponentName(
                    "com.hihonor.systemmanager",
                    "com.hihonor.systemmanager.optimize.process.ProtectActivity",
                ),
            ),
            // Xiaomi (MIUI)
            Intent().setComponent(
                ComponentName(
                    "com.miui.powerkeeper",
                    "com.miui.powerkeeper.ui.HiddenAppsConfigActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.miui.securitycenter",
                    "com.miui.permcenter.autostart.AutoStartManagementActivity",
                ),
            ),
            // OPPO (ColorOS)
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.permission.startup.StartupAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.coloros.safecenter",
                    "com.coloros.safecenter.startupapp.StartupAppListActivity",
                ),
            ),
            Intent().setComponent(
                ComponentName(
                    "com.oppo.safe",
                    "com.oppo.safe.permission.startup.StartupAppListActivity",
                ),
            ),
            // Samsung (One UI)
            Intent().setComponent(
                ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity",
                ),
            ),
            // Vivo (FuntouchOS)
            Intent().setComponent(
                ComponentName(
                    "com.vivo.permissionmanager",
                    "com.vivo.permissionmanager.activity.BgStartUpManagerActivity",
                ),
            ),
        )

        for (rawIntent in candidates) {
            val intent = rawIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            try {
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    log.info("power.battery.oem", "opened OEM battery screen")
                    return
                }
            } catch (e: Throwable) {
                // Try next candidate.
            }
        }
        log.warn("power.battery.oem", "no OEM battery screen found, falling back to app info")
        openAppInfoFallback()
    }

    private fun openAppInfoFallback() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        runCatching { context.startActivity(intent) }
            .onFailure {
                log.warn("power.battery.fallback.fail", "could not open app info", mapOf("err" to (it.message ?: "")))
            }
    }

    /**
     * Returns true if the OS supports the request at all (it's a
     * stock-Android feature; some OEMs omit it).
     */
    fun hasIgnoreBatteryOptimizationsFeature(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
    }
}
