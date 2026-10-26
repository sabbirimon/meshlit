package com.meshlit.network

import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowNetworkCapabilities
import org.robolectric.shadows.ShadowNetworkInfo

@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, sdk = [33])
class AndroidVpnObserverTest {
    private fun context(): Context = RuntimeEnvironment.getApplication()
    @Test fun observationRequiresPersistedOptInAndRegistersNoBackgroundMonitor() {
        val context = context(); val observer = AndroidVpnObserver(context)
        assertFalse(observer.enabled()); assertThrows(IllegalStateException::class.java) { observer.read() }
        observer.setEnabled(true); assertTrue(AndroidVpnObserver(context).enabled())
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val before = shadowOf(cm).networkCallbacks.size
        observer.read(); assertEquals(before, shadowOf(cm).networkCallbacks.size)
        observer.setEnabled(false); assertThrows(IllegalStateException::class.java) { observer.read() }
    }
    @Test fun realAndroidApiMappingSeparatesVpnTransportFromInstalledCompanion() {
        val context = context(); val cm = context.getSystemService(ConnectivityManager::class.java)
        val shadow = shadowOf(cm)
        shadow.setActiveNetworkInfo(ShadowNetworkInfo.newInstance(NetworkInfo.DetailedState.CONNECTED, ConnectivityManager.TYPE_WIFI, 0, true, true))
        val network = cm.activeNetwork!!
        val caps = ShadowNetworkCapabilities.newInstance()
        shadowOf(caps).addTransportType(NetworkCapabilities.TRANSPORT_VPN)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        shadow.setNetworkCapabilities(network, caps)
        val link = LinkProperties().apply { interfaceName = "tun-fixture" }
        val address = LinkAddress::class.java.getDeclaredConstructor(String::class.java).newInstance("100.64.0.1/32")
        LinkProperties::class.java.getDeclaredMethod("addLinkAddress", LinkAddress::class.java).invoke(link, address)
        shadow.setLinkProperties(network, link)
        shadowOf(context.packageManager).installPackage(PackageInfo().apply {
            packageName = AndroidVpnObserver.TAILSCALE_PACKAGE
            applicationInfo = ApplicationInfo().apply { packageName = AndroidVpnObserver.TAILSCALE_PACKAGE }
        })
        val observer = AndroidVpnObserver(context); observer.setEnabled(true)
        val result = observer.read()
        assertEquals(true, result.vpnForApp); assertEquals(true, result.tailscaleInstalled)
        assertEquals(true, result.validated); assertEquals(false, result.metered)
        assertTrue(result.provider.contains("unavailable")); assertEquals("tun-fixture", result.interfaceName); assertTrue(result.addresses.single().contains("100.64.0.1"))
        shadowOf(caps).removeTransportType(NetworkCapabilities.TRANSPORT_VPN)
        assertEquals(false, observer.read().vpnForApp)
    }
    @Test fun noNetworkAndPermissionFailureCannotBecomeConnected() {
        val context = context(); val observer = AndroidVpnObserver(context); observer.setEnabled(true)
        shadowOf(context.getSystemService(ConnectivityManager::class.java)).setDefaultNetworkActive(false)
        assertEquals(false, observer.read().hasNetwork); assertNull(observer.read().vpnForApp)
        val denied = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? { if(name == Context.CONNECTIVITY_SERVICE) throw SecurityException("denied"); return super.getSystemService(name) }
        }
        assertThrows(SecurityException::class.java) { AndroidVpnObserver(denied).read() }
    }
}
