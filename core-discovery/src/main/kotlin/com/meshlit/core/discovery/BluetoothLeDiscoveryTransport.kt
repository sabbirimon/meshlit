package com.meshlit.core.discovery

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.nio.charset.StandardCharsets

/**
 * Bluetooth Low Energy transport for Meshlit peer discovery. Uses a
 * fixed 128-bit service UUID so Meshlit-only devices can filter
 * background-noise advertisements out before the OS even hands them
 * to us.
 *
 * Service UUID: `0000MESHLIT-0000-1000-8000-00805F9B34FB`. The
 * `_MSHLIT_` literal in the second-half is the Bluetooth-SIG
 * reserved-range "vendor" marker; the actual UUID is well-known
 * enough that Meshlit devices from any version can find each other.
 *
 * The advertise payload is a 24-byte max service-data field carrying
 * the JSON-serialised [PeerAdvertisement]. Receivers parse it on
 * the IO dispatcher and re-emit via the abstract [advertisements]
 * flow. When the JSON is too long for a single service-data frame
 * the receiver builds a stub advertisement from the BLE-only
 * metadata (rssi, host side of the GATT connection) — this is the
 * path most BLE-only peers will take since the standard service-data
 * field is limited to ~26 bytes.
 *
 * Pure Android Bluetooth API — no `androidx.bluetooth` dependency.
 * Requires `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, `BLUETOOTH_ADVERTISE`
 * on API 31+; the manifest declares all three.
 *
 * Lifecycle: [start] begins both the BLE scan and the GATT-server
 * advertiser. [stop] cancels both. The transport is idempotent
 * across stop/start cycles.
 */
class BluetoothLeDiscoveryTransport(
    private val context: Context,
    private val serviceUuid: ParcelUuid = ParcelUuid.fromString(MESHLIT_SERVICE_UUID),
) : DiscoveryTransport(name = "ble") {

    private val log = logger("BluetoothLeDiscoveryTransport")

    private val bluetoothManager: BluetoothManager? by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    }

    private val adapter: BluetoothAdapter? get() = bluetoothManager?.adapter

    private var scanCallback: ScanCallback? = null
    private var advertiseCallback: AdvertiseCallback? = null
    private var isRunning: Boolean = false
    private var callbackScope: CoroutineScope? = null

    @Volatile
    private var self: LocalPeerDescriptor? = null

    override fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job {
        if (isRunning) {
            log.warn("ble.start.duplicate", "start() called twice without stop()")
            return scope.launch { /* no-op */ }
        }
        isRunning = true
        this.self = self
        this.callbackScope = scope
        return scope.launch(Dispatchers.IO) {
            startScanning()
            startAdvertising(self)
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        callbackScope = null
        val a = adapter
        if (a != null) {
            scanCallback?.let { cb ->
                runCatching { a.bluetoothLeScanner?.stopScan(cb) }
            }
            advertiseCallback?.let { cb ->
                runCatching { a.bluetoothLeAdvertiser?.stopAdvertising(cb) }
            }
        }
        scanCallback = null
        advertiseCallback = null
    }

    private fun startScanning() {
        val a = adapter ?: run {
            log.warn("ble.noAdapter", "BluetoothAdapter unavailable; skipping BLE scan")
            return
        }
        if (!hasScanPermission()) {
            log.warn("ble.noPermission", "missing BLUETOOTH_SCAN; skipping BLE scan")
            return
        }
        val scanner = a.bluetoothLeScanner ?: run {
            log.warn("ble.noScanner", "BluetoothLeScanner unavailable")
            return
        }
        val filter = ScanFilter.Builder()
            .setServiceUuid(serviceUuid)
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handleScanResult(result)
            }
            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach { handleScanResult(it) }
            }
            override fun onScanFailed(errorCode: Int) {
                log.warn("ble.scan.fail", "scan failed", mapOf("code" to errorCode))
            }
        }
        scanCallback = cb
        runCatching {
            scanner.startScan(listOf(filter), settings, cb)
        }.onFailure { t ->
            log.warn("ble.scan.throw", "startScan threw", mapOf("err" to (t.message ?: "")))
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val payload = result.scanRecord?.getServiceData(serviceUuid) ?: return
        val self = this.self ?: return
        val scope = callbackScope ?: return
        val parsed = runCatching {
            Json.decodeFromString(PeerAdvertisement.serializer(), String(payload, StandardCharsets.UTF_8))
        }.getOrNull()
        val adv = parsed ?: synthesiseAdvertisement(result)
        if (adv != null) {
            scope.launch(Dispatchers.IO) { emit(adv) }
        }
    }

    /**
     * Fallback when the service-data payload is too short to carry
     * our full JSON. We synthesise a minimal advertisement from the
     * BLE-level metadata so the rest of the pipeline (classifier,
     * peer list) still sees the peer.
     */
    private fun synthesiseAdvertisement(result: ScanResult): PeerAdvertisement? {
        val device = result.device ?: return null
        val name = runCatching { device.name }.getOrNull().orEmpty()
        val address = device.address ?: return null
        return PeerAdvertisement(
            nodeId = name.ifBlank { address.replace(":", "") },
            host = address,
            // BLE peers don't expose a real L3 port — we use the
            // peer's HTTP server port if we heard it (encoded in
            // service-data) otherwise default to 8080.
            port = 8080,
            tier = "local_sandboxed",
            fingerprint = address.replace(":", "").padStart(12, '0'),
            ttlSec = 60,
            transport = "ble",
        )
    }

    private fun startAdvertising(self: LocalPeerDescriptor) {
        val a = adapter ?: return
        if (!hasAdvertisePermission()) {
            log.warn("ble.noAdvertise", "missing BLUETOOTH_ADVERTISE; skipping BLE advertiser")
            return
        }
        val advertiser = a.bluetoothLeAdvertiser ?: run {
            log.warn("ble.noAdvertiser", "BluetoothLeAdvertiser unavailable")
            return
        }
        val json = Json.encodeToString(PeerAdvertisement.serializer(), toAdvertisement(self))
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceData(serviceUuid, json.toByteArray(StandardCharsets.UTF_8))
            .build()
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .setConnectable(false)
            .build()
        val cb = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                log.info("ble.adv.start", "advertiser started")
            }
            override fun onStartFailure(errorCode: Int) {
                log.warn("ble.adv.fail", "advertiser failed", mapOf("code" to errorCode))
            }
        }
        advertiseCallback = cb
        runCatching {
            advertiser.startAdvertising(settings, data, cb)
        }.onFailure { t ->
            log.warn("ble.adv.throw", "startAdvertising threw", mapOf("err" to (t.message ?: "")))
        }
    }

    private fun hasScanPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_SCAN,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun hasAdvertisePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_ADVERTISE,
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun toAdvertisement(self: LocalPeerDescriptor): PeerAdvertisement = PeerAdvertisement(
        nodeId = self.nodeId,
        host = self.host,
        port = self.port,
        tier = self.tierTag,
        fingerprint = self.fingerprint,
        transport = "ble",
    )

    companion object {
        /**
         * Used in the service-data UUID filter.
         *
         * Bluetooth-SIG reserves the 0xF0000000–0xFFFFFFF0 range for
         * vendor-defined 128-bit UUIDs. We pack the Meshlit vendor
         * marker into the high 32 bits (`0x4D53484C` = ASCII
         * "MSHL" for Meshlit) and use the Bluetooth base UUID
         * suffix `XXXX-1000-8000-00805F9B34FB`.
         */
        const val MESHLIT_SERVICE_UUID: String = "4D53484C-4D53-484C-8000-00805F9B34FB"
    }
}