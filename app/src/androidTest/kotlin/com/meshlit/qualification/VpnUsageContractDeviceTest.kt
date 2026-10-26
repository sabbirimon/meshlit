package com.meshlit.qualification

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.network.AndroidVpnObserver
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.workspace.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real OS/Keystore observations, disposable accounting only; no provider/VPN changes. */
@RunWith(AndroidJUnit4::class)
class VpnUsageContractDeviceTest {
    @Test fun appNetworkObservationAndPersistedOptIn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val observer = AndroidVpnObserver(context)
        val previous = observer.enabled()
        try {
            observer.setEnabled(false)
            assertThrows(IllegalStateException::class.java) { observer.read() }
            observer.setEnabled(true)
            assertTrue(AndroidVpnObserver(context).enabled())
            val result = observer.read()
            assertTrue(vpnSnapshotIsFresh(result.observedAt, System.currentTimeMillis()))
            assertTrue(result.provider.contains("unavailable"))
            println("MESHLIT_VPN_DEVICE hasNetwork=${result.hasNetwork} vpnForApp=${result.vpnForApp} validated=${result.validated} metered=${result.metered} tailscaleInstalled=${result.tailscaleInstalled} providerIdentity=unavailable")
        } finally { observer.setEnabled(previous) }
    }
    @Test fun encryptedLedgerReopenAndAtomicCredentialMutation() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "g1-device-test-${UUID.randomUUID()}"
        try {
            val store = EncryptedCredentialStore(context, name)
            fun open() = EncryptedCredentialStore(context, name).let { backing -> UsageLedger({ backing.get("journal") }, { backing.putCommitted("journal", it) }) }
            val ledger = open()
            ledger.setPolicy(false, UsageBudget("USD", 10, 6), "USD")
            val price = UsagePrice("USD", 1, 1, "Device fixture", System.currentTimeMillis())
            val route = UsageLedger.route("https://fixture.invalid", "fixture")
            val id = ledger.begin(route, "fixture", price, 1, 1)
            val reopened = open()
            assertEquals(6L, reopened.snapshot().held("USD"))
            reopened.recoverInterrupted()
            assertThrows(IllegalArgumentException::class.java) { reopened.begin(route, "fixture", price, 1, 1) }
            reopened.reconcile(id, 0)
            assertNotNull(reopened.begin(route, "fixture", price, 1, 1))
            store.updateCommitted(mapOf("profile-fixture" to "endpoint-A", "key-fixture" to "test-only-secret-value"))
            assertEquals("test-only-secret-value", EncryptedCredentialStore(context, name).get("key-fixture"))
            val raw = File(context.applicationInfo.dataDir, "shared_prefs/$name.xml").readText()
            assertFalse(raw.contains("test-only-secret-value")); assertFalse(raw.contains(id))
            store.updateCommitted(mapOf("profile-fixture" to "endpoint-B", "key-fixture" to null))
            assertNull(EncryptedCredentialStore(context, name).get("key-fixture"))
            println("MESHLIT_G1_DEVICE encrypted=true reopen=true interruptedHold=true credentialBatch=true liveProvider=false processDeath=false")
        } finally { context.deleteSharedPreferences(name) }
    }
}
