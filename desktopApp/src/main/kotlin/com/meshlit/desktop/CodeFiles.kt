package com.meshlit.desktop

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.*
import java.security.MessageDigest

internal data class CodeDocument(val path: Path, val text: String, val digest: String)
/** Bounded text editor. Only explicit user-selected paths; no automatic repository execution. */
internal class CodeFiles(folder: Path) {
    private val selectedRoot = folder.toAbsolutePath().normalize()
    val root: Path = folder.toRealPath().also { require(Files.isDirectory(it)) }
    private fun checked(path: Path): Path {
        val selected = path.toAbsolutePath().normalize()
        val normalized = if(selected.startsWith(selectedRoot)) root.resolve(selectedRoot.relativize(selected)) else selected
        require(normalized.startsWith(root) && normalized != root) { "Choose a file inside this workspace" }
        var cursor = root
        root.relativize(normalized).forEach { part -> cursor = cursor.resolve(part); require(!Files.isSymbolicLink(cursor)) { "Symbolic links are not editable here" } }
        require(normalized.toRealPath().startsWith(root))
        return normalized
    }
    fun entries(directory: Path = root): List<Path> {
        val dir = if(directory == root) root else checked(directory)
        require(Files.isDirectory(dir))
        return Files.list(dir).use { stream -> stream.filter { !it.fileName.toString().startsWith(".") && !Files.isSymbolicLink(it) }
            .limit(501).toList().also { require(it.size <= 500) { "Folder has more than 500 entries; choose a smaller folder" } }
            .sortedWith(compareBy<Path> { !Files.isDirectory(it) }.thenBy { it.fileName.toString().lowercase() }) }
    }
    fun read(path: Path): CodeDocument {
        val file = checked(path)
        require(Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && Files.size(file) <= 1_048_576) { "Only text files up to 1 MiB can be edited" }
        val bytes = Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(1_048_577) }
        require(bytes.size <= 1_048_576 && bytes.none { it == 0.toByte() }) { "Binary or oversized file" }
        return CodeDocument(file, bytes.decodeToString(throwOnInvalidSequence = true), hash(bytes))
    }
    fun save(document: CodeDocument, text: String): CodeDocument {
        val file = checked(document.path)
        val bytes = text.toByteArray(Charsets.UTF_8)
        require(bytes.size <= 1_048_576 && '\u0000' !in text)
        check(read(file).digest == document.digest) { "File changed outside Meshlit; reload before saving" }
        val temporary = Files.createTempFile(file.parent, ".meshlit-edit-", ".tmp")
        try {
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(temporary, Files.getPosixFilePermissions(file))
            FileChannel.open(temporary, StandardOpenOption.WRITE).use { channel ->
                val buffer = ByteBuffer.wrap(bytes); while(buffer.hasRemaining()) channel.write(buffer); channel.force(true)
            }
            check(read(file).digest == document.digest) { "File changed outside Meshlit; reload before saving" }
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            return CodeDocument(file, text, hash(bytes))
        } finally { Files.deleteIfExists(temporary) }
    }
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
