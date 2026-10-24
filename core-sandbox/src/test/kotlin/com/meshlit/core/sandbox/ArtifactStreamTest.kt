package com.meshlit.core.sandbox

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.security.MessageDigest

class ArtifactStreamTest {
    private val bytes="owned image bytes".toByteArray()
    private fun hash(data:ByteArray)=MessageDigest.getInstance("SHA-256").digest(data).joinToString(""){"%02x".format(it.toInt() and 255)}
    @Test fun verifiedStreamCommitsAndDoesNotOverwrite()=runBlocking {
        val dir=Files.createTempDirectory("artifact-stream").toFile()
        try {
            val store=ArtifactStore(dir);var progress=0L
            val file=store.installStream({ByteArrayInputStream(bytes)},"owned.qcow2",hash(bytes),bytes.size.toLong(),progress={progress=it})
            assertArrayEquals(bytes,file.readBytes());assertEquals(bytes.size.toLong(),progress)
            val replacement="other bytes".toByteArray()
            assertTrue(runCatching {store.installStream({ByteArrayInputStream(replacement)},"owned.qcow2",hash(replacement),replacement.size.toLong())}.isFailure)
            assertArrayEquals(bytes,file.readBytes())
        } finally {dir.deleteRecursively()}
    }
    @Test fun corruptTruncatedAndOversizedImportsLeaveNoFiles()=runBlocking {
        val dir=Files.createTempDirectory("artifact-invalid").toFile()
        try {
            val store=ArtifactStore(dir)
            for ((digest,size) in listOf("0".repeat(64) to bytes.size.toLong(),hash(bytes) to (bytes.size+1L),hash(bytes) to (bytes.size-1L))) {
                assertTrue(runCatching {store.installStream({ByteArrayInputStream(bytes)},"owned.qcow2",digest,size)}.isFailure)
                assertTrue(dir.listFiles()!!.isEmpty())
            }
        } finally {dir.deleteRecursively()}
    }
    @Test fun cancellationClosesInputAndRemovesStaging()=runBlocking {
        val dir=Files.createTempDirectory("artifact-cancel").toFile()
        try {
            var closed=false
            val result=runCatching {ArtifactStore(dir).installStream({object:ByteArrayInputStream(bytes){override fun close(){closed=true;super.close()}}},"owned.qcow2",hash(bytes),bytes.size.toLong(),progress={throw CancellationException("owner stopped")})}
            assertTrue(result.exceptionOrNull() is CancellationException);assertTrue(closed);assertTrue(dir.listFiles()!!.isEmpty())
        } finally {dir.deleteRecursively()}
    }
    @Test fun networkSourcesRejectPlaintextCredentialsAndQueries()=runBlocking {
        val dir=Files.createTempDirectory("artifact-url").toFile()
        try {
            for(url in listOf("http://example.com/disk","https://user:secret@example.com/disk","https://example.com/disk?token=secret","https://example.com/disk#fragment")) {
                assertTrue(runCatching {ArtifactStore(dir).download(url,"owned.qcow2",hash(bytes),bytes.size.toLong())}.isFailure)
            }
            assertTrue(dir.listFiles()!!.isEmpty())
        } finally {dir.deleteRecursively()}
    }
}
