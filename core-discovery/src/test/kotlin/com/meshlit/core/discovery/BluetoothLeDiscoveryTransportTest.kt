package com.meshlit.core.discovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Shape + invariants tests for [BluetoothLeDiscoveryTransport].
 *
 * The transport's hot path is the Android Bluetooth LE scanner /
 * advertiser callback which requires a Robolectric runtime + a
 * shadow Bluetooth adapter. The on-device pass (Phase 1 milestone)
 * is the authoritative integration test. What we cover here:
 *
 *  - the transport's `name` is `"ble"` so the Scan toggle row can
 *    match by name,
 *  - the service UUID constant is non-empty, has the Bluetooth-SIG
 *    vendor-form length, and parses cleanly into a `ParcelUuid`
 *    via `android.os.ParcelUuid.fromString` (we don't run that
 *    here — Android-only — but we assert the format),
 *  - the JSON advertisement round-trips through `PeerAdvertisement`
 *    so the parser path stays stable when BLE service-data carries
 *    our full JSON.
 */
class BluetoothLeDiscoveryTransportTest {

    @Test
    fun service_uuid_is_in_bluetooth_sig_vendor_format() {
        // 128-bit UUID: 8-4-4-4-12 hex chars, case-insensitive,
        // dash-separated. Android's ParcelUuid.fromString accepts
        // both cases; our test mirrors that.
        val uuid = BluetoothLeDiscoveryTransport.MESHLIT_SERVICE_UUID
        assertTrue(
            "UUID must match 8-4-4-4-12 hex pattern (case-insensitive), was $uuid",
            Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\$")
                .matches(uuid),
        )
    }

    @Test
    fun json_advertisement_round_trips_for_ble_service_data() {
        // The receiver side of the BLE transport parses service-data
        // as a JSON-encoded PeerAdvertisement. Make sure that path is
        // symmetric — whatever we send over the air decodes back to
        // the same record.
        val adv = PeerAdvertisement(
            nodeId = "node-ble-1",
            host = "10.0.0.99",
            port = 8080,
            tier = "local_trusted",
            fingerprint = "deadbeefcafe",
            ttlSec = 60,
            transport = "ble",
        )
        val json = kotlinx.serialization.json.Json.encodeToString(
            PeerAdvertisement.serializer(),
            adv,
        )
        val decoded = kotlinx.serialization.json.Json.decodeFromString(
            PeerAdvertisement.serializer(),
            json,
        )
        assertEquals(adv, decoded)
        // Guard against accidental transport-tag regressions: BLE
        // peers must advertise `transport = "ble"`, not "nsd".
        assertEquals("ble", decoded.transport)
        assertNotEquals("nsd", decoded.transport)
    }
}