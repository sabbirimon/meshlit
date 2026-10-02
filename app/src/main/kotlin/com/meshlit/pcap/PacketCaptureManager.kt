package com.meshlit.pcap

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.meshlit.core.net.capture.MdnsCaptureListener
import com.meshlit.core.net.capture.MdnsPcapRecorder
import com.meshlit.core.net.capture.MdnsPacketRecord
import com.meshlit.core.net.capture.MulticastLockFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * Owner of the mDNS capture pipeline on the v2 Scan screen.
 *
 * Coordinates:
 *  - [MdnsCaptureListener] — a `MulticastSocket(5353)` listener that
 *    decodes Meshlit TXT records and publishes to [live].
 *  - [MdnsPcapRecorder] — wraps `PcapWriter` to write the captured
 *    IP frames to a `.pcap` file in `cacheDir/exports/captures/`.
 *
 * The recorder is only active between [startCapture] and [stopAndExport].
 * Outside that window, decoded packets still flow through [live] so
 * the live packet stream works without recording.
 *
 * Lifecycle ownership: this class is a Koin singleton in
 * `coreModule`. The Scan screen drives it via the public
 * `startCapture()` / `stopAndExport()` / `discard()` methods — it
 * never starts automatically.
 */
class PacketCaptureManager(
    private val context: Context,
    private val listener: MdnsCaptureListener = MdnsCaptureListener(
        multicastLockFactory = AndroidMulticastLockFactory(context),
    ),
    private val scope: CoroutineScope = CoroutineScope(
        SupervisorJob() + Dispatchers.IO,
    ),
) {
    private val _state = MutableStateFlow<RecorderState>(RecorderState.Idle)
    val state: StateFlow<RecorderState> = _state.asStateFlow()

    /** Live stream of decoded mDNS packets. Always emits when the
     *  listener is running, regardless of recorder state. */
    val live: SharedFlow<MdnsPacketRecord> = listener.records

    private var recorder: MdnsPcapRecorder? = null
    private var currentFile: File? = null
    private var collectorJob: Job? = null

    /**
     * Begin capture. Starts the listener if it isn't already running
     * and opens a fresh `.pcap` file in `cacheDir/exports/captures/`.
     * No-op if the manager is already recording.
     */
    fun startCapture(): Boolean {
        if (_state.value is RecorderState.Recording) return false
        if (listener.start() == null) {
            _state.value = RecorderState.Failed("Failed to bind mDNS socket")
            return false
        }
        val dir = File(context.cacheDir, "exports/captures").apply { mkdirs() }
        val file = File(dir, "meshlit-${System.currentTimeMillis()}.pcap")
        val rec = MdnsPcapRecorder(file)
        rec.start()
        recorder = rec
        currentFile = file
        _state.value = RecorderState.Recording(
            startedAtMs = System.currentTimeMillis(),
            packetCount = 0,
        )
        // Bridge the listener's flow to the recorder (and to the
        // packet-count counter). One collector per recording session.
        collectorJob?.cancel()
        collectorJob = scope.launch {
            listener.records.collect { rec ->
                val frame = com.meshlit.core.net.capture.MdnsCaptureUtils.wrapAsIpv4Udp5353(
                    dnsPayload = rec.rawDnsPayload,
                    srcIp = rec.sourceIp,
                )
                recorder?.record(rec.timestampMs, frame)
                val prev = _state.value
                if (prev is RecorderState.Recording) {
                    _state.value = prev.copy(packetCount = prev.packetCount + 1)
                }
            }
        }
        return true
    }

    /**
     * Stop capture, close the recorder, and return the file that was
     * written. Caller is expected to either share the file via
     * [android.content.Intent.ACTION_SEND] + FileProvider, or call
     * [discard] to delete it.
     */
    fun stopAndExport(): File? {
        val cur = currentFile ?: return null
        recorder?.close()
        recorder = null
        collectorJob?.cancel()
        collectorJob = null
        val size = cur.length()
        val count = when (val s = _state.value) {
            is RecorderState.Recording -> s.packetCount
            else -> 0
        }
        _state.value = RecorderState.Ready(
            file = cur,
            sizeBytes = size,
            packetCount = count,
        )
        return cur
    }

    /**
     * Discard the last capture (delete the file + reset to Idle).
     * Idempotent.
     */
    fun discard() {
        val cur = currentFile
        if (cur != null && cur.exists()) cur.delete()
        currentFile = null
        recorder = null
        collectorJob?.cancel()
        collectorJob = null
        _state.value = RecorderState.Idle
    }

    /** Tear down the listener + any in-flight recorder. */
    fun shutdown() {
        recorder?.close()
        recorder = null
        collectorJob?.cancel()
        collectorJob = null
        listener.stop()
        currentFile = null
        _state.value = RecorderState.Idle
    }
}

/**
 * UI-facing state model for the capture row. The Scan screen calls
 * `startCapture()` / `stopAndExport()` / `discard()` on the manager
 * and collects this state.
 */
sealed interface RecorderState {
    /** No file in flight. The Start chip is visible. */
    data object Idle : RecorderState

    /** Capture in progress. The Stop chip is visible. */
    data class Recording(
        val startedAtMs: Long,
        val packetCount: Int,
    ) : RecorderState

    /** Capture stopped but the file still lives on disk. The Share
     *  + Discard chips are visible. */
    data class Ready(
        val file: File,
        val sizeBytes: Long,
        val packetCount: Int,
    ) : RecorderState

    /** Capture failed. The Start chip reappears with a reason. */
    data class Failed(val reason: String) : RecorderState
}

/**
 * Acquires a real `WifiManager.MulticastLock` so the OS delivers
 * multicast UDP/5353 frames to our socket. Without this, Android
 * filters mDNS multicast when the Wi-Fi link is in power-save mode
 * — the listener would receive nothing. The lock is released when
 * the returned [AutoCloseable] is closed (i.e. on listener stop).
 */
private class AndroidMulticastLockFactory(
    private val context: Context,
) : MulticastLockFactory {
    override fun acquire(): AutoCloseable? {
        val wm = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as? WifiManager ?: return null
        @Suppress("DEPRECATION")
        val lock = wm.createMulticastLock("meshlit-mdns-capture")
        return try {
            lock.setReferenceCounted(false)
            lock.acquire()
            Log.i(TAG, "MulticastLock acquired")
            AutoCloseable {
                try {
                    lock.release()
                    Log.i(TAG, "MulticastLock released")
                } catch (e: Throwable) {
                    Log.w(TAG, "MulticastLock release failed: ${e.message}")
                }
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "MulticastLock denied (missing CHANGE_WIFI_MULTICAST_STATE?)")
            null
        } catch (e: Throwable) {
            Log.w(TAG, "MulticastLock acquire failed: ${e.message}")
            null
        }
    }

    companion object {
        private const val TAG = "PacketCapture"
    }
}
