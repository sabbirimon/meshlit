package com.meshlit.diagnostics

import android.app.ActivityManager
import android.app.Application
import android.bluetooth.BluetoothManager
import android.content.Context
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.StatFs
import com.meshlit.core.common.DetectedDeviceInfo
import com.meshlit.core.common.GpuFamily
import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.common.PeripheralDevice
import com.meshlit.core.common.PeripheralKind
import com.meshlit.core.common.PeripheralTransport
import com.meshlit.core.common.SocFamily
import com.meshlit.core.common.SystemProbe
import com.meshlit.core.common.inferGpuFamily
import com.meshlit.core.common.inferNpuPresence
import com.meshlit.core.common.inferSocFamily
import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android-side implementation of [SystemProbe]. Lives in `:app` because
 * it depends on `android.os.Build`, `ActivityManager`, `StatFs`,
 * `UsbManager`, and `BluetoothManager`.
 *
 * All heavy reads run on `Dispatchers.IO`. The probe is called from
 * the UI thread at first launch, and we don't want to block startup
 * on EGL display queries (those go through a separate, opt-in
 * capability probe in Phase 2).
 */
class AndroidSystemProbe(private val application: Application) : SystemProbe {

    private val log = logger("AndroidSystemProbe")

    override suspend fun detect(): MeshlitResult<DetectedDeviceInfo> = withContext(Dispatchers.IO) {
        try {
            val info = collect()
            log.info("system_probe.ok", "detected device", mapOf(
                "manufacturer" to info.manufacturer,
                "model" to info.model,
                "primaryAbi" to info.primaryAbi,
                "socFamily" to info.socFamily.tag,
                "totalRamMb" to info.totalRamMb,
                "abis" to info.abis.joinToString(","),
                "hasNpu" to info.hasNpu,
            ))
            MeshlitResult.Success(info)
        } catch (t: Throwable) {
            log.error("system_probe.fail", "probe failed", t)
            MeshlitResult.Failure(MeshlitError.Unknown(t))
        }
    }

    private fun collect(): DetectedDeviceInfo {
        val abis = Build.SUPPORTED_ABIS?.toList() ?: listOf(Build.CPU_ABI)
        val primaryAbi = abis.firstOrNull() ?: Build.UNKNOWN
        val socFamily = inferSocFamily(
            hardware = Build.HARDWARE.orEmpty(),
            socManufacturer = readSocManufacturer(),
            model = Build.MODEL.orEmpty(),
        )
        val (hasNpu, npuName) = inferNpuPresence(socFamily)
        val gpuFamily = inferGpuFamilyFromSoc(socFamily, Build.HARDWARE.orEmpty())
        val (totalRamMb, availRamMb) = readRam()
        val (totalStorageMb, availStorageMb) = readStorage()
        val (cpuCores, cpuMaxFreqKHz) = readCpu()

        return DetectedDeviceInfo(
            manufacturer = Build.MANUFACTURER ?: Build.UNKNOWN,
            brand = Build.BRAND ?: Build.UNKNOWN,
            model = Build.MODEL ?: Build.UNKNOWN,
            device = Build.DEVICE ?: Build.UNKNOWN,
            product = Build.PRODUCT ?: Build.UNKNOWN,
            hardware = Build.HARDWARE ?: Build.UNKNOWN,
            board = Build.BOARD ?: Build.UNKNOWN,
            abis = abis,
            primaryAbi = primaryAbi,
            socFamily = socFamily,
            socModel = Build.SOC_MODEL,
            gpuFamily = gpuFamily,
            hasNpu = hasNpu,
            npuName = npuName,
            totalRamMb = totalRamMb,
            availableRamMb = availRamMb,
            totalStorageMb = totalStorageMb,
            availableStorageMb = availStorageMb,
            cpuCoreCount = cpuCores,
            cpuMaxFreqKHz = cpuMaxFreqKHz,
            androidVersion = Build.VERSION.RELEASE ?: Build.UNKNOWN,
            androidSdkInt = Build.VERSION.SDK_INT,
            securityPatch = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Build.VERSION.SECURITY_PATCH
            } else null,
            buildFingerprint = Build.FINGERPRINT ?: Build.UNKNOWN,
            buildType = Build.TYPE ?: Build.UNKNOWN,
        )
    }

    private fun readSocManufacturer(): String? {
        // Build.SOC_MANUFACTURER landed in API 31. Older devices return null,
        // so infer from HARDWARE prefix as a fallback.
        val sm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Build.SOC_MANUFACTURER
        } else null
        if (sm != null) return sm
        val hw = Build.HARDWARE.orEmpty().lowercase()
        return when {
            hw.startsWith("qcom") || hw.startsWith("msm") || hw.startsWith("sdm") -> "qcom"
            hw.startsWith("mt") -> "mediatek"
            hw.startsWith("exynos") -> "samsung"
            hw.startsWith("gs") -> "google"
            hw.startsWith("hi") -> "hi"
            hw.startsWith("ums") || hw.startsWith("sp") -> "unisoc"
            hw.startsWith("intel") -> "intel"
            else -> null
        }
    }

    /**
     * Infer GPU family from the SoC. The EGL/GL query is correct but
     * adds 50–300 ms of startup latency, which the first-run flow
     * can't afford. The pattern table covers most real devices; users
     * can override from Settings if we got it wrong. Phase 2 adds an
     * opt-in "deep probe" that goes through EGL and confirms the GPU.
     */
    private fun inferGpuFamilyFromSoc(soc: SocFamily, hardware: String): GpuFamily {
        val hw = hardware.lowercase()
        // Cheap EGL renderer probe on platforms that support it.
        val rendererStr = tryQueryGlRenderer()
        if (rendererStr.isNotBlank()) return inferGpuFamily(rendererStr)
        // Fall back to SoC heuristic.
        return when (soc) {
            SocFamily.SNAPDRAGON_8, SocFamily.SNAPDRAGON_7,
            SocFamily.SNAPDRAGON_6, SocFamily.SNAPDRAGON_4,
            SocFamily.SNAPDRAGON_OLDER -> GpuFamily.ADRENO
            SocFamily.DIMENSITY_9000 -> GpuFamily.IMMORTALIS
            SocFamily.DIMENSITY_8000, SocFamily.DIMENSITY_7000,
            SocFamily.DIMENSITY_6000, SocFamily.DIMENSITY_1000 -> GpuFamily.MALI
            SocFamily.HELIO_G99, SocFamily.HELIO_OLDER -> GpuFamily.MALI
            SocFamily.EXYNYS_2400 -> GpuFamily.XCLIPSE        // Xclipse = AMD RDNA2
            SocFamily.EXYNOS_2200 -> GpuFamily.XCLIPSE        // Xclipse = AMD RDNA2
            SocFamily.EXYNOS_2100, SocFamily.EXYNOS_1400,
            SocFamily.EXYNOS_OLDER -> GpuFamily.MALI
            SocFamily.TENSOR_G5, SocFamily.TENSOR_G4,
            SocFamily.TENSOR_G3, SocFamily.TENSOR_G2,
            SocFamily.TENSOR_G1 -> GpuFamily.MALI
            SocFamily.KIRIN_9000, SocFamily.KIRIN_8000,
            SocFamily.KIRIN_OLDER -> GpuFamily.MALI
            SocFamily.UNISOC_TIGER, SocFamily.UNISOC_OLDER -> GpuFamily.IMG
            SocFamily.X86_INTEL -> GpuFamily.POWERVR
            SocFamily.X86_AMD -> GpuFamily.ADRENO              // unlikely but defined
            SocFamily.RISCV, SocFamily.OTHER -> GpuFamily.UNKNOWN
        }
    }

    /**
     * Best-effort GL renderer string query. Catches every failure
     * and returns empty — the caller's pattern table picks up the slack.
     */
    private fun tryQueryGlRenderer(): String {
        return try {
            val eglClass = Class.forName("android.opengl.GLES20")
            // Just use static methods to read renderer via the GL pipeline —
            // for a first-run probe we accept the heuristic fallback if
            // this throws. We don't construct an EGL context: that's a
            // ~200ms hit for a value the SoC table already has.
            val rendererMethod = eglClass.getMethod("glGetString", Int::class.java)
            // Actually calling GLES20 requires a current context. If we
            // had one, we'd invoke glGetString(GL_RENDERER). Without one,
            // we return empty and rely on the heuristic.
            ""
        } catch (_: Throwable) {
            ""
        }
    }

    private fun readRam(): Pair<Long, Long> {
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mi)
        val totalMb = mi.totalMem / 1024 / 1024
        val availMb = mi.availMem / 1024 / 1024
        return totalMb to availMb
    }

    private fun readStorage(): Pair<Long, Long> {
        return try {
            val path = application.filesDir.absolutePath
            val stat = StatFs(path)
            val totalMb = stat.blockCountLong * stat.blockSizeLong / 1024 / 1024
            val availMb = stat.availableBlocksLong * stat.blockSizeLong / 1024 / 1024
            totalMb to availMb
        } catch (_: Throwable) {
            0L to 0L
        }
    }

    private fun readCpu(): Pair<Int, Long> {
        val cores = Runtime.getRuntime().availableProcessors()
        val maxFreqKHz = try {
            java.io.File("/sys/devices/system/cpu/cpu0/cpufreq/cpuinfo_max_freq")
                .readText().trim().toLongOrNull() ?: 0L
        } catch (_: Throwable) { 0L }
        return cores to maxFreqKHz
    }
}

/**
 * Lightweight USB / Bluetooth peripheral probe. Separate from the
 * main system probe because it costs more I/O and the user only sees
 * the result on the Device screen, not at first launch.
 */
class AndroidPeripheralProbe(private val application: Application) {

    private val log = logger("AndroidPeripheralProbe")

    fun probeUsb(): List<PeripheralDevice> {
        val usb = application.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return emptyList()
        val devices = usb.deviceList.values
        return devices.map { d ->
            val vid = d.vendorId
            val pid = d.productId
            val name = d.productName ?: d.manufacturerName
            val kind = classifyUsb(vid, pid, name)
            PeripheralDevice(
                kind = kind,
                name = name,
                transport = PeripheralTransport.USB,
                vendorId = vid,
                productId = pid,
            )
        }
    }

    private fun classifyUsb(vid: Int, pid: Int, name: String?): PeripheralKind {
        val n = name?.lowercase().orEmpty()
        // eGPU enclosures (Razer Core, ASUS ROG XG Mobile, GPD G1,
        // OneXGPU, Minisforum DEG1). The eGPU probe reads back for
        // vendor-specifics; this is the coarse PeripheralKind.
        if (vid == 0x1de7 /* Razer */ ||
            vid == 0x0b05 /* ASUS */ && pid in listOf(0x18e5, 0x18e7, 0x18e9, 0x18ec) ||
            vid == 0x2e95 /* GPD */ ||
            vid == 0x1bcf /* One-Netbook / OneXGPU */ ||
            vid == 0x1e5f /* Minisforum */ ||
            n.contains("egpu") || n.contains("rog xg")) {
            return PeripheralKind.USB_EGPU
        }
        return when {
            n.contains("hub") -> PeripheralKind.USB_HUB
            n.contains("mass storage") || n.contains("ssd") || n.contains("hdd") -> PeripheralKind.USB_STORAGE
            n.contains("ethernet") || n.contains("rtl815") || n.contains("asix") -> PeripheralKind.USB_NETWORK
            n.contains("keyboard") || n.contains("hid") -> PeripheralKind.USB_KEYBOARD
            n.contains("mouse") -> PeripheralKind.USB_MOUSE
            n.contains("audio") || n.contains("snd") -> PeripheralKind.USB_AUDIO
            n.contains("midi") -> PeripheralKind.USB_MIDI
            else -> PeripheralKind.OTHER
        }
    }

    fun probeBluetooth(): List<PeripheralDevice> {
        val bt = application.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return emptyList()
        val adapter = bt.adapter ?: return emptyList()
        return try {
            adapter.bondedDevices?.map { d ->
                val name = d.name
                val kind = when (d.bluetoothClass?.majorDeviceClass) {
                    0x500 /* PHONE */,
                    0x540 /* LAPTOP */ -> PeripheralKind.OTHER  // Likely a peer, not a peripheral
                    0x600 /* IMAGING */ -> PeripheralKind.OTHER
                    0x800 /* PERIPHERAL */ -> PeripheralKind.BLUETOOTH_HID
                    0x200 /* AUDIO_VIDEO */ -> PeripheralKind.BLUETOOTH_AUDIO
                    else -> PeripheralKind.OTHER
                }
                PeripheralDevice(
                    kind = kind,
                    name = name,
                    transport = PeripheralTransport.BLUETOOTH,
                )
            } ?: emptyList()
        } catch (t: SecurityException) {
            // Missing BLUETOOTH_CONNECT permission on Android 12+
            log.warn("bt.permission_denied", "cannot read paired devices")
            emptyList()
        }
    }
}