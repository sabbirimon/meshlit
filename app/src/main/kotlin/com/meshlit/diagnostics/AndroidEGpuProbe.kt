package com.meshlit.diagnostics

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import com.meshlit.core.common.EGpuConnection
import com.meshlit.core.common.EGpuDatabaseSnapshot
import com.meshlit.core.common.EGpuKind
import com.meshlit.core.common.EGpuMatcher
import com.meshlit.core.common.EGpuTransport
import com.meshlit.core.common.EGpuDriverAvailability
import com.meshlit.core.common.EGpuBackend
import com.meshlit.core.common.logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Probes for external GPUs attached to the phone.
 *
 * Three signal sources:
 *  1. USB host-mode device list — covers TB3/TB4/USB4 enclosures
 *     (Razer Core, ASUS ROG XG Mobile, GPD G1, OneXGPU, Minisforum
 *     DEG1, generic TB3 boxes).
 *  2. Filesystem probes — `/dev/kfd` (AMD ROCm), `/dev/nvidia*`
 *     (NVIDIA), `/dev/dri/renderD128` (Intel/AMD GPU node). These
 *     only succeed on rooted devices or peer machines exposing the
 *     filesystem over the LAN/WAN tunnel; we record the result
 *     either way.
 *  3. EGL renderer string — falls through to the matcher when the
 *     USB VID/PID lookup doesn't match (Chinese enclosures often
 *     show up with a generic vendor ID).
 *
 * Detached peer GPUs (a desktop with a discrete GPU that the phone
 * talks to over Tailscale) are reported separately and merged into
 * a single [EGpuConnection] with `isRemote = true`.
 *
 * Like other probes, runs on `Dispatchers.IO` because the USB
 * enumeration can block ~50 ms on cold start.
 */
class AndroidEGpuProbe(
    private val application: Application,
    private val snapshot: EGpuDatabaseSnapshot? = null,
) {

    private val log = logger("AndroidEGpuProbe")
    private val matcher = EGpuMatcher(snapshot)

    suspend fun probe(): List<EGpuConnection> = withContext(Dispatchers.IO) {
        val results = mutableListOf<EGpuConnection>()

        // 1. USB host-mode enumeration.
        probeUsbEnclosures()?.let { results += it }

        // 2. Filesystem probes — best-effort, never fatal.
        probeKfd()?.let { results += it }
        probeNvidiaDev()?.let { results += it }
        probeDriRenderNode()?.let { results += it }

        // 3. Remote peer GPU: a desktop on the Tailscale/WG tunnel
        //    can advertise itself as a BRAIN with a discrete GPU.
        //    The actual peer probe lives in core-tunnel; we leave a
        //    hook here so the orchestration layer can wire it in.
        //    See AndroidTunnelPeerProbe (Phase 4).

        log.info("egpu_probe.ok", "found ${results.size} eGPU(s)", mapOf(
            "count" to results.size,
            "kinds" to results.joinToString(",") { it.kind.tag },
        ))
        results
    }

    // --- USB host-mode enclosure scan ---------------------------------------

    private fun probeUsbEnclosures(): EGpuConnection? {
        val usb = application.getSystemService(Context.USB_SERVICE) as? UsbManager ?: return null
        for (device in usb.deviceList.values) {
            val name = device.productName ?: device.manufacturerName ?: "USB eGPU"
            val connection = matcher.resolve(
                vendorId = device.vendorId,
                productId = device.productId,
                productName = name,
                glRenderer = null,           // filled in by a separate EGL probe
                driVendor = null,
            ) ?: continue
            // Local USB enclosures are always LOCAL_TRUSTED.
            return connection.copy(
                displayName = name + " (" + connection.kind.displayName + ")",
                isRemote = false,
            )
        }
        return null
    }

    // --- AMD ROCm KFD node (rooted only, but we probe anyway) --------------

    private fun probeKfd(): EGpuConnection? {
        return try {
            if (!File("/dev/kfd").exists()) return null
            EGpuConnection(
                kind = EGpuKind.DISCRETE_AMD_RADEON,
                displayName = "AMD GPU (/dev/kfd present)",
                transport = EGpuTransport.USB4,
                driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                preferredBackend = EGpuBackend.VULKAN,
            )
        } catch (_: Throwable) { null }
    }

    // --- NVIDIA device nodes ----------------------------------------------

    private fun probeNvidiaDev(): EGpuConnection? {
        return try {
            val dev = File("/dev")
            val nvidiaNodes = dev.listFiles()?.filter { it.name.startsWith("nvidia") } ?: emptyList()
            if (nvidiaNodes.isEmpty()) return null
            // Could be a Tesla/A100 server, a GeForce eGPU, or a Quadro.
            EGpuConnection(
                kind = EGpuKind.DISCRETE_NVIDIA_GEFORCE,
                displayName = "NVIDIA GPU (${nvidiaNodes.size} /dev nodes)",
                transport = EGpuTransport.OTHER,
                driverStatus = EGpuDriverAvailability.THROUGH_ZLUDA_OR_REMOTE,
                preferredBackend = EGpuBackend.CUDA_OR_VULKAN,
            )
        } catch (_: Throwable) { null }
    }

    // --- Intel / AMD DRM render node ---------------------------------------

    private fun probeDriRenderNode(): EGpuConnection? {
        return try {
            val dri = File("/dev/dri")
            if (!dri.isDirectory) return null
            val renderNodes = dri.listFiles()?.filter { it.name.startsWith("renderD") } ?: emptyList()
            if (renderNodes.isEmpty()) return null
            EGpuConnection(
                kind = EGpuKind.DISCRETE_INTEL_ARC,
                displayName = "DRM render node (${renderNodes.size} devices)",
                transport = EGpuTransport.OTHER,
                driverStatus = EGpuDriverAvailability.OPEN_SOURCE,
                preferredBackend = EGpuBackend.VULKAN,
            )
        } catch (_: Throwable) { null }
    }

    // --- Remote peer GPU ----------------------------------------------------

    /**
     * Resolve a peer node that has advertised itself as having a
     * discrete GPU on the Tailscale/WG tunnel. The probe returns
     * the peer address so callers can dispatch inference there.
     */
    fun remotePeerGpu(
        peerDisplayName: String,
        peerAddress: String,
        rendererString: String?,
        vendorFromDri: String?,
    ): EGpuConnection? {
        return matcher.resolve(
            vendorId = null,
            productId = null,
            productName = peerDisplayName,
            glRenderer = rendererString,
            driVendor = vendorFromDri,
        )?.copy(
            isRemote = true,
            remoteAddress = peerAddress,
        )
    }
}
