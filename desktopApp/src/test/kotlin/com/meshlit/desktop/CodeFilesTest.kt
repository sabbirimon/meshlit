package com.meshlit.desktop
import java.nio.file.Files
import kotlin.test.*
class CodeFilesTest {
    @Test fun editsPreserveExternalChangesAndStayInsideWorkspace() {
        val root = Files.createTempDirectory("code-workspace")
        val outside = Files.createTempFile("code-outside", ".txt")
        try {
            val file = root.resolve("hello.kt"); Files.writeString(file, "initial")
            val editor = CodeFiles(root); val opened = editor.read(file)
            assertEquals("changed", editor.save(opened, "changed").text)
            assertFails { editor.save(opened, "stale") }
            assertEquals("changed", Files.readString(file))
            assertFails { editor.read(outside) }
            Files.write(file, byteArrayOf(1,0,2)); assertFails { editor.read(file) }
            if (Files.getFileStore(root).supportsFileAttributeView("posix")) {
                val link = root.resolve("link"); Files.createSymbolicLink(link, outside)
                assertFails { editor.read(link) }; assertFalse(editor.entries().contains(link))
            }
        } finally { root.toFile().deleteRecursively(); Files.deleteIfExists(outside) }
    }
}
