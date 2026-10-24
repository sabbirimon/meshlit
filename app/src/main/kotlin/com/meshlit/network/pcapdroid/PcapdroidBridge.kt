package com.meshlit.network.pcapdroid

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.meshlit.core.net.capture.PcapParser

/** Original external-app handoff; PCAPdroid's code/native libraries are not bundled. */
object PcapdroidBridge {
    const val PACKAGE = "com.emanuelef.remote_capture"
    fun isInstalled(context: Context): Boolean = runCatching {
        context.packageManager.getPackageInfo(PACKAGE, 0); true
    }.getOrDefault(false)

    /** Must be launched for a result from a visible human UI, preserving companion consent. */
    fun captureIntent(context: Context, start: Boolean): Intent = Intent(Intent.ACTION_VIEW)
        .setClassName(PACKAGE, "$PACKAGE.activities.CaptureCtrl")
        .putExtra("action", if (start) "start" else "stop")
        .apply {
            if (start) {
                putExtra("app_filter", context.packageName)
                putExtra("pcap_dump_mode", "pcap_file")
                putExtra("pcap_name", "meshlit-${System.currentTimeMillis()}.pcap")
                putExtra("max_dump_size", PcapParser.MAX_FILE_BYTES.toInt())
                putExtra("root_capture", false)
                putExtra("tls_decryption", false)
                putExtra("pcapng_format", false)
            }
        }

    fun openApp(context: Context): Boolean = runCatching {
        val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE) ?: return false
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)

    /** Opens a store page only; installation stays under the user's control. */
    fun openInstall(context: Context): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrElse {
        openPage(context, "https://play.google.com/store/apps/details?id=$PACKAGE")
    }

    fun openPage(context: Context, url: String): Boolean = runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
    }.getOrDefault(false)
}
