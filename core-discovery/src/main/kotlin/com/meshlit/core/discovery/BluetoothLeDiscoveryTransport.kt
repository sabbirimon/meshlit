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

/** BLE discovery with a 24-byte manufacturer frame that fits legacy advertisements. */
class BluetoothLeDiscoveryTransport(
    private val context: Context,
    private val serviceUuid: ParcelUuid = ParcelUuid.fromString(MESHLIT_SERVICE_UUID),
) : DiscoveryTransport(name = "ble") {

    private val log = logger("BluetoothLeDiscoveryTransport")
    fun refreshAvailability() {
        val unavailable: TransportStatus? = when {
            !hasScanPermission() -> TransportStatus.MissingPermission(if (Build.VERSION.SDK_INT >= 31) Manifest.permission.BLUETOOTH_SCAN else Manifest.permission.ACCESS_FINE_LOCATION)
            !hasConnectPermission() -> TransportStatus.MissingPermission(Manifest.permission.BLUETOOTH_CONNECT)
            adapter == null -> TransportStatus.Unavailable("Bluetooth hardware unavailable")
            runCatching { adapter?.isEnabled }.getOrDefault(false) != true -> TransportStatus.Unavailable("Bluetooth is off")
            else -> null
        }
        if (unavailable != null) {
            stop()
            setStatus(unavailable)
        } else if (status.value is TransportStatus.Unavailable || status.value is TransportStatus.MissingPermission) setStatus(TransportStatus.Stopped)
    }

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
        refreshAvailability()
        if (status.value is TransportStatus.Unavailable || status.value is TransportStatus.MissingPermission) return scope.launch { }
        setStatus(TransportStatus.Starting)
        isRunning = true
        this.self = self
        this.callbackScope = scope
        return scope.launch(Dispatchers.IO) {
            startScanning()
            startAdvertising(self)
        }
    }

    override fun stop() {
        setStatus(TransportStatus.Stopped)
        if (!isRunning) return
        isRunning = false
        callbackScope = null
        val a = if (hasConnectPermission()) adapter else null
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
        setStatus(TransportStatus.Stopped)
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
            .setManufacturerData(BlePeerPacket.MANUFACTURER_ID, byteArrayOf(0x4D, 1))
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
                setStatus(TransportStatus.Error(errorCode, "BLE scan failed"))
                log.warn("ble.scan.fail", "scan failed", mapOf("code" to errorCode))
            }
        }
        scanCallback = cb
        runCatching {
            scanner.startScan(listOf(filter), settings, cb)
            setStatus(TransportStatus.Active)
        }.onFailure { t ->
            setStatus(TransportStatus.Error(-1, "BLE scan failed: ${t.message}"))
            log.warn("ble.scan.throw", "startScan threw", mapOf("err" to (t.message ?: "")))
        }
    }

    private fun handleScanResult(result: ScanResult) {
        val payload = result.scanRecord?.getManufacturerSpecificData(BlePeerPacket.MANUFACTURER_ID) ?: return
        val scope = callbackScope ?: return
        val adv = BlePeerPacket.decode(payload) ?: return
        if (adv.nodeId != self?.nodeId) scope.launch(Dispatchers.IO) { emit(adv) }
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
        val payload = runCatching { BlePeerPacket.encode(self) }.getOrElse {
            setStatus(TransportStatus.Unavailable("Local IPv4 address required for BLE advertisement"))
            return
        }
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addManufacturerData(BlePeerPacket.MANUFACTURER_ID, payload)
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
                setStatus(TransportStatus.Error(errorCode, "BLE advertisement failed"))
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
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    private fun hasConnectPermission(): Boolean = Build.VERSION.SDK_INT < 31 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED

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
