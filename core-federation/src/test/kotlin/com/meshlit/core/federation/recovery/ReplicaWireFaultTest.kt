package com.meshlit.core.federation.recovery

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.KeyPairGenerator
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

/** Real sockets, encrypted files, restarted acceptor and denied endpoint. Same-host failure domains. */
class ReplicaWireFaultTest {
    @Test fun socketQuorumSurvivesOneUnavailableReplicaAndEncryptedRestart() = runBlocking {
        val root = Files.createTempDirectory("meshlit-replica-wire")
        val keys = (0..2).map { KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair() }
        val group = ReplicaGroup("wire", keys.mapIndexed { i, key -> ReplicaMember("node$i", Base64.getEncoder().encodeToString(key.public.encoded)) })
        val aes = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        val stores = (0..2).map { i -> object : ReplicaPersistence {
            val file = root.resolve("replica$i")
            override fun load(): ReplicaState? {
                if (!Files.exists(file)) return null
                val bytes = Files.readAllBytes(file); val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, bytes.copyOfRange(0, 12)))
                return Json.decodeFromString(String(cipher.doFinal(bytes.copyOfRange(12, bytes.size))))
            }
            override fun save(state: ReplicaState) {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, aes)
                val stage = root.resolve("stage$i")
                java.io.FileOutputStream(stage.toFile()).use { stream -> stream.write(cipher.iv + cipher.doFinal(Json.encodeToString(state).toByteArray())); stream.fd.sync() }
                Files.move(stage, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            }
        } }
        val nodes = keys.mapIndexed { i, key -> ReplicaAcceptor(group, "node$i", key.private, stores[i]) }.toMutableList()
        var denyThird = false
        val servers = (0..2).map { i -> MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    if (i == 2 && denyThird || request.getHeader("Authorization") != "Bearer " + "x".repeat(40)) return MockResponse().setResponseCode(403)
                    return try {
                        val value = nodes[i].handle(Json.decodeFromString<ReplicaRequest>(request.body.readUtf8()))
                        MockResponse().setHeader("Content-Type", "application/json").setBody(Json.encodeToString(value))
                    } catch (_: Exception) { MockResponse().setResponseCode(409) }
                }
            }; start()
        } }
        try {
            val endpoints = servers.mapIndexed { i, server -> ReplicaEndpoint("node$i", server.url("/replica").newBuilder().host("127.0.0.1").build().toString(), "x".repeat(40)) }
            val journal = ReplicaJournal(group, ReplicaHttpTransport(endpoints))
            journal.append(JournalEntry("start", "job", "masterA", 1, JournalAction.ACQUIRE))
            nodes[0] = ReplicaAcceptor(group, "node0", keys[0].private, stores[0]); denyThird = true
            journal.append(JournalEntry("transfer", "job", "masterB", 2, JournalAction.ACQUIRE))
            assertEquals("masterB", journal.read().last().value.entry.owner)
            assertFalse(String(Files.readAllBytes(root.resolve("replica0"))).contains("masterB"))
            denyThird = false; journal.read()
            assertEquals(2, nodes[2].handle(ReplicaRequest(group.fingerprint(), "status")).state.commits.size)
        } finally { servers.forEach { it.shutdown() }; root.toFile().deleteRecursively() }
    }
}
