package com.meshlit.pipeline
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
object LayerPairingQr {
    private const val PREFIX="meshlit-layer-v1:"
    /** A spoken visual cross-check only. The complete certificate pin and token authenticate transport. */
    fun identityCode(fingerprint:String):String {
        require(fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        return fingerprint.lowercase().take(12).chunked(4).joinToString("-")
    }
    fun encode(peer:PipelinePeer):String {
        validate(peer)
        return PREFIX+Json.encodeToString(peer.copy(weight=1,group="default"))
    }
    fun decode(raw:String):PipelinePeer {
        require(raw.length<=4096 && raw.startsWith(PREFIX)){"Use a Meshlit layer worker QR"}
        val peer=Json.decodeFromString<PipelinePeer>(raw.removePrefix(PREFIX))
        validate(peer)
        // Group assignment belongs to the coordinator, not the worker's QR.
        return peer.copy(weight=1,group="default")
    }
    private fun validate(peer:PipelinePeer) {
        require(peer.host.isNotBlank() && peer.host.length<=253 && peer.host.none{it.isWhitespace() || it in "/?#@"})
        require(peer.host !in setOf("localhost","127.0.0.1","0.0.0.0","::1")) { "Use an address reachable from another device" }
        require(peer.port in 1024..65535 && peer.fingerprint.matches(Regex("[a-fA-F0-9]{64}")))
        require(peer.token.length in 32..128 && peer.token.all{it.code in 33..126})
    }
}
