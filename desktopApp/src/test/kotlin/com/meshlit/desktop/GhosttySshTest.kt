package com.meshlit.desktop
import java.nio.ByteBuffer
import java.nio.file.Path
import java.security.MessageDigest
import java.util.Base64
import kotlin.test.*
import org.junit.Test
class GhosttySshTest {
    @Test fun pinAndLiteralArguments() {
        val type="ssh-ed25519".toByteArray(); val wire=ByteBuffer.allocate(4+type.size+4+32).putInt(type.size).put(type).putInt(32).put(ByteArray(32){it.toByte()}).array()
        val key="ssh-ed25519 "+Base64.getEncoder().encodeToString(wire)
        val pin="SHA256:"+Base64.getEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256").digest(wire))
        assertTrue(GhosttySsh.knownHost("node.example",2222,key,pin).startsWith("[node.example]:2222 ssh-ed25519 "))
        assertFailsWith<IllegalArgumentException>{GhosttySsh.knownHost("node.example",22,key,"SHA256:"+"A".repeat(43))}
        // Argument construction is portable; POSIX root-only paths lack a drive on Windows.
        // No terminal or SSH process is launched by this contract test.
        val root=Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath()
        val terminal=root.resolve("ghostty"); val ssh=root.resolve("ssh")
        val identity=root.resolve("key with spaces"); val pins=root.resolve("pins")
        val args=GhosttySsh.arguments(terminal,ssh,"node.example",2222,"owner",identity,pins)
        assertTrue("StrictHostKeyChecking=yes" in args); assertTrue("ClearAllForwardings=yes" in args); assertTrue(identity.toString() in args)
        assertFalse(args.any { it == "sh" || it == "-c" }); assertTrue("--config-default-files=false" in args)
        assertFailsWith<IllegalArgumentException>{GhosttySsh.arguments(terminal,ssh,"-oProxyCommand=bad",22,"owner",identity,pins)}
        assertFailsWith<IllegalArgumentException>{GhosttySsh.arguments(Path.of("relative"),ssh,"node.example",22,"owner",identity,pins)}
    }
}
