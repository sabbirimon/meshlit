package com.meshlit.network.pcapdroid

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Bridge to the external PCAPdroid app
 * (`com.emanuelef.remote_capture`). Used by the Network Monitor
 * screen to (a) install PCAPdroid from the Play Store when it
 * isn't present, and (b) deep-link into its "start capture"
 * intent when the user wants Meshlit to use PCAPdroid's capture
 * path instead of Meshlit's own VPN service.
 */
object PcapdroidBridge {

    const val PACKAGE = "com.emanuelef.remote_capture"
    const val ACTION_START = "com.emanuelef.remote_capture.action.START_CAPTURE"
    const val ACTION_STOP = "com.emanuelef.remote_capture.action.STOP_CAPTURE"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

    /** Launch Play Store install page. */
    fun openInstall(context: Context): Boolean {
        return runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrElse { _ ->
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
                true
            }.getOrDefault(false)
        }
    }

    /** Ask PCAPdroid to start its capture. No-op when not installed. */
    fun startCapture(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(ACTION_START).setPackage(PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrElse { _ -> false }

    /** Ask PCAPdroid to stop its capture. */
    fun stopCapture(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(ACTION_STOP).setPackage(PACKAGE)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrElse { _ -> false }
}
