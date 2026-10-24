package com.meshlit.core.inference.pipeline

import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.cert.X509Certificate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.Semaphore
import javax.net.ssl.*

/** Native GGML RPC stays on loopback. This transport encrypts peer traffic,
 * pins the approved certificate and authenticates clients before forwarding.
 * It is not a sandbox for the upstream experimental RPC interpreter. */
class RpcTunnel private constructor(
    private val listener: ServerSocket,
    private val connect: () -> Socket,
    private val authenticate: (Socket) -> Unit,
    private val onError: (String) -> Unit,
) : Closeable {
    val port get()=listener.localPort
    private val executor=Executors.newFixedThreadPool(12)
    private val sockets=ConcurrentHashMap.newKeySet<Socket>()
    private val limit=Semaphore(4)
    @Volatile private var closed=false
    init {
        executor.submit {
            while(!closed) {
                val incoming=try {listener.accept()} catch(e:Exception) { break }
                if(!limit.tryAcquire()) {incoming.close();continue}
                sockets.add(incoming)
                executor.submit {
                    var remote:Socket?=null
                    try {
                        incoming.soTimeout=10000
                        authenticate(incoming)
                        remote=connect();sockets.add(remote)
                        incoming.soTimeout=120000;remote.soTimeout=120000
                        val target=remote
                        val reverse=executor.submit {
                            try {target.getInputStream().copyTo(incoming.getOutputStream(),64*1024)}
                            finally {runCatching{incoming.close()};runCatching{target.close()}}
                        }
                        try {incoming.getInputStream().copyTo(target.getOutputStream(),64*1024)}
                        finally {runCatching{incoming.close()};runCatching{target.close()};reverse.cancel(true)}
                    } catch(e:Exception) {if(!closed && e !is CapabilityComplete) onError(if(e is IllegalArgumentException && e.message=="Network policy denied") "RPC channel denied by listener firewall" else if(e is SSLHandshakeException) "RPC TLS handshake failed: ${e.message?.take(250)}" else "RPC channel closed: ${e.javaClass.simpleName}")}
                    finally {
                        sockets.remove(incoming);runCatching{incoming.close()}
                        remote?.let {sockets.remove(it);runCatching{it.close()}}
                        limit.release()
                    }
                }
            }
        }
    }
    override fun close() {
        closed=true;runCatching{listener.close()};sockets.forEach{runCatching{it.close()}};sockets.clear();executor.shutdownNow()
    }
    companion object {
        private const val MAGIC="MESHLIT_LAYER_RPC/1"
        private const val CAPS="MESHLIT_LAYER_CAPS/1"
        private class CapabilityComplete: Exception()
        fun fingerprint(cert:X509Certificate):String=MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            .joinToString(""){"%02x".format(it)}
        fun server(context:SSLContext, publicPort:Int, nativePort:Int,token:String,onError:(String)->Unit={},capabilities:()->String={"{}"},networkAllowed:(String)->Boolean={true}):RpcTunnel {
            validateToken(token)
            require(publicPort in 1024..65535 && nativePort in 1024..65535 && publicPort!=nativePort)
            val listener=context.serverSocketFactory.createServerSocket(publicPort,4) as SSLServerSocket
            listener.enabledProtocols=listener.supportedProtocols.filter {it in setOf("TLSv1.2","TLSv1.3")}.toTypedArray()
            return RpcTunnel(listener,connect={Socket().apply{connect(java.net.InetSocketAddress("127.0.0.1",nativePort),5000)}},
                authenticate={incoming ->
                    require(networkAllowed(incoming.inetAddress.hostAddress ?: "")){"Network policy denied"}
                    (incoming as SSLSocket).startHandshake()
                    val input=DataInputStream(incoming.inputStream)
                    val protocol=readBounded(input)
                    require(protocol==MAGIC || protocol==CAPS) {"Protocol mismatch"}
                    require(MessageDigest.isEqual(readBounded(input).toByteArray(),token.toByteArray())) {"Peer authentication rejected"}
                    DataOutputStream(incoming.outputStream).apply {
                        writeUTF("OK")
                        if(protocol==CAPS) {val body=capabilities();require(body.toByteArray().size<=16000);writeUTF(body)}
                        flush()
                    }
                    if(protocol==CAPS) throw CapabilityComplete()
                },onError=onError)
        }
        fun client(host:String,port:Int,certificateSha256:String,token:String,onError:(String)->Unit={}):RpcTunnel {
            require(host.isNotBlank() && host.length<=253 && host.none {it.isWhitespace()} && port in 1024..65535)
            require(certificateSha256.matches(Regex("[a-fA-F0-9]{64}"))) {"Approve the worker certificate fingerprint first"}
            validateToken(token)
            val context=clientContext(certificateSha256)
            val listener=ServerSocket(0,4,InetAddress.getByName("127.0.0.1"))
            return RpcTunnel(listener,connect={
                val remote=context.socketFactory.createSocket() as SSLSocket
                try {
                    remote.enabledProtocols=remote.supportedProtocols.filter{it in setOf("TLSv1.2","TLSv1.3")}.toTypedArray()
                    remote.soTimeout=10000;remote.connect(java.net.InetSocketAddress(host,port),5000);remote.startHandshake()
                    val output=DataOutputStream(remote.outputStream)
                    output.writeUTF(MAGIC);output.writeUTF(token);output.flush()
                    require(readBounded(DataInputStream(remote.inputStream))=="OK") {"Worker rejected pairing"}
                    remote
                } catch(e:Exception) {remote.close();throw e}
            },authenticate={},onError=onError)
        }
        private fun clientContext(certificateSha256:String):SSLContext {
            require(certificateSha256.matches(Regex("[a-fA-F0-9]{64}")))
            val trust=object:X509TrustManager {
                override fun getAcceptedIssuers()=emptyArray<X509Certificate>()
                override fun checkClientTrusted(chain:Array<out X509Certificate>?,authType:String?)=throw java.security.cert.CertificateException("Client certificates unused")
                override fun checkServerTrusted(chain:Array<out X509Certificate>?,authType:String?) {
                    val cert=chain?.firstOrNull() ?: throw java.security.cert.CertificateException("Missing worker certificate")
                    cert.checkValidity()
                    if(!MessageDigest.isEqual(fingerprint(cert).lowercase().toByteArray(),certificateSha256.lowercase().toByteArray()))
                        throw java.security.cert.CertificateException("Worker certificate changed")
                }
            }
            return SSLContext.getInstance("TLS").apply{init(null,arrayOf(trust),null)}
        }
        fun queryCapabilities(host:String,port:Int,certificateSha256:String,token:String):String {
            validateToken(token)
            require(host.isNotBlank() && host.length<=253 && port in 1024..65535)
            val context=clientContext(certificateSha256)
            (context.socketFactory.createSocket() as SSLSocket).use { remote ->
                remote.soTimeout=10000
                remote.enabledProtocols=remote.supportedProtocols.filter{it in setOf("TLSv1.2","TLSv1.3")}.toTypedArray()
                remote.connect(java.net.InetSocketAddress(host,port),5000);remote.startHandshake()
                DataOutputStream(remote.outputStream).apply{writeUTF(CAPS);writeUTF(token);flush()}
                val input=DataInputStream(remote.inputStream)
                require(readBounded(input)=="OK")
                val size=input.readUnsignedShort();require(size in 2..16000)
                val bytes=ByteArray(size);input.readFully(bytes)
                // NodeOffer contains no non-ASCII user text; reject modified UTF ambiguity.
                require(bytes.all{it.toInt() in 32..126})
                return bytes.toString(Charsets.US_ASCII)
            }
        }
        private fun validateToken(token:String) {require(token.length in 32..128 && token.all {it.code in 33..126}) {"Invalid pairing token"}}
        private fun readBounded(input:DataInputStream):String {
            val size=input.readUnsignedShort();require(size in 1..256){"Invalid authentication frame"}
            val bytes=ByteArray(size);input.readFully(bytes)
            require(bytes.all {it.toInt() in 32..126}) {"Invalid authentication encoding"}
            return bytes.toString(Charsets.US_ASCII)
        }
    }
}
