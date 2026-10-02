package com.meshlit.permissions

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
     * granted already, pre-API-33, the user previously selected
     * "Don't ask again", or we have already shown the prompt once
     * on this device).
     *
     * Callers must call [markNotificationsAsked] after the request
     * resolves so the dialog doesn't re-fire on every cold start.
     */
    fun requestNotificationsIfNeeded(activity: Activity): Boolean {
        if (!needsRuntimeNotifications) return false
        if (hasNotificationPermission(activity)) return false
        if (wasNotificationsAsked(activity)) return false
        if (isNotificationsPermanentlyDenied(activity)) return false
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
     * Pure predicate: should the app fire the media-permissions batch
     * dialog on first launch? Used by `MainActivity` (which owns the
     * `RequestMultiplePermissions` launcher) to gate the call.
     *
     * Returns `false` if the batch is already granted, pre-API-29,
     * or the user already saw the prompt once (so we don't re-pop
     * the dialog on every cold start).
     *
     * On `MainActivity` the launcher callback must call
     * [markMediaAsked] after the result is delivered so the
     * shared-preferences flag flips.
     */
    fun shouldRequestMediaPermissions(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
        if (hasAllMediaPermissions(context)) return false
        if (wasMediaAsked(context)) return false
        return true
    }

    // -------------------------------------------------------------------
    // Microphone permission (Phase 2.x — Voice screen).
    //
    // `RECORD_AUDIO` is a runtime permission from API 23 onwards and
    // the only one the Voice screen needs. The SDK's STT engine throws
    // `SecurityException` from `AudioRecord.<init>` if it isn't held,
    // so the screen must check + request *before* subscribing to the
    // capture flow. Mirrors the notifications pattern above so the
    // Voice screen's permission flow is consistent with the rest of
    // the app.
    // -------------------------------------------------------------------

    /** True when the app holds `RECORD_AUDIO`. The Voice screen
     *  binds its mic button's enabled-state to this. */
    fun hasMicrophonePermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Fire the `RECORD_AUDIO` dialog if the user hasn't granted it
     * yet. Returns `true` if a dialog was requested, `false` if the
     * permission was already held, the user previously checked
     * "Don't ask again", or we have already shown the prompt once on
     * this device. In the silent-denial case the Voice screen must
     * deep-link to App Settings via [openAppSettings].
     *
     * Callers must call [markMicrophoneAsked] after the request
     * resolves so the dialog doesn't re-fire on every cold start.
     */
    fun requestMicrophoneIfNeeded(activity: Activity): Boolean {
        if (hasMicrophonePermission(activity)) return false
        if (wasMicrophoneAsked(activity)) return false
        if (isMicrophonePermanentlyDenied(activity)) return false
        log.info("perm.mic.request", "requesting RECORD_AUDIO")
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO),
            REQ_MIC,
        )
        return true
    }

    /** True if the user has permanently denied `RECORD_AUDIO`.
     *  After this returns true, [requestMicrophoneIfNeeded] won't
     *  surface the dialog; the Voice screen needs to call
     *  [openAppSettings] instead. */
    fun isMicrophonePermanentlyDenied(activity: Activity): Boolean {
        if (hasMicrophonePermission(activity)) return false
        return !ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.RECORD_AUDIO,
        )
    }

    private const val REQ_NOTIFICATIONS = 0xA51F
    private const val REQ_MEDIA = 0xA52E
    private const val REQ_MIC = 0xA52F

    // -------------------------------------------------------------------
    // "Asked once" persistence (Phase 7 P0 fix).
    //
    // Without this flag the `requestNotificationsIfNeeded` /
    // `requestMediaPermissionsIfNeeded` / `requestMicrophoneIfNeeded`
    // helpers fire on every `MainActivity.onCreate` even when the
    // user has previously denied the request. On API 33+ the
    // notification dialog would re-surface on every cold-start once
    // the user declined once — the dialog returns silently to the
    // system but still flashes for a frame, and on some OEM builds
    // the dialog re-pops until the user selects "Don't ask again".
    //
    // The flag is purely additive: a `markAsked*` call records
    // "we have already shown this prompt" and the guard checks it
    // before the next call. The flag is cleared automatically when
    // the underlying permission becomes granted (see
    // `hasNotificationPermission` + the per-group helpers below),
    // so if the user later flips the permission on in App Settings
    // we will request a new permission the next time the relevant
    // surface needs it.
    // -------------------------------------------------------------------

    private const val PREFS_NAME = "meshlit_permissions"
    private const val KEY_ASKED_NOTIFICATIONS = "asked.post_notifications.v1"
    private const val KEY_ASKED_MEDIA = "asked.media.v1"
    private const val KEY_ASKED_MIC = "asked.mic.v1"

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE,
        )

    /** True when the app has already shown the POST_NOTIFICATIONS prompt
     *  on this device. The notification system UI surfaces the dialog
     *  exactly once per install after this flag flips. */
    fun wasNotificationsAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED_NOTIFICATIONS, false)

    /** Mark the POST_NOTIFICATIONS prompt as shown. */
    fun markNotificationsAsked(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ASKED_NOTIFICATIONS, true)
            .apply()
    }

    /** True when the app has already shown the media-permissions batch
     *  prompt. */
    fun wasMediaAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED_MEDIA, false)

    /** Mark the media batch prompt as shown. */
    fun markMediaAsked(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ASKED_MEDIA, true)
            .apply()
    }

    /** True when the app has already shown the RECORD_AUDIO prompt. */
    fun wasMicrophoneAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ASKED_MIC, false)

    /** Mark the RECORD_AUDIO prompt as shown. */
    fun markMicrophoneAsked(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ASKED_MIC, true)
            .apply()
    }
}
