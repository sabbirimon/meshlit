package com.meshlit.core.inference.pipeline
import org.junit.Test
import org.junit.Assert.*
import java.net.*
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.*
import java.util.concurrent.Executors
class RpcTunnelTest {
    private fun unusedPort()=ServerSocket(0).use{it.localPort}
    private fun bindTestTunnel(tls:SSLContext,nativePort:Int,token:String,networkAllowed:(String)->Boolean={true},authenticateFirst:Boolean=false):RpcTunnel {
        // The API takes an explicit public port. Reserving then releasing an
        // ephemeral test port cannot reserve it across the actual TLS bind.
        // Retry only allocation conflicts, retaining every TLS/auth assertion.
        var conflict:BindException?=null
        repeat(20) {
            try { return RpcTunnel.server(tls,unusedPort(),nativePort,token,capabilities={"{\"workerAllowed\":true}"},networkAllowed=networkAllowed,authenticateBeforeNetworkCheck=authenticateFirst) }
            catch(e:BindException) { conflict=e }
        }
        throw requireNotNull(conflict)
    }
    @Test fun authenticatedNetworkCheckDoesNotAdmitWrongTokenOrDeniedPolicy() {
        val fixture=requireNotNull(javaClass.getResourceAsStream("/pipeline-test-only.p12"))
        val keys=KeyStore.getInstance("PKCS12").apply{fixture.use{load(it,"test-only".toCharArray())}}
        val managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(keys,"test-only".toCharArray())}
        val tls=SSLContext.getInstance("TLS").apply{init(managers.keyManagers,null,null)}
        val pin=RpcTunnel.fingerprint(keys.getCertificate("worker") as X509Certificate)
        val token="t".repeat(64)
        val calls=java.util.concurrent.atomic.AtomicInteger()
        ServerSocket(0,4,InetAddress.getByName("127.0.0.1")).use { native ->
            native.soTimeout=300
            bindTestTunnel(tls,native.localPort,token,{calls.incrementAndGet();true},true).use { server ->
                assertTrue(runCatching{RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,"x".repeat(64))}.isFailure)
                assertEquals("Unverified clients must not receive paired trust",0,calls.get())
                assertEquals("{\"workerAllowed\":true}",RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,token))
                assertEquals(1,calls.get())
                assertTrue("Capabilities must not connect native RPC",runCatching{native.accept()}.exceptionOrNull() is SocketTimeoutException)
            }
            bindTestTunnel(tls,native.localPort,token,{false},true).use { server ->
                assertTrue("A valid token must not bypass listener policy",runCatching{RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,token)}.isFailure)
            }
            bindTestTunnel(tls,native.localPort,token,{false}).use { server ->
                assertTrue("Default listeners keep their pre-authentication deny",runCatching{RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,token)}.isFailure)
            }
        }
    }
    @Test fun authenticatedTunnelForwardsAndWrongPinFails() {
        // This keystore is a public, disposable TEST FIXTURE, never a deployment identity.
        val fixture=requireNotNull(javaClass.getResourceAsStream("/pipeline-test-only.p12"))
        val keys=KeyStore.getInstance("PKCS12").apply{fixture.use{load(it,"test-only".toCharArray())}}
        val managers=KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm()).apply{init(keys,"test-only".toCharArray())}
        val tls=SSLContext.getInstance("TLS").apply{init(managers.keyManagers,null,null)}
        val pin=RpcTunnel.fingerprint(keys.getCertificate("worker") as X509Certificate)
        val native=ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))
        val executor=Executors.newSingleThreadExecutor()
        val echo=executor.submit {native.accept().use{socket -> val data=ByteArray(4);java.io.DataInputStream(socket.inputStream).readFully(data);socket.outputStream.write(data);socket.outputStream.flush()}}
        val token="t".repeat(64)
        try { bindTestTunnel(tls,native.localPort,token).use { server ->
            assertEquals("{\"workerAllowed\":true}",RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,token))
            assertTrue(runCatching{RpcTunnel.queryCapabilities("127.0.0.1",server.port,"0".repeat(64),token)}.isFailure)
            assertTrue(runCatching{RpcTunnel.queryCapabilities("127.0.0.1",server.port,pin,"x".repeat(64))}.isFailure)
            RpcTunnel.client("127.0.0.1",server.port,pin,token).use { client ->
                Socket("127.0.0.1",client.port).use{socket -> socket.soTimeout=5000;socket.outputStream.write(byteArrayOf(1,2,3,4));socket.outputStream.flush()
                    val received=ByteArray(4);java.io.DataInputStream(socket.inputStream).readFully(received);assertArrayEquals(byteArrayOf(1,2,3,4),received)}
            }
        }
        echo.get(5,java.util.concurrent.TimeUnit.SECONDS)
        } finally {native.close();executor.shutdownNow()}
    }
}
