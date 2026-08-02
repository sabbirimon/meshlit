package com.meshlit.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.meshlit.core.common.logger

/**
 * Centralised runtime permission logic for Meshlit.
 *
 * Android split runtime permissions into two buckets:
 *
 * 1. **Automatic** — `INTERNET`, `WAKE_LOCK`, `FOREGROUND_SERVICE`, the
 *    Bluetooth/Wi-Fi scan permissions. Granted at install time on
 *    every supported API level. Nothing to ask for.
 *
 * 2. **Runtime** — `POST_NOTIFICATIONS` (API 33+), `MANAGE_EXTERNAL_STORAGE`
 *    (API 30+), `READ_MEDIA_*` (API 33+). The user must explicitly
 *    approve via a system dialog or by visiting App Settings.
 *
 * For runtime permissions we expose:
 *  - [requestNotificationsIfNeeded] — fires the system dialog on
 *    API 33+. Returns `true` if we requested, `false` if it was
 *    already granted or we're on a pre-API-33 device.
 *  - [hasNotificationPermission] — quick check used by the FGS to
 *    decide whether to suppress its notification.
 *  - [manageAllFilesIntent] / [openAppSettings] — escape hatches
 *    when the user has permanently denied a runtime permission.
 *
 * On `MANAGE_EXTERNAL_STORAGE`: the bundled model extraction writes
 * to internal storage (no permission needed), but if the user
 * configures a custom model path under `Environment.getExternalStorageDirectory()`
 * they must opt-in via App Settings → "All files access". This is
 * an advanced flow — we don't request it on first launch.
 */
object PermissionHelper {

    private val log = logger("PermissionHelper")

    /** True when the device supports a runtime POST_NOTIFICATIONS grant. */
    val needsRuntimeNotifications: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** True when this app can post notifications on this device. */
    fun hasNotificationPermission(context: Context): Boolean {
        if (!needsRuntimeNotifications) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Fire the POST_NOTIFICATIONS dialog on API 33+. Returns `true`
     * if the launcher fired, `false` if no dialog is needed (either
     * granted already, or pre-API-33).
     */
    fun requestNotificationsIfNeeded(activity: Activity): Boolean {
        if (!needsRuntimeNotifications) return false
        if (hasNotificationPermission(activity)) return false
        log.info("perm.notif.request", "requesting POST_NOTIFICATIONS")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQ_NOTIFICATIONS,
        )
        return true
    }

    /**
     * True if the app has "All files access" — only relevant when the
     * user picks a custom model path under external storage. We do
     * NOT request this automatically; the Models screen surfaces the
     * opt-in flow.
     */
    fun hasManageAllFilesPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return true
    }

    /** Build the Settings intent for "All files access" on API 30+. */
    fun manageAllFilesIntent(packageName: String): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        return runCatching {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                .setData(Uri.fromParts("package", packageName, null))
        }.getOrNull()
    }

    /** Open the app's notification settings screen. Used as a fallback
     *  if the user has permanently denied POST_NOTIFICATIONS. */
    fun openAppSettings(context: Context, packageName: String): Intent {
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.fromParts("package", packageName, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** True if the user has selected "Don't ask again" for a runtime
     *  permission. After this returns true, [requestNotificationsIfNeeded]
     *  won't surface the dialog; the app must deep-link to Settings. */
    fun isNotificationsPermanentlyDenied(activity: Activity): Boolean {
        if (!needsRuntimeNotifications) return false
        if (hasNotificationPermission(activity)) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.POST_NOTIFICATIONS,
        )
    }

    /**
     * The set of media / storage permissions Meshlit declares. We ask
     * for them in one batch on first launch so the App Info screen
     * shows the full list (and the user understands why we're allowed
     * to read model files / save exports).
     */
    val mediaPermissions: Array<String>
        get() = when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
            )
            else -> arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
            )
        }

    /**
     * Are all media permissions already granted? Pre-API-29 always
     * returns true because legacy storage is granted at install.
     */
    fun hasAllMediaPermissions(context: Context): Boolean {
        return mediaPermissions.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Fire the media-permissions dialog on devices that need it. The
     * user gets one prompt — if they deny, we remember and stop
     * asking until they re-launch from cold.
     */
    fun requestMediaPermissionsIfNeeded(activity: Activity): Boolean {
        if (hasAllMediaPermissions(activity)) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        ActivityCompat.requestPermissions(
            activity,
            mediaPermissions,
            REQ_MEDIA,
        )
        return true
    }

    private const val REQ_NOTIFICATIONS = 0xA51F
    private const val REQ_MEDIA = 0xA52E
}
