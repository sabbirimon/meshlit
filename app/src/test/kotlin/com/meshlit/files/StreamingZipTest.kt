package com.meshlit.files
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.*
import java.util.zip.*
class StreamingZipTest {
    private fun archive(entries:List<Pair<String,ByteArray>>):ByteArray=ByteArrayOutputStream().also{out->ZipOutputStream(out).use{zip->entries.forEach{(name,bytes)->zip.putNextEntry(ZipEntry(name));zip.write(bytes);zip.closeEntry()}}}.toByteArray()
    @Test fun streamsMultipleAiFilesAndUnicodeNames()=runBlocking {
        val model=ByteArray(1000000){(it%251).toByte()};val dataset="{\"text\":\"training sample\"}\n".toByteArray();val out=ByteArrayOutputStream()
        val result=StreamingZip.create(listOf(ArchiveInput("模型.gguf"){model.inputStream()},ArchiveInput("dataset.jsonl"){dataset.inputStream()}),out,ArchiveLimits()){}
        assertEquals(model.size+dataset.size.toLong(),result.bytes);assertEquals(2,result.files)
        val files=mutableMapOf<String,ByteArrayOutputStream>()
        StreamingZip.extract(out.toByteArray().inputStream(),ArchiveLimits(),{}, {name->ByteArrayOutputStream().also{files[name]=it}}){}
        assertArrayEquals(model,files["模型.gguf"]!!.toByteArray());assertArrayEquals(dataset,files["dataset.jsonl"]!!.toByteArray())
    }
    @Test fun malformedHeadersFailAndPreflightClosesOutput()=runBlocking {
        for(bytes in listOf(byteArrayOf(),"not zip".toByteArray(),byteArrayOf(80,75,5,6))) assertTrue(runCatching{StreamingZip.extract(bytes.inputStream(),ArchiveLimits(),{},{ByteArrayOutputStream()}){}}.isFailure)
        var closed=false;val out=object:ByteArrayOutputStream(){override fun close(){closed=true;super.close()}}
        assertTrue(runCatching{StreamingZip.create(listOf(ArchiveInput("../bad"){byteArrayOf().inputStream()}),out,ArchiveLimits()){}}.isFailure);assertTrue(closed)
        val empty=archive(emptyList());assertEquals(0,StreamingZip.extract(empty.inputStream(),ArchiveLimits(),{},{ByteArrayOutputStream()}){}.files)
        val collision=archive(listOf("Model.gguf" to byteArrayOf(1),"model.gguf" to byteArrayOf(2)))
        assertTrue(runCatching{StreamingZip.extract(collision.inputStream(),ArchiveLimits(),{},{ByteArrayOutputStream()}){}}.isFailure)
    }
    @Test fun traversalAbsoluteDriveAndAmbiguousPathsAreRejected(){
        listOf("../secret","/absolute","C:/windows","a/../../b","a\\b","a//b","a/./b","a\u0000b").forEach{assertTrue(it,runCatching{StreamingZip.checkedPath(it)}.isFailure)}
    }
    @Test fun expandedByteAndEntryLimitsStopExtraction()=runBlocking {
        val bytes=archive(listOf("large.gguf" to ByteArray(5000)))
        assertTrue(runCatching{StreamingZip.extract(bytes.inputStream(),ArchiveLimits(maxExpandedBytes=1000),{}, {ByteArrayOutputStream()}){}}.isFailure)
        val multiple=archive(listOf("one" to byteArrayOf(1),"two" to byteArrayOf(2)))
        assertTrue(runCatching{StreamingZip.extract(multiple.inputStream(),ArchiveLimits(maxEntries=1),{}, {ByteArrayOutputStream()}){}}.isFailure)
    }
    @Test fun corruptStoredEntryFailsCrcVerification()=runBlocking {
        val out=ByteArrayOutputStream();val data=byteArrayOf(1,2,3,4)
        ZipOutputStream(out).use{zip->val entry=ZipEntry("file").apply{method=ZipEntry.STORED;size=data.size.toLong();compressedSize=size;crc=CRC32().apply{update(data)}.value};zip.putNextEntry(entry);zip.write(data);zip.closeEntry()}
        val bytes=out.toByteArray();bytes[34]=(bytes[34].toInt() xor 1).toByte()
        assertTrue(runCatching{StreamingZip.extract(bytes.inputStream(),ArchiveLimits(),{}, {ByteArrayOutputStream()}){}}.isFailure)
    }
    @Test fun duplicateInputNamesAndCancellationAreRejected()=runBlocking {
        val inputs=listOf(ArchiveInput("model.gguf"){byteArrayOf(1).inputStream()},ArchiveInput("model.gguf"){byteArrayOf(2).inputStream()})
        assertTrue(runCatching{StreamingZip.create(inputs,ByteArrayOutputStream(),ArchiveLimits()){}}.isFailure)
        var closed=false;var copied=0L
        val job=launch{StreamingZip.create(listOf(ArchiveInput("model.gguf"){object:FilterInputStream(ByteArray(1000000).inputStream()){override fun close(){closed=true;super.close()}}}),ByteArrayOutputStream(),ArchiveLimits()){copied=it.bytes;cancel()}}
        job.join();assertTrue(job.isCancelled);assertTrue(closed);assertEquals(65536L,copied)
    }
}
