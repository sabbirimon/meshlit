package com.meshlit.network

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.meshlit.workspace.VpnNetworkObservation

/** Reads only this app's effective default network; never starts a VPN or infers its owner. */
class AndroidVpnObserver(private val context: Context) {
    companion object { const val TAILSCALE_PACKAGE = "com.tailscale.ipn" }
    private val preferences = context.getSharedPreferences("vpn-observation", Context.MODE_PRIVATE)
    fun enabled() = preferences.getBoolean("enabled", false)
    fun setEnabled(enabled: Boolean) { check(preferences.edit().putBoolean("enabled", enabled).commit()) { "Cannot save VPN observation preference" } }
    fun launchIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(TAILSCALE_PACKAGE)?.setPackage(TAILSCALE_PACKAGE)
    fun read(): VpnNetworkObservation {
        check(enabled()) { "VPN observations are off" }
        val installed = try { context.packageManager.getPackageInfo(TAILSCALE_PACKAGE, 0); true }
            catch(e: PackageManager.NameNotFoundException) { false } catch(e: SecurityException) { null }
        val cm = context.getSystemService(ConnectivityManager::class.java)
            ?: return VpnNetworkObservation(System.currentTimeMillis(), null, null, null, null, null, emptyList(), installed)
        val network = cm.activeNetwork
        val caps = network?.let(cm::getNetworkCapabilities)
        val link = network?.let(cm::getLinkProperties)
        return VpnNetworkObservation(System.currentTimeMillis(), network != null,
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_VPN),
            caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            caps?.let { !it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) },
            link?.interfaceName, link?.linkAddresses?.take(32)?.map { it.toString() }.orEmpty(), installed)
    }
}
