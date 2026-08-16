package com.meshlit.network.termux

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import java.io.File

/**
 * Bridge to the Termux app (`com.termux`). Builds a one-tap
 * `tcpdump` capture script for users who want full device capture
 * through Termux instead of the Meshlit VpnService. Capture files
 * land in the standard Download directory and are then picked up
 * by the Network Monitor's "External capture" tab.
 *
 * Behaviour:
 * - If Termux isn't installed, the bridge returns the Play Store
 *   URL — the caller surfaces a "Install Termux" CTA.
 * - If it is installed, the bridge fires a `termux://` deep-link
 *   with a ready-to-run `tcpdump -i any -w <file>` command.
 */
object TermuxBridge {

    const val PACKAGE = "com.termux"

    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0)
        true
    }.getOrDefault(false)

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

    /**
     * Build a `termux://` deep-link that runs `tcpdump` in the
     * background and writes a `.pcap` file to the standard
     * Download directory. The filename embeds the timestamp so
     * repeated runs don't collide.
     */
    fun startCaptureIntent(): Intent {
        val timestamp = System.currentTimeMillis()
        val file = File(Environment.getExternalStorageDirectory(), "Download/meshlit-$timestamp.pcap")
        val command = "tcpdump -i any -w ${file.absolutePath}"
        val uri = Uri.parse("com.termux://com.termux.app.RunCommand?command=" +
            Uri.encode(command) + "&action=" + Uri.encode("run-in-background"))
        return Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    fun startCapture(context: Context): Boolean = runCatching {
        context.startActivity(startCaptureIntent())
        true
    }.getOrDefault(false)

    /**
     * Path the Network Monitor watches for new captures. We don't
     * watch DirectoryObserver ourselves — the standard MediaStore
     * scanner picks up new files in Download/ and the user can
     * pick them via the file picker.
     */
    fun downloadDir(): File =
        File(Environment.getExternalStorageDirectory(), "Download")
}
