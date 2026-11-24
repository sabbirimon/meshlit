package com.meshlit.openclaw

import android.content.Intent
import android.content.pm.PackageManager

internal data class LaunchableApp(val label: String, val packageName: String)

internal fun launchableApps(pm: PackageManager): List<LaunchableApp> {
    val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(launcher, 0)
        .map { LaunchableApp(it.loadLabel(pm).toString(), it.activityInfo.packageName) }
        .distinct()
}
