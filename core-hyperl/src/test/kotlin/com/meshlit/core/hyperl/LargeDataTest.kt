// SPDX-License-Identifier: LicenseRef-HyperL-Community-1.0
package com.meshlit.core.hyperl
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files

class LargeDataTest {
    @Test fun truncatedOrTamperedManifestNeverPublishesAndEmptyFileWorks()=runBlocking {
        val dir=Files.createTempDirectory("hyperl-manifest-test-")
        try {
            val key=dir.resolve("key");LargeData.keygen(key);val source=dir.resolve("source");Files.write(source,byteArrayOf())
            LargeData.import(source,dir.resolve("dataset"),key,1)
            assertEquals(0L,LargeData.export(dir.resolve("dataset"),dir.resolve("empty"),key,1).bytes)
            val manifest=dir.resolve("dataset/manifest.aesgcm");val original=Files.readAllBytes(manifest)
            for(broken in listOf(original.copyOf(original.size-1),original.copyOf().also{it[it.lastIndex]=(it.last().toInt() xor 1).toByte()})){
                Files.write(manifest,broken)
                try{LargeData.export(dir.resolve("dataset"),dir.resolve("unverified"),key,1);fail("Bad manifest")}catch(_:Exception){}
                assertFalse(Files.exists(dir.resolve("unverified")))
            }
        }finally{Files.walk(dir).use{it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
    @Test fun actualMultiChunkFileEncryptsAndRestores()=runBlocking {
        val dir=Files.createTempDirectory("hyperl-data-test-")
        try {
            val source=dir.resolve("source");val data=ByteArray(LargeData.CHUNK_BYTES+512){(it%251).toByte()};Files.write(source,data)
            val key=dir.resolve("key");LargeData.keygen(key)
            val summary=LargeData.import(source,dir.resolve("dataset"),key,data.size.toLong())
            assertEquals(2,summary.chunks);assertEquals(data.size.toLong(),summary.bytes)
            assertEquals(summary,LargeData.export(dir.resolve("dataset"),dir.resolve("restored"),key,data.size.toLong()))
            assertArrayEquals(data,Files.readAllBytes(dir.resolve("restored")))
            assertFalse(Files.readAllBytes(dir.resolve("dataset/chunks/0000/chunk_0.aesgcm")).copyOfRange(12,24).contentEquals(data.copyOfRange(0,12)))
        }finally{Files.walk(dir).use{it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
    @Test fun wrongKeyTamperAndQuotaNeverPublishOutput()=runBlocking {
        val dir=Files.createTempDirectory("hyperl-data-fail-")
        try {
            val source=dir.resolve("source");Files.write(source,byteArrayOf(1,2,3));val key=dir.resolve("key");val wrong=dir.resolve("wrong");LargeData.keygen(key);LargeData.keygen(wrong)
            try{LargeData.import(source,dir.resolve("overquota"),key,2);fail("Quota") }catch(_:IllegalArgumentException){};assertFalse(Files.exists(dir.resolve("overquota")))
            LargeData.import(source,dir.resolve("dataset"),key,3)
            try{LargeData.export(dir.resolve("dataset"),dir.resolve("output"),wrong,3);fail("Wrong key")}catch(_:Exception){};assertFalse(Files.exists(dir.resolve("output")))
            val chunk=dir.resolve("dataset/chunks/0000/chunk_0.aesgcm");val bytes=Files.readAllBytes(chunk);bytes[bytes.lastIndex]=(bytes.last().toInt() xor 1).toByte();Files.write(chunk,bytes)
            try{LargeData.export(dir.resolve("dataset"),dir.resolve("output"),key,3);fail("Tamper")}catch(_:Exception){};assertFalse(Files.exists(dir.resolve("output")))
            assertFalse(Files.list(dir).use{it.anyMatch{p->p.fileName.toString().startsWith(".hyperl-data-")}})
        }finally{Files.walk(dir).use{it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
}
