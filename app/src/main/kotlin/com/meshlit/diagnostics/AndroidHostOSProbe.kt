package com.meshlit.diagnostics

import android.os.Build
import com.meshlit.core.common.DesktopBackend
import com.meshlit.core.common.HostOS
import com.meshlit.core.common.HostOSDetection
import com.meshlit.core.common.logger
import java.io.File

/**
 * Probe that decides whether the Android runtime is hosted on Linux
 * x86_64 (emulator, container, or native install) versus stock
 * Android. The probe is read-only — it inspects [Build] fields and a
 * handful of /proc and /sys paths that exist on every rooted device
 * and every container / emulator.
 *
 * Detection sources (ranked by reliability):
 *  1. ro.kernel.qemu = 1 + abi = x86_64 → Android Studio Emulator
 *  2. ro.boot.hardware.platform starts with "Google_Cros..." → ChromeOS ARC
 *  3. ro.hardware = cheeseburger / brioche / kukui → ChromeOS board
 *  4. abi is x86 or x86_64 + qemu → Waydroid / Anbox / Genymotion / Bluestacks
 *  5. /sys/class/dmi/id/product_name contains "Bliss" / "Prime" /
 *     "Phoenix" → native Android-x86 install
 *  6. Package fingerprint of pre-installed system apps: "com.android.emu",
 *     "com.google.android.bliss", etc.
 *  7. Fallback: abi = arm64-v8a → stock Android
 *
 * Why we care: on x86_64 hosts inference runs 5–20x faster than on a
 * phone (no thermal throttle, AVX2 SIMD, lots of RAM), and the eGPU
 * backend landscape is much wider (CUDA, ROCm, oneAPI, not just Vulkan).
 */
class AndroidHostOSProbe {

    private val log = logger("HostOSProbe")

    /**
     * Run the probe. Safe to call from any thread — it's pure I/O on
     * a handful of small files.
     */
    fun probe(): HostOSDetection {
        val abi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val isQemu = readSysProp("ro.kernel.qemu") == "1"
        val isChromeOSArc = isChromeOSArcSystem()
        val product = readDmiProduct()
        val kernelVersion = readKernelVersion()
        val cpuModel = readCpuModel()
        val gpuRender = File("/dev/dri/renderD128").exists()
        val nvidiaSmi = commandExists("nvidia-smi")
        val rocmSmi = commandExists("rocm-smi") || commandExists("rocminfo")
        val kfd = File("/dev/kfd").exists()
        val oneApi = commandExists("sycl-ls") || commandExists("intel_gpu_top")

        val hostOS = resolveHostOS(abi, isQemu, isChromeOSArc, product)

        return HostOSDetection(
            hostOS = hostOS,
            abi = abi,
            isQemu = isQemu,
            isChromeOSArc = isChromeOSArc,
            kernelVersion = kernelVersion,
            hostProduct = product,
            hostCpuModel = cpuModel,
            hostGpuRenderNode = gpuRender,
            hostHasNvidiaSmi = nvidiaSmi,
            hostHasRocmSmi = rocmSmi,
            hostHasKfd = kfd,
            hostHasOneApi = oneApi,
        ).also {
            log.info(
                "hostos.detected",
                "Host OS probed",
                mapOf(
                    "host" to it.hostOS.tag,
                    "abi" to it.abi,
                    "kernel" to it.kernelVersion,
                    "qemu" to it.isQemu,
                    "arc" to it.isChromeOSArc,
                    "nvidia" to it.hostHasNvidiaSmi,
                    "rocm" to it.hostHasRocmSmi,
                    "kfd" to it.hostHasKfd,
                    "dri" to it.hostGpuRenderNode,
                ),
            )
        }
    }

    private fun resolveHostOS(
        abi: String,
        isQemu: Boolean,
        isChromeOSArc: Boolean,
        product: String?,
    ): HostOS {
        if (isChromeOSArc) return HostOS.CHROMEOS_ARC
        if (product?.contains("bliss", ignoreCase = true) == true) return HostOS.ANDROID_X86
        if (product?.contains("prime os", ignoreCase = true) == true) return HostOS.ANDROID_X86
        if (product?.contains("phoenix", ignoreCase = true) == true) return HostOS.ANDROID_X86
        return when {
            abi.startsWith("x86") && isQemu -> {
                // Differentiate Android Studio emulator from Waydroid:
                //  - Studio emulator has Build.FINGERPRINT = "google/sdk_gphone..."
                //  - Waydroid has Build.PRODUCT = "waydroid_x86_64" / "treble_x86_64..."
                val fingerprint = Build.FINGERPRINT.lowercase()
                val product2 = (Build.PRODUCT ?: "").lowercase()
                when {
                    fingerprint.contains("sdk_gphone") -> HostOS.ANDROID_EMULATOR
                    product2.contains("waydroid") -> HostOS.WAYDROID
                    product2.contains("anbox") -> HostOS.ANBOX
                    else -> HostOS.THIRD_PARTY_EMULATOR
                }
            }
            abi.startsWith("x86") -> HostOS.ANDROID_X86
            abi.startsWith("arm") -> HostOS.ANDROID
            else -> HostOS.UNKNOWN
        }
    }

    private fun isChromeOSArcSystem(): Boolean {
        // ChromeOS sets ro.boot.hardware.platform starting with "Google_Cros..."
        val platform = readSysProp("ro.boot.hardware.platform") ?: ""
        if (platform.startsWith("Google_Cros", ignoreCase = true)) return true
        // Some Crostini boards set ro.hardware to a board name like
        // "kukui" / "cheeseburger" / "brioche" — none of these are
        // real phones, so we treat any of them as ChromeOS.
        val hardware = Build.HARDWARE.lowercase()
        return hardware in chromeOsBoardNames
    }

    private fun readSysProp(key: String): String? = try {
        val process = ProcessBuilder("/system/bin/getprop", key).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText().trim().takeIf { s -> s.isNotEmpty() } }
    } catch (t: Throwable) {
        null
    }

    private fun readDmiProduct(): String? = try {
        File("/sys/class/dmi/id/product_name").takeIf { it.exists() }?.readText()?.trim()
    } catch (t: Throwable) {
        null
    }

    private fun readKernelVersion(): String = try {
        File("/proc/version").takeIf { it.exists() }?.readText()?.trim()?.substringBefore(' ')
            ?: System.getProperty("os.version") ?: "unknown"
    } catch (t: Throwable) {
        "unknown"
    }

    private fun readCpuModel(): String? = try {
        File("/proc/cpuinfo").takeIf { it.exists() }?.useLines { lines ->
            lines.firstOrNull { it.startsWith("model name", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
                ?: lines.firstOrNull { it.startsWith("Hardware", ignoreCase = true) }
                    ?.substringAfter(':')?.trim()
        }
    } catch (t: Throwable) {
        null
    }

    private fun commandExists(command: String): Boolean = try {
        ProcessBuilder("which", command).start().waitFor() == 0
    } catch (t: Throwable) {
        false
    }

    /** Convenience: turn the detection into a role-suggestion boost.
     *  x86 hosts default to BRAIN because their compute is desktop-class. */
    val HostOSDetection.brainEligible: Boolean
        get() = hostOS.defaultBrainEligible && abi.startsWith("x86")

    /** Convenience: recommend a desktop eGPU backend if available.
     *  Falls back to CPU-only if nothing is detected. */
    val HostOSDetection.recommendedDesktopBackend: DesktopBackend
        get() = preferredDesktopBackend ?: DesktopBackend.CPU

    private companion object {
        val chromeOsBoardNames = setOf(
            // Common Chromebooks that ship Crostini
            "kukui", "krane", "kodama", "kakadu", "hana", "hatch",
            "volta", "veyron", "trogdor", "brya", "brask",
            "auron", "cheeseburger", "brioche", "coral",
            "octopus", "nami", "fizz", "kalista", "lasilla",
            "rammus", "sarien", "asuka", "caroline", "cave",
            "rei", "tidus", "ultima", "lulu", "meowth",
            "elemi", "gaelin", "poppy", "dumo", "drobit",
        )
    }
}