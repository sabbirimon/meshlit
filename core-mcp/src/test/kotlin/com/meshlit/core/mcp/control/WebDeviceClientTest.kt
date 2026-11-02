package com.meshlit.core.mcp.control
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import okhttp3.tls.HeldCertificate
import okhttp3.tls.HandshakeCertificates
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest
class WebDeviceClientTest {
    @Test fun onlyCleanHttpsEndpointsAndValidPinsAreAccepted() {
        for(url in listOf("http://example.com","https://user:pass@example.com","https://example.com/?token=x","https://example.com/path"))assertThrows(IllegalArgumentException::class.java){WebDeviceEndpoint(url)}
        assertThrows(IllegalArgumentException::class.java){WebDeviceEndpoint("https://example.com","bad")}
        assertEquals("https://example.com/api/v1/resources",WebDeviceEndpoint("https://example.com").route("/api/v1/resources"))
    }
    @Test fun pinnedHttpsPreservesBearerAndRejectsAnotherIdentity()=runBlocking {
        val cert=HeldCertificate.Builder().commonName("localhost").addSubjectAlternativeName("localhost").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val server=MockWebServer();server.useHttps(tls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("127.0.0.1"),0)
        try {
            val pin=MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded).joinToString(""){"%02x".format(it)}
            server.enqueue(MockResponse().setBody("{\"state\":\"APPROVED\"}"))
            val client=WebDeviceClient(WebDeviceEndpoint("https://127.0.0.1:${server.port}/",pin))
            assertEquals("APPROVED",client.request("/api/v1/status",token="a".repeat(64))["state"]!!.toString().trim('"'))
            assertEquals("Bearer "+"a".repeat(64),server.takeRequest().getHeader("Authorization"))
            try{WebDeviceClient(WebDeviceEndpoint("https://127.0.0.1:${server.port}/","0".repeat(64))).request("/api/v1/status");fail("Wrong pin accepted")}catch(_:javax.net.ssl.SSLException){}
        }finally{server.shutdown()}
    }
    @Test fun pinnedIpv6NormalizesLiteralAddressesWithoutWeakeningIdentity()=runBlocking {
        val cert=HeldCertificate.Builder().commonName("IPv6 fixture").addSubjectAlternativeName("::1").build()
        val tls=HandshakeCertificates.Builder().heldCertificate(cert).build()
        val server=MockWebServer();server.useHttps(tls.sslSocketFactory(),false);server.start(java.net.InetAddress.getByName("::1"),0)
        try {
            val pin=MessageDigest.getInstance("SHA-256").digest(cert.certificate.encoded).joinToString(""){"%02x".format(it)}
            for(host in listOf("::1","0:0:0:0:0:0:0:1")) {
                server.enqueue(MockResponse().setBody("{\"state\":\"APPROVED\"}"))
                val client=WebDeviceClient(WebDeviceEndpoint("https://[$host]:${server.port}",pin))
                assertEquals("APPROVED",client.request("/api/v1/status",token="a".repeat(64))["state"]!!.toString().trim('"'))
                assertEquals("Bearer "+"a".repeat(64),server.takeRequest().getHeader("Authorization"))
            }
            try{WebDeviceClient(WebDeviceEndpoint("https://[::1]:${server.port}","0".repeat(64))).request("/api/v1/status");fail("Wrong IPv6 pin accepted")}catch(_:javax.net.ssl.SSLException){}
        }finally{server.shutdown()}
    }

}
