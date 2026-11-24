package com.meshlit.desktop

import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption.*
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import java.util.UUID

internal object StudioBuild {
    private val values = Properties().apply {
        StudioBuild::class.java.getResourceAsStream("/studio-build.properties")?.use { load(it) }
    }
    val version: String get() = values.getProperty("version", "unversioned")
    val revision: String get() = values.getProperty("revision", "unknown")
    val builtAt: String get() = values.getProperty("builtAt", "unknown")
    val title: String get() = "Meshlit · $version · ${revision.take(12)}${if(values.getProperty("dirty") == "true") " + changes" else ""}"
    val details: String get() = "$title\nBuilt: $builtAt\nSource: $revision\nRuntime: Java ${System.getProperty("java.version")} · ${System.getProperty("os.name")} ${System.getProperty("os.arch")}"
}

/** One GUI per user profile, shared by installed and development launches. No network listener. */
internal class StudioInstance private constructor(
    private val channel: FileChannel,
    private val lock: FileLock,
    private val request: Path,
    private var lastRequest: String,
) : AutoCloseable {
    fun consumeActivation(): Boolean {
        if(!Files.exists(request)) return false
        require(!Files.isSymbolicLink(request) && Files.size(request) <= 128)
        val value = Files.readString(request)
        return (value != lastRequest).also { lastRequest = value }
    }
    @Synchronized override fun close() {
        if(channel.isOpen) { lock.release(); channel.close() }
        // Keep the lock inode: removing it could let another process lock a different file.
    }
    companion object {
        fun acquire(directory: Path, identity: String): StudioInstance? {
            Files.createDirectories(directory)
            require(!Files.isSymbolicLink(directory))
            if(Files.getFileStore(directory).supportsFileAttributeView("posix"))
                Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("rwx------"))
            val channel = FileChannel.open(directory.resolve("instance.lock"), CREATE, READ, WRITE, LinkOption.NOFOLLOW_LINKS)
            try {
                val lock = try { channel.tryLock() } catch(_: OverlappingFileLockException) { null }
                val request = directory.resolve("activate")
                val marker = UUID.randomUUID().toString()
                if(lock == null) {
                    Files.newByteChannel(request, setOf(CREATE, WRITE, TRUNCATE_EXISTING, LinkOption.NOFOLLOW_LINKS)).use {
                        it.write(ByteBuffer.wrap(marker.toByteArray()))
                    }
                    channel.close()
                    return null
                }
                val value = "pid=${ProcessHandle.current().pid()}\n$identity\n".toByteArray()
                channel.truncate(0); channel.write(ByteBuffer.wrap(value)); channel.force(true)
                // Observe any existing request without deleting a concurrently written activation.
                val previous = if(Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS) && Files.size(request) <= 128) Files.readString(request) else ""
                return StudioInstance(channel, lock, request, previous)
            } catch(e: Exception) { channel.close(); throw e }
        }
    }
}
