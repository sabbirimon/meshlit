package com.meshlit.peering

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.util.concurrent.atomic.AtomicReference

/** Sharing is active only while the user explicitly selects NFC sharing in Devices. */
object NfcPairingSession {
    const val AID = "F04D4553484C4954"
    val payload = AtomicReference<ByteArray?>(null)
}
class NfcPairingService : HostApduService() {
    private var selected = false
    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val data = NfcPairingSession.payload.get() ?: return byteArrayOf(0x69, 0x85.toByte())
        val cmd = commandApdu ?: return byteArrayOf(0x6F, 0x00)
        if (cmd.size == 13 && cmd[0] == 0.toByte() && cmd[1] == 0xA4.toByte() &&
            cmd.copyOfRange(5, 13).contentEquals(com.meshlit.core.trust.PeerCrypto.bytes(NfcPairingSession.AID))) {
            selected = true
            return byteArrayOf((data.size shr 8).toByte(), data.size.toByte(), 0x90.toByte(), 0x00)
        }
        if (selected && cmd.size == 5 && cmd[0] == 0.toByte() && cmd[1] == 0xB0.toByte()) {
            val offset = ((cmd[2].toInt() and 255) shl 8) or (cmd[3].toInt() and 255)
            val count = cmd[4].toInt() and 255
            if (count in 1..240 && offset in 0 until data.size) return data.copyOfRange(offset, minOf(offset + count, data.size)) + byteArrayOf(0x90.toByte(), 0x00)
        }
        return byteArrayOf(0x6D, 0x00)
    }
    override fun onDeactivated(reason: Int) { selected = false }
}
