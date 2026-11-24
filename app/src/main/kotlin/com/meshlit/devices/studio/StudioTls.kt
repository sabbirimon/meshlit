package com.meshlit.devices.studio

import com.meshlit.core.trust.PeerCrypto
import com.meshlit.core.trust.RecoverableIdentity
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.DERBitString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo
import org.bouncycastle.asn1.x509.Time
import org.bouncycastle.asn1.x509.V3TBSCertificateGenerator
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyStore
import java.security.SecureRandom
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.X509TrustManager

private fun leaf(identity: RecoverableIdentity): X509Certificate {
    val now = System.currentTimeMillis()
    val algorithm = AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256)
    val name = X500Name("CN=Meshlit device")
    val body = V3TBSCertificateGenerator().apply {
        setSignature(algorithm)
        setSerialNumber(ASN1Integer(BigInteger(128, SecureRandom()).max(BigInteger.ONE)))
        setSubject(name)
        setIssuer(name)
        setStartDate(Time(Date(now - 86400000)))
        setEndDate(Time(Date(now + 365L * 86400000)))
        setSubjectPublicKeyInfo(SubjectPublicKeyInfo.getInstance(PeerCrypto.bytes(identity.publicKey)))
    }.generateTBSCertificate()
    val signature = PeerCrypto.bytes(PeerCrypto.sign(identity, body.encoded))
    val encoded = DERSequence(arrayOf(body, algorithm, DERBitString(signature))).encoded
    return CertificateFactory.getInstance("X.509").generateCertificate(encoded.inputStream()) as X509Certificate
}

internal fun studioEngine(identity: RecoverableIdentity, deviceId: String, pinnedKey: String?): SSLEngine {
    val certificate = leaf(identity)
    val key = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(PeerCrypto.bytes(identity.privateKey)))
    val pass = CharArray(0)
    val store = KeyStore.getInstance("PKCS12").apply {
        load(null, pass)
        setKeyEntry("device", key, pass, arrayOf(certificate))
    }
    val keys = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply { init(store, pass) }
    val trust = object : X509TrustManager {
        override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) { throw CertificateException("Client only") }
        override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
            val peer = chain?.firstOrNull() ?: throw CertificateException("Missing Studio identity.")
            val encoded = PeerCrypto.hex(peer.publicKey.encoded)
            // DER SPKI algorithm identifier for an uncompressed SEC1 prime256v1 key.
            if (encoded.length != 182 || !encoded.startsWith("3059301306072a8648ce3d020106082a8648ce3d03010703420004") ||
                PeerCrypto.fingerprint(encoded).take(20) != deviceId || (pinnedKey != null && encoded != pinnedKey)) {
                throw CertificateException("Studio device identity changed.")
            }
            // TLS 1.3 CertificateVerify proves possession. The SPKI pin is independent of certificate renewal.
        }
    }
    val context = SSLContext.getInstance("TLS").apply { init(keys.keyManagers, arrayOf(trust), null) }
    return context.createSSLEngine().apply {
        useClientMode = true
        check("TLSv1.3" in supportedProtocols) { "This device requires a TLS 1.3 provider." }
        enabledProtocols = arrayOf("TLSv1.3")
    }
}

/** TLS records may span binary messages or share a message with subsequent records. */
internal class StudioTls(private val ws: StudioWebSocket, private val engine: SSLEngine) {
    private val network = ByteBuffer.allocate(StudioWebSocket.LIMIT + 65536).apply { limit(0) }
    private val output = ByteBuffer.allocate(65536)
    private val application = ByteBuffer.allocate(65536)
    private val empty = ByteBuffer.allocate(0)

    private fun wrap(src: ByteBuffer) {
        output.clear()
        val result = engine.wrap(src, output)
        check(result.status == SSLEngineResult.Status.OK) { "Studio TLS write failed." }
        if (output.position() > 0) ws.send(StudioWebSocket.BINARY, output.array().copyOf(output.position()))
    }

    private fun unwrap() {
        val result = engine.unwrap(network, application)
        when (result.status) {
            SSLEngineResult.Status.BUFFER_UNDERFLOW -> {
                network.compact()
                val frame = ws.read(StudioWebSocket.BINARY)
                check(frame.size <= network.remaining()) { "Invalid Studio TLS record." }
                network.put(frame)
                network.flip()
            }
            SSLEngineResult.Status.OK -> Unit
            else -> error("Studio TLS session ended.")
        }
    }

    private fun negotiate() {
        while (true) when (engine.handshakeStatus.name) {
            "NEED_WRAP" -> wrap(empty)
            "NEED_UNWRAP", "NEED_UNWRAP_AGAIN" -> unwrap()
            "NEED_TASK" -> engine.delegatedTask?.run() ?: error("Missing TLS task.")
            "FINISHED", "NOT_HANDSHAKING" -> return
            else -> error("Unexpected Studio TLS state.")
        }
    }

    fun exchange(message: String): Pair<String, String> {
        engine.beginHandshake()
        negotiate()
        check(engine.session.protocol == "TLSv1.3")
        val key = PeerCrypto.hex(engine.session.peerCertificates.first().publicKey.encoded)
        val src = ByteBuffer.wrap((message + "\n").toByteArray(Charsets.UTF_8))
        while (src.hasRemaining()) { negotiate(); wrap(src) }
        ws.waitForApproval()
        val reply = ByteArrayOutputStream()
        while (true) {
            if (application.position() == 0) { negotiate(); unwrap() }
            application.flip()
            while (application.hasRemaining()) {
                val next = application.get().toInt() and 0xff
                if (next == 10) return key to reply.toString("UTF-8")
                check(reply.size() < 16384) { "Studio reply too large." }
                reply.write(next)
            }
            application.clear()
        }
    }
}
