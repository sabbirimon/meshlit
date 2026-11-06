package com.meshlit.core.federation.recovery

import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext

class ReplicaDeviceTlsTest {
    @Test fun directDeviceTlsRejectsWrongPinBeforeSendingCredentialsAndTransfersAQuorum() = runBlocking {
        val root = Files.createTempDirectory("meshlit-replica-tls").toFile()
        val servers = mutableListOf<MockWebServer>()
        try {
            val password = "local-fixture"
            val storeFile = File(root, "replica.p12")
            val keytool = File(System.getProperty("java.home"), "bin/keytool")
            val tool = ProcessBuilder(
                keytool.path, "-genkeypair", "-alias", "replica", "-keyalg", "EC",
                "-dname", "CN=Meshlit LAN Fixture", "-storetype", "PKCS12",
                "-keystore", storeFile.path, "-storepass", password, "-validity", "2", "-noprompt",
            ).redirectErrorStream(true).start()
            try {
                check(tool.waitFor(20, TimeUnit.SECONDS) && tool.exitValue() == 0) { "Could not create test TLS certificate" }
            } finally {
                if (tool.isAlive) tool.destroyForcibly()
            }
            val keyStore = KeyStore.getInstance("PKCS12").apply {
                storeFile.inputStream().use { load(it, password.toCharArray()) }
            }
            val managers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply {
                init(keyStore, password.toCharArray())
            }
            val tls = SSLContext.getInstance("TLS").apply { init(managers.keyManagers, null, null) }
            val pin = sha256(keyStore.getCertificate("replica").encoded)
            val keys = (1..3).map { KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair() }
            val group = ReplicaGroup("private-tls", keys.mapIndexed { i, key ->
                ReplicaMember("phone-$i", Base64.getEncoder().encodeToString(key.public.encoded))
            })
            val token = "x".repeat(48)
            group.members.forEachIndexed { i, member ->
                val acceptor = ReplicaAcceptor(group, member.id, keys[i].private, object : ReplicaPersistence {
                    private var state: ReplicaState? = null
                    override fun load() = state
                    override fun save(state: ReplicaState) { this.state = state }
                })
                val server = MockWebServer()
                servers += server
                server.useHttps(tls.socketFactory, false)
                server.dispatcher = object : Dispatcher() {
                    override fun dispatch(request: RecordedRequest): MockResponse {
                        if (request.getHeader("Authorization") != "Bearer $token") return MockResponse().setResponseCode(401)
                        val reply = runCatching {
                            acceptor.handle(Json.decodeFromString<ReplicaRequest>(request.body.readUtf8()))
                        }.getOrElse { return MockResponse().setResponseCode(409) }
                        return MockResponse().setHeader("Content-Type", "application/json").setBody(Json.encodeToString(reply))
                    }
                }
                server.start()
            }
            val endpoints = servers.mapIndexed { i, server ->
                ReplicaEndpoint(
                    group.members[i].id, server.url("/replica").newBuilder().host("127.0.0.1").build().toString(),
                    token, deviceCertificateSha256 = pin,
                )
            }
            ReplicaHttpTransport(endpoints.map { it.copy(deviceCertificateSha256 = "0".repeat(64)) }).use { wrong ->
                assertTrue(runCatching { ReplicaJournal(group, wrong).read() }.isFailure)
                assertEquals(listOf(0, 0, 0), servers.map { it.requestCount })
                assertTrue(wrong.pathStatus().all { it.identityRejected })
            }
            ReplicaHttpTransport(endpoints).use { original ->
                ReplicaJournal(group, original).append(JournalEntry("started", "task", "coordinator-a", 1, JournalAction.ACQUIRE))
            }
            servers.last().shutdown()
            ReplicaHttpTransport(endpoints).use { replacement ->
                val journal = ReplicaJournal(group, replacement)
                journal.append(JournalEntry("handoff", "task", "coordinator-b", 2, JournalAction.ACQUIRE, expectedTaskHead = "started"))
                assertEquals("coordinator-b", journal.read().last().value.entry.owner)
                assertTrue(runCatching {
                    journal.append(JournalEntry(
                        "stale", "task", "coordinator-a", 1, JournalAction.SNAPSHOT,
                        modelSha256 = "a".repeat(64), expectedTaskHead = "handoff",
                    ))
                }.isFailure)
            }
        } finally {
            servers.forEach { runCatching { it.shutdown() } }
            root.deleteRecursively()
        }
        Unit
    }

    @Test fun selfSignedModeIsLimitedToExactPrivateHttpsDestinations() {
        val pin = "a".repeat(64)
        ReplicaPath("https://192.168.2.3:18894/replica", "x".repeat(48), deviceCertificateSha256 = pin).validate()
        for (url in listOf("http://127.0.0.1/replica", "https://1.2.3.4/replica", "https://example.com/replica")) {
            assertThrows(IllegalArgumentException::class.java) {
                ReplicaPath(url, "x".repeat(48), deviceCertificateSha256 = pin).validate()
            }
        }
    }

    private fun sha256(bytes: ByteArray) =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
