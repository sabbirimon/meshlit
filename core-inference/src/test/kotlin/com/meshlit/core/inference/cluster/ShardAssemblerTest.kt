package com.meshlit.core.inference.cluster

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy

class ShardAssemblerTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Helper — synthesize a model file of size `bytes` filled with a
     *  deterministic pattern, then split it via `splitLocalModel` and
     *  reassemble. Verifies byte-for-byte equality. */
    @Test fun split_then_reassemble_round_trips_bytes() {
        val modelFile = File(tmp.root, "model.bin").apply {
            FileOutputStream(this).use { out ->
                val total = 1024 * 1024
                val buf = ByteArray(64 * 1024)
                for (i in 0 until total / buf.size) {
                    buf.fill(((i and 0xFF).toByte()))
                    out.write(buf)
                }
            }
        }
        val modelId = "split"
        val assignments = listOf(
            ClusterShardPlanner.ShardAssignment(
                shardId = "shard-000",
                layerStart = 0,
                layerEnd = 1,
                peerId = ShardAssembler.SELF_PEER_ID,
                byteOffset = 0L,
                byteLength = modelFile.length() / 2,
            ),
            ClusterShardPlanner.ShardAssignment(
                shardId = "shard-001",
                layerStart = 1,
                layerEnd = 2,
                peerId = ShardAssembler.SELF_PEER_ID,
                byteOffset = modelFile.length() / 2,
                byteLength = modelFile.length() - modelFile.length() / 2,
            ),
        )
        val assembler = ShardAssembler(tmp.root)
        // Split — write local shards.
        val shardFiles = kotlinx.coroutines.runBlocking {
            assembler.splitLocalModel(modelId, modelFile, assignments)
        }
        assertEquals(2, shardFiles.size)
        shardFiles.forEach { assertTrue("missing shard", it.exists()) }

        // Compute expected SHA-256 of the source.
        val expectedSha = sha256(modelFile)

        // Reassemble — the shards live on self, no peer URL needed.
        val reassembled = kotlinx.coroutines.runBlocking {
            assembler.reassemble(
                modelId = modelId,
                assignments = assignments,
                expectedSha256 = expectedSha,
                peerBaseUrl = { error("not used") },
            )
        }
        assertNotNull(reassembled)
        assertEquals(modelFile.length(), reassembled.length())
        assertArrayEquals(
            modelFile.readBytes(),
            reassembled.readBytes(),
        )
    }

    @Test fun mismatch_sha_throws_and_deletes_partial() {
        runBlocking {
            val source=File(tmp.root,"source").apply{writeText("bad!")}
            val assembler=ShardAssembler(tmp.root)
            val assignments=listOf(part("a",0,4,"self"))
            assembler.splitLocalModel("m",source,assignments)
            val previous=File(tmp.root,"reassembled/m.gguf").apply{parentFile!!.mkdirs();writeText("previous good output")}
            assertTrue(runCatching {assembler.reassemble("m",assignments,hash("good"),{null})}.isFailure)
            assertEquals("previous good output",previous.readText())
            assertFalse(File(tmp.root,"reassembled/m.gguf.part").exists())
        }
    }

    @Test fun truncated_or_corrupt_remote_content_is_refetched_once_before_publication() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setChunkedBody("bad",2))
            server.enqueue(MockResponse().setBody("good"))
            val assembler=ShardAssembler(tmp.root)
            val result=assembler.reassemble("m",listOf(part("a",0,4)),hash("good"),{server.url("/").toString().trimEnd('/')})
            assertEquals("good",result.readText());assertEquals(2,server.requestCount)
            // A same-size corruption is also retried, not trusted by length.
            result.writeText("bad!")
            assertNull(assembler.verifiedCachedModel("m",4,hash("good")))
            server.enqueue(MockResponse().setBody("bad!"));server.enqueue(MockResponse().setBody("good"))
            assertEquals("good",assembler.reassemble("m",listOf(part("a",0,4)),hash("good"),{server.url("/").toString().trimEnd('/')}).readText())
            assertEquals(4,server.requestCount)
        }
    }

    @Test fun repeated_corruption_is_bounded_and_keeps_previous_committed_file() = runBlocking {
        MockWebServer().use { server ->
            repeat(2){server.enqueue(MockResponse().setBody("bad!"))}
            val previous=File(tmp.root,"reassembled/m.gguf").apply{parentFile!!.mkdirs();writeText("older verified model")}
            assertTrue(runCatching {ShardAssembler(tmp.root).reassemble("m",listOf(part("a",0,4)),hash("good"),{server.url("/").toString().trimEnd('/')})}.isFailure)
            assertEquals(2,server.requestCount);assertEquals("older verified model",previous.readText())
            assertFalse(File(tmp.root,"reassembled/m.gguf.part").exists())
        }
    }

    @Test fun cancellation_resume_rehashes_valid_ranges_and_refetches_damaged_ranges() = runBlocking {
        MockWebServer().use { server ->
            val assembler=ShardAssembler(tmp.root)
            val parts=listOf(part("a",0,4),part("b",4,4))
            val hashes=mapOf("a" to hash("AAAA"),"b" to hash("BBBB"))
            val base={ _:String -> server.url("/").toString().trimEnd('/') }
            for (damage in listOf(false,true)) {
                val id=if(damage) "damaged" else "intact"
                server.enqueue(MockResponse().setBody("AAAA"))
                val stopped=runCatching {assembler.reassemble(id,parts,hash("AAAABBBB"),base,
                    onProgress={if(it==50L) throw CancellationException("stop")},expectedShardSha256=hashes)}
                assertTrue(stopped.exceptionOrNull() is CancellationException)
                assertFalse(File(tmp.root,"reassembled/$id.gguf").exists())
                if(damage) {
                    java.io.RandomAccessFile(File(tmp.root,"reassembled/$id.gguf.part"),"rw").use{it.writeByte(0)}
                    server.enqueue(MockResponse().setBody("AAAA"))
                }
                server.enqueue(MockResponse().setBody("BBBB"))
                val before=server.requestCount
                val result=assembler.reassemble(id,parts,hash("AAAABBBB"),base,expectedShardSha256=hashes)
                assertEquals("AAAABBBB",result.readText())
                assertEquals(if(damage) 2 else 1,server.requestCount-before)
            }
        }
    }

    @Test fun changed_resume_manifest_cannot_reuse_old_ranges() = runBlocking {
        MockWebServer().use { server ->
            val assembler=ShardAssembler(tmp.root)
            val parts=listOf(part("a",0,4),part("b",4,4))
            val base={ _:String -> server.url("/").toString().trimEnd('/') }
            server.enqueue(MockResponse().setBody("AAAA"))
            runCatching {assembler.reassemble("m",parts,hash("AAAABBBB"),base,
                onProgress={throw CancellationException("stop")},expectedShardSha256=mapOf("a" to hash("AAAA"),"b" to hash("BBBB")))}
            server.enqueue(MockResponse().setBody("CCCC"));server.enqueue(MockResponse().setBody("DDDD"))
            val result=assembler.reassemble("m",parts,hash("CCCCDDDD"),base,
                expectedShardSha256=mapOf("a" to hash("CCCC"),"b" to hash("DDDD")))
            assertEquals("CCCCDDDD",result.readText());assertEquals(3,server.requestCount)
        }
    }

    @Test fun invalid_manifests_and_missing_digest_fail_before_any_fetch() = runBlocking {
        val assembler=ShardAssembler(tmp.root)
        var fetches=0
        val unexpectedSource={ _:String -> fetches++; "http://127.0.0.1:1" }
        val invalid=listOf(listOf(part("a",1,4)),listOf(part("a",0,4),part("b",3,4)),
            listOf(part("../escape",0,4)),listOf(part("a",0,Long.MAX_VALUE)))
        for(parts in invalid) assertTrue(runCatching {assembler.reassemble("m",parts,hash("good"),unexpectedSource)}.isFailure)
        assertTrue(runCatching {assembler.reassemble("m",listOf(part("a",0,4)),"",unexpectedSource)}.isFailure)
        assertTrue(runCatching {assembler.reassemble("m",listOf(part("a",0,4)),hash("good"),unexpectedSource,expectedSizeBytes=5)}.isFailure)
        assertEquals(0,fetches)
    }

    @Test fun contradictory_model_and_chunk_digests_discard_resume_state() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("good"))
            assertTrue(runCatching {ShardAssembler(tmp.root).reassemble("m",listOf(part("a",0,4)),hash("bad!"),
                {server.url("/").toString().trimEnd('/')},expectedShardSha256=mapOf("a" to hash("good")))}.isFailure)
            assertEquals(1,server.requestCount)
            assertFalse(File(tmp.root,"reassembled/m.gguf").exists())
            assertFalse(File(tmp.root,"reassembled/m.gguf.part").exists())
            assertFalse(File(tmp.root,"reassembled/m.gguf.part.manifest").exists())
        }
    }

    @Test fun transfer_rejects_oversized_payload_and_redirect_and_cancels_hung_read() = runBlocking {
        MockWebServer().use { server ->
            val transport=ShardTransport()
            val target=File(tmp.root,"transfer")
            val base=server.url("/").toString().trimEnd('/')
            server.enqueue(MockResponse().setChunkedBody("oversized",2))
            assertTrue(transport.fetchShardToFile(base,"m","a",target,0,4).isFailure)
            server.enqueue(MockResponse().setResponseCode(302).setHeader("Location",server.url("/other")))
            assertTrue(transport.fetchShardToFile(base,"m","a",target,0,4).isFailure)
            assertEquals(2,server.requestCount)
            server.enqueue(MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE))
            assertNull(withTimeoutOrNull(300){transport.fetchShardToFile(base,"m","a",target,0,4)})
        }
    }

    private fun part(id:String,offset:Long,length:Long,peer:String="peer") =
        ClusterShardPlanner.ShardAssignment(id,if(offset==0L) 0 else 1,if(offset==0L) 1 else 2,peer,offset,length)
    private fun hash(value:String)=MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString(""){"%02x".format(it)}

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buf = ByteArray(64 * 1024)
            var read: Int
            while (input.read(buf).also { read = it } != -1) digest.update(buf, 0, read)
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
