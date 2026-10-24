// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
package com.meshlit.core.hyperl

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.DataOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Independently authored encrypted fixtures, including authenticated invalid JSON. */
class DatasetFormatTest {
    private fun sha(data: ByteArray) = MessageDigest.getInstance("SHA-256").digest(data)
        .joinToString("") { "%02x".format(it) }

    private fun encrypt(key: ByteArray, aad: String, plaintext: ByteArray): ByteArray {
        val nonce = ByteArray(12).also(SecureRandom()::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        cipher.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return nonce + cipher.doFinal(plaintext)
    }

    private fun fixture(root: Path, keyFile: Path, version: Int, records: List<String>,
                        bytes: ByteArray = byteArrayOf(1, 2, 3)): Path {
        val dataset = Files.createDirectory(root)
        val key = Files.readAllBytes(keyFile)
        val id = UUID.randomUUID().toString()
        val keyId = sha(key)
        Files.writeString(dataset.resolve("id"), id)
        val prefix = "hyperl-dataset/$version:$id" + if (version == 2) ":$keyId" else ""
        val chunk = if (version == 1) dataset.resolve("chunk_0.aesgcm") else {
            Files.writeString(dataset.resolve("key-id"), keyId)
            Files.createDirectories(dataset.resolve("chunks/0000")).resolve("chunk_0.aesgcm")
        }
        Files.write(chunk, encrypt(key, "$prefix:0:${bytes.size}", bytes))
        DataOutputStream(Files.newOutputStream(dataset.resolve("manifest.aesgcm"))).use { output ->
            output.write("HLM$version".toByteArray(Charsets.US_ASCII))
            records.forEachIndexed { index, text ->
                val frame = encrypt(key, "$prefix:manifest:$index", text.toByteArray(Charsets.UTF_8))
                output.writeShort(frame.size)
                output.write(frame)
            }
        }
        return dataset
    }

    private fun delete(root: Path) = Files.walk(root).use {
        it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
    }

    @Test fun legacyManifestExportsAndRotatesIntoTypedFormat() = runBlocking {
        val root = Files.createTempDirectory("hyperl-legacy-")
        try {
            val oldKey = root.resolve("old-key").also(LargeData::keygen)
            val newKey = root.resolve("new-key").also(LargeData::keygen)
            val digest = sha(byteArrayOf(1, 2, 3))
            val legacy = fixture(root.resolve("legacy"), oldKey, 1, listOf(
                "{\"index\":0,\"bytes\":3,\"sha256\":\"$digest\"}",
                "{\"totalBytes\":3,\"chunks\":1,\"sha256\":\"$digest\"}"
            ))
            val original = Files.readAllBytes(legacy.resolve("manifest.aesgcm"))
            val summary = LargeData.export(legacy, root.resolve("restored"), oldKey, 3)
            val rotated = root.resolve("rotated")
            assertEquals(summary, LargeData.rekey(legacy, rotated, oldKey, newKey, 3))
            assertEquals("HLM2", Files.readAllBytes(rotated.resolve("manifest.aesgcm"))
                .copyOfRange(0, 4).toString(Charsets.US_ASCII))
            assertEquals(summary, LargeData.export(rotated, root.resolve("new-output"), newKey, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), Files.readAllBytes(root.resolve("new-output")))
            assertArrayEquals(original, Files.readAllBytes(legacy.resolve("manifest.aesgcm")))
        } finally { delete(root) }
    }

    @Test fun multiChunkRotationPreservesSourceAndRejectsOldKeyAndOverwrites() = runBlocking {
        val root = Files.createTempDirectory("hyperl-rekey-")
        try {
            val oldKey = root.resolve("old-key").also(LargeData::keygen)
            val newKey = root.resolve("new-key").also(LargeData::keygen)
            val source = root.resolve("source")
            val data = ByteArray(LargeData.CHUNK_BYTES + 17) { (it % 251).toByte() }
            Files.write(source, data)
            val dataset = root.resolve("dataset")
            val summary = LargeData.import(source, dataset, oldKey, data.size.toLong())
            val originalManifest = Files.readAllBytes(dataset.resolve("manifest.aesgcm"))
            val rotated = root.resolve("rotated")
            assertEquals(summary, LargeData.rekey(dataset, rotated, oldKey, newKey, data.size.toLong()))
            assertNotEquals(Files.readString(dataset.resolve("key-id")), Files.readString(rotated.resolve("key-id")))
            assertArrayEquals(originalManifest, Files.readAllBytes(dataset.resolve("manifest.aesgcm")))
            assertEquals(summary, LargeData.export(rotated, root.resolve("output"), newKey, data.size.toLong()))
            assertArrayEquals(data, Files.readAllBytes(root.resolve("output")))
            try {
                LargeData.export(rotated, root.resolve("wrong-key-output"), oldKey, data.size.toLong())
                fail("Old key must not decrypt rotated dataset")
            } catch (_: IllegalArgumentException) { }
            assertFalse(Files.exists(root.resolve("wrong-key-output")))
            try {
                LargeData.rekey(dataset, rotated, oldKey, newKey, data.size.toLong())
                fail("Rekey must not overwrite")
            } catch (_: IllegalArgumentException) { }
            try {
                LargeData.export(rotated, source, newKey, data.size.toLong())
                fail("Export must not overwrite")
            } catch (_: IllegalArgumentException) { }
            assertArrayEquals(data, Files.readAllBytes(source))
            assertFalse(Files.list(root).use { paths -> paths.anyMatch { it.fileName.toString().startsWith(".hyperl-data-") } })
        } finally { delete(root) }
    }

    @Test fun authenticatedMalformedRecordsNeverPublishPlaintextOrRotatedDataset() = runBlocking {
        val root = Files.createTempDirectory("hyperl-schema-")
        try {
            val key = root.resolve("key").also(LargeData::keygen)
            val newKey = root.resolve("new-key").also(LargeData::keygen)
            val digest = sha(byteArrayOf(1, 2, 3))
            val cases = listOf(
                "{\"type\":\"unknown\",\"index\":0,\"bytes\":3,\"sha256\":\"$digest\"}",
                "{\"type\":\"chunk\",\"index\":0,\"bytes\":\"3\",\"sha256\":\"$digest\"}",
                "{\"type\":\"chunk\",\"index\":0,\"bytes\":3,\"sha256\":\"$digest\",\"totalBytes\":3}",
                "{\"type\":\"chunk\",\"index\":0,\"bytes\":3,\"sha256\":\"totalBytes\"}",
                "{\"index\":0,\"bytes\":3,\"sha256\":\"$digest\"}"
            )
            cases.forEachIndexed { index, row ->
                val dataset = fixture(root.resolve("case-$index"), key, 2, listOf(row,
                    "{\"type\":\"footer\",\"totalBytes\":3,\"chunks\":1,\"sha256\":\"$digest\"}"))
                try {
                    LargeData.export(dataset, root.resolve("output-$index"), key, 3)
                    fail("Invalid authenticated record must be rejected")
                } catch (_: Exception) { }
                try {
                    LargeData.rekey(dataset, root.resolve("rotated-$index"), key, newKey, 3)
                    fail("Invalid dataset must not be rotated")
                } catch (_: Exception) { }
                assertFalse(Files.exists(root.resolve("output-$index")))
                assertFalse(Files.exists(root.resolve("rotated-$index")))
            }
        } finally { delete(root) }
    }
}
