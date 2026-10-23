package com.meshlit.core.gibberlink

/** Short printable frames for the existing 96-byte ggwave channel.
 * Discovery proves only that another modem decoded a fresh challenge. */
object AcousticControlPacket {
    private const val HELLO = "ML2HELLO:"
    private const val HERE = "ML2HERE:"
    private const val COMMAND = "ML2CMD:"
    private val noncePattern = Regex("[A-F0-9]{12}")

    fun hello(nonce: String): String { require(noncePattern.matches(nonce)); return HELLO + nonce }
    fun reply(nonce: String): String { require(noncePattern.matches(nonce)); return HERE + nonce }
    fun helloNonce(packet: String): String? = packet.removePrefix(HELLO).takeIf { packet.startsWith(HELLO) && noncePattern.matches(it) }
    fun matchesReply(packet: String, nonce: String): Boolean = packet == reply(nonce)
    fun commandRequest(command: String): String {
        require(command.length in 1..(96 - COMMAND.length) && command.all { it.code in 32..126 })
        return COMMAND + command
    }
    fun receivedCommand(packet: String): String? = packet.removePrefix(COMMAND).takeIf { packet.startsWith(COMMAND) && packet.length <= 96 &&
        it.isNotBlank() && it.all { char -> char.code in 32..126 } }
}
