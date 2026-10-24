package com.meshlit.ui.v2.screens
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.meshlit.devices.PairingPayload
import com.meshlit.peering.NfcPairingSession

private fun Context.activity(): Activity? = when (this) { is Activity -> this; is ContextWrapper -> baseContext.activity(); else -> null }
@Composable
internal fun NfcPairingControls(own: PairingPayload?, onPeer: (PairingPayload) -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val activity = context.activity()
    val adapter = remember(context) { NfcAdapter.getDefaultAdapter(context) }
    val owner = LocalLifecycleOwner.current
    var mode by remember { mutableStateOf("off") }
    val receive by rememberUpdatedState(onPeer); val fail by rememberUpdatedState(onError)
    DisposableEffect(activity, adapter, mode, own, owner) {
        fun start() {
            if (activity == null || adapter == null || !adapter.isEnabled) return
            if (mode == "share" && own != null) NfcPairingSession.payload.set(own.encode().toByteArray(Charsets.UTF_8))
            if (mode == "read") adapter.enableReaderMode(activity, { tag ->
                try {
                    val iso = IsoDep.get(tag) ?: error("Peer does not support NFC card emulation")
                    iso.connect()
                    try {
                        iso.timeout = 3000
                        val aid = com.meshlit.core.trust.PeerCrypto.bytes(NfcPairingSession.AID)
                        val selected = iso.transceive(byteArrayOf(0, 0xA4.toByte(), 4, 0, aid.size.toByte()) + aid)
                        require(selected.size == 4 && selected.takeLast(2) == listOf(0x90.toByte(), 0.toByte())) { "Open Share NFC on the other device" }
                        val length = ((selected[0].toInt() and 255) shl 8) or (selected[1].toInt() and 255)
                        require(length in 1..8192) { "Invalid NFC payload length" }
                        val output = java.io.ByteArrayOutputStream()
                        while (output.size() < length) {
                            val offset = output.size(); val count = minOf(240, length - offset)
                            val reply = iso.transceive(byteArrayOf(0, 0xB0.toByte(), (offset shr 8).toByte(), offset.toByte(), count.toByte()))
                            require(reply.size == count + 2 && reply.takeLast(2) == listOf(0x90.toByte(), 0.toByte())) { "Incomplete NFC payload" }
                            output.write(reply, 0, count)
                        }
                        val payload = PairingPayload.decode(output.toString("UTF-8"))
                        activity.runOnUiThread { receive(payload); mode = "off" }
                    } finally { iso.close() }
                } catch (e: Exception) { activity.runOnUiThread { fail("nfc.pairing_failed: ${e.message}"); mode = "off" } }
            }, NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, null)
        }
        fun stop() { NfcPairingSession.payload.set(null); if (activity != null && adapter != null) runCatching { adapter.disableReaderMode(activity) } }
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_RESUME) start() else if (event == Lifecycle.Event.ON_PAUSE) stop() }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) start()
        onDispose { stop(); owner.lifecycle.removeObserver(observer) }
    }
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(mode == "share", { mode = if (mode == "share") "off" else "share" },
                { Text("Share NFC") }, enabled = adapter != null && own != null)
            FilterChip(mode == "read", { mode = if (mode == "read") "off" else "read" },
                { Text("Read NFC") }, enabled = adapter != null)
        }
        Text(when { adapter == null -> "NFC unavailable on this device"
            mode == "share" -> "Keep this screen open. Tap a device with Read NFC selected."
            mode == "read" -> "Tap a device with Share NFC selected."
            else -> "NFC pairing uses card emulation where supported." }, style = MaterialTheme.typography.bodySmall)
    }
}
