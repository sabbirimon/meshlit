package com.meshlit.core.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * mDNS / DNS-SD transport built on `android.net.nsd.NsdManager`.
 *
 * Service type: `_meshlit._tcp.` (the `_tcp` is mandatory for NSD and
 * the underscore-prefixed `_meshlit` makes the service name a
 * well-known discovery key for any Meshlit device on the LAN).
 *
 * Each advertisement is published as a TXT record containing the
 * JSON-serialized [PeerAdvertisement]. Receivers parse the TXT and
 * hand it to the [DiscoveryTransport.advertisements] flow.
 *
 * Lifecycle:
 *  - [start] registers the service AND begins discovery.
 *  - [stop] unregisters and tears down the discovery listener.
 *  - All callbacks are dispatched onto the calling coroutine's
 *    context so the SDK never touches the Android main thread
 *    directly outside the manager's own dispatch path.
 */
class NsdDiscoveryTransport(
    private val context: Context,
    private val serviceType: String = "_meshlit._tcp.",
) : DiscoveryTransport(name = "nsd") {

    private val log = logger("NsdDiscoveryTransport")

    private val nsdManager: NsdManager by lazy {
        context.getSystemService(Context.NSD_SERVICE) as NsdManager
    }

    private var registeredName: String? = null
    private var listener: NsdManager.DiscoveryListener? = null
    private var isRunning: Boolean = false
    private var callbackScope: CoroutineScope? = null

    @Volatile
    private var self: LocalPeerDescriptor? = null

    override fun start(scope: CoroutineScope, self: LocalPeerDescriptor): Job {
        if (isRunning) {
            log.warn("nsd.start.duplicate", "start() called twice without stop()")
            return scope.launch { /* no-op */ }
        }
        isRunning = true
        this.self = self
        this.callbackScope = scope
        return scope.launch(Dispatchers.IO) {
            registerService(self)
            startDiscovery()
        }
    }

    override fun stop() {
        if (!isRunning) return
        isRunning = false
        callbackScope = null
        listener?.let { l ->
            runCatching { nsdManager.stopServiceDiscovery(l) }
        }
        listener = null
        registeredName?.let { name ->
            runCatching { nsdManager.unregisterService(registrationListener) }
        }
        registeredName = null
    }

    private fun registerService(self: LocalPeerDescriptor) {
        val info = NsdServiceInfo().apply {
            serviceName = "meshlit-${self.nodeId.take(8)}"
            serviceType = this@NsdDiscoveryTransport.serviceType
            port = self.port
            // TXT record carries the JSON advertisement. NSD
            // requires keys/values as byte arrays.
            val json = Json.encodeToString(PeerAdvertisement.serializer(), toAdvertisement(self))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                setAttribute("meshlit", json)
            }
        }
        runCatching {
            nsdManager.registerService(info, NsdManager.PROTOCOL_DNS_SD, registrationListener)
        }.onFailure { t ->
            log.warn("nsd.register.fail", "registerService threw", mapOf("err" to (t.message ?: "")))
        }
    }

    private val registrationListener = object : NsdManager.RegistrationListener {
        override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
            registeredName = serviceInfo.serviceName
            log.info("nsd.registered", "service registered", mapOf("name" to serviceInfo.serviceName))
        }
        override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            log.warn("nsd.register.fail", "registration failed", mapOf("code" to errorCode))
        }
        override fun onServiceUnregistered(arg0: NsdServiceInfo) {
            log.info("nsd.unregistered", "service unregistered")
        }
        override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
            log.warn("nsd.unregister.fail", "unregistration failed", mapOf("code" to errorCode))
        }
    }

    private fun startDiscovery() {
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                log.info("nsd.discover.start", "discovery started", mapOf("type" to regType))
            }
            override fun onServiceFound(service: NsdServiceInfo) {
                if (service.serviceType != serviceType) return
                resolve(service)
            }
            override fun onServiceLost(service: NsdServiceInfo) {
                log.info("nsd.lost", "service lost", mapOf("name" to service.serviceName))
            }
            override fun onDiscoveryStopped(serviceType: String) {
                log.info("nsd.discover.stop", "discovery stopped", mapOf("type" to serviceType))
            }
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                log.warn("nsd.discover.fail", "startDiscovery failed", mapOf("type" to serviceType, "code" to errorCode))
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                log.warn("nsd.discover.stopfail", "stopDiscovery failed", mapOf("type" to serviceType, "code" to errorCode))
            }
        }
        listener = l
        runCatching {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, l)
        }.onFailure { t ->
            log.warn("nsd.discover.throw", "discoverServices threw", mapOf("err" to (t.message ?: "")))
        }
    }

    private fun resolve(service: NsdServiceInfo) {
        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                log.warn("nsd.resolve.fail", "resolve failed", mapOf("code" to errorCode, "name" to serviceInfo.serviceName))
            }
            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                val txt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    serviceInfo.attributes["meshlit"]?.let { String(it) }
                } else null
                val adv = parseAdvertisement(txt, serviceInfo)
                val scope = callbackScope
                if (adv != null && scope != null) {
                    scope.launch(Dispatchers.IO) { emit(adv) }
                }
            }
        }
        @Suppress("DEPRECATION")
        runCatching { nsdManager.resolveService(service, resolveListener) }
            .onFailure { t ->
                log.warn("nsd.resolve.throw", "resolveService threw", mapOf("err" to (t.message ?: "")))
            }
    }

    private fun parseAdvertisement(json: String?, info: NsdServiceInfo): PeerAdvertisement? {
        if (json.isNullOrBlank()) {
            // No TXT payload — synthesise a minimal advertisement.
            return PeerAdvertisement(
                nodeId = info.serviceName.removePrefix("meshlit-"),
                host = info.host?.hostAddress.orEmpty(),
                port = info.port,
                tier = "local_sandboxed",
                fingerprint = "",
                transport = "nsd",
            )
        }
        return runCatching {
            Json.decodeFromString(PeerAdvertisement.serializer(), json)
        }.getOrNull()
    }

    private fun toAdvertisement(self: LocalPeerDescriptor): PeerAdvertisement = PeerAdvertisement(
        nodeId = self.nodeId,
        host = self.host,
        port = self.port,
        tier = self.tierTag,
        fingerprint = self.fingerprint,
        transport = "nsd",
    )
}
