package com.meshlit.bots

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.operations.OperationsControl
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private val eventId = Regex("[A-Za-z0-9_-]{1,80}")
private const val maxBody = 8192
private const val maxPending = 32
private const val maxRecords = 256
private const val retentionSeconds = 86_400L

@Serializable internal data class BotEnvelope(val source: String, val id: String, val text: String) {
    fun validate() {
        require(source.matches(eventId) && id.matches(eventId))
        require(text.isNotBlank() && text.length <= 4_000 && text.none { it.isISOControl() && it !in "\n\r\t" })
    }
}
@Serializable internal data class BotInboxRecord(
    val source: String,
    val id: String,
    val digest: String,
    val receivedAt: Long,
    val text: String? = null,
)
internal enum class InboxDecision { QUEUED, DUPLICATE, CONFLICT, FULL }

internal class BotInboxQueue(
    records: List<BotInboxRecord>,
    private val persist: (List<BotInboxRecord>) -> Unit,
) {
    private var records = records.toList()
    private val mutable = MutableStateFlow(records.filter { it.text != null })
    val pending = mutable.asStateFlow()

    init {
        require(records.size <= maxRecords && mutable.value.size <= maxPending)
        require(records.map { it.source to it.id }.distinct().size == records.size)
        records.forEach {
            require(it.source.matches(eventId) && it.id.matches(eventId) && it.digest.matches(Regex("[0-9a-f]{64}")) && it.receivedAt >= 0)
            it.text?.let { text -> BotEnvelope(it.source, it.id, text).validate() }
        }
    }

    @Synchronized fun accept(event: BotEnvelope, now: Long): InboxDecision {
        event.validate()
        val retained = records.filter { it.text != null || now - it.receivedAt < retentionSeconds }
        val digest = MessageDigest.getInstance("SHA-256").digest(event.text.toByteArray()).hex()
        val prior = retained.firstOrNull { it.source == event.source && it.id == event.id }
        if (prior != null) return if (prior.digest == digest) InboxDecision.DUPLICATE else InboxDecision.CONFLICT
        if (retained.size >= maxRecords || retained.count { it.text != null } >= maxPending) return InboxDecision.FULL
        update(retained + BotInboxRecord(event.source, event.id, digest, now, event.text))
        return InboxDecision.QUEUED
    }

    @Synchronized fun dismiss(record: BotInboxRecord) {
        require(record.text != null && record in records) { "This event has already been removed" }
        update(records.map { if (it == record) it.copy(text = null) else it })
    }

    private fun update(next: List<BotInboxRecord>) {
        persist(next)
        records = next
        mutable.value = next.filter { it.text != null }
    }
}

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
private fun authenticated(key: String, timestamp: String?, signature: String?, bytes: ByteArray, now: Long): Boolean {
    if (timestamp == null || !timestamp.matches(Regex("[0-9]{1,12}")) ||
        timestamp.toLong() !in now - 300..now + 300 || signature == null || !signature.matches(Regex("[0-9a-f]{64}"))
    ) return false
    val mac = Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key.toByteArray(), "HmacSHA256")) }
    mac.update("$timestamp.".toByteArray())
    return MessageDigest.isEqual(mac.doFinal(bytes).hex().toByteArray(), signature.toByteArray())
}

internal class BotInboxListener(
    port: Int,
    private val key: String,
    private val queue: BotInboxQueue,
    private val authorize: () -> Unit,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) : NanoHTTPD("127.0.0.1", port) {
    init { setAsyncRunner(com.meshlit.util.BoundedHttpRunner(4, "meshlit-bot-http")) }
    private val slots = java.util.concurrent.Semaphore(2)
    @Volatile private var accepting = true
    fun disable() { accepting = false }

    override fun serve(session: IHTTPSession): Response {
        fun reply(status: Response.Status, value: String) =
            newFixedLengthResponse(status, "application/json", "{\"status\":\"$value\"}").apply { closeConnection(true) }
        if (session.uri != "/bot/inbox" || session.method != Method.POST || session.queryParameterString != null ||
            session.headers["origin"] != null
        ) return reply(Response.Status.FORBIDDEN, "forbidden")
        val size = session.headers["content-length"]?.toIntOrNull()
        if (size == null || size !in 1..maxBody || session.headers["transfer-encoding"] != null ||
            session.headers["content-type"]?.substringBefore(';') != "application/json"
        ) return reply(Response.Status.BAD_REQUEST, "invalid-body")
        if (!accepting || !slots.tryAcquire()) return reply(Response.Status.SERVICE_UNAVAILABLE, "unavailable")
        try {
            try { authorize() } catch (_: Exception) { return reply(Response.Status.FORBIDDEN, "disabled") }
            val bytes = ByteArray(size)
            var count = 0
            while (count < size) {
                val read = session.inputStream.read(bytes, count, size - count)
                if (read <= 0) return reply(Response.Status.BAD_REQUEST, "incomplete")
                count += read
            }
            val now = clock()
            if (!authenticated(key, session.headers["x-meshlit-timestamp"], session.headers["x-meshlit-signature"], bytes, now))
                return reply(Response.Status.UNAUTHORIZED, "invalid-signature")
            val body = bytes.toString(Charsets.UTF_8)
            if (!body.toByteArray(Charsets.UTF_8).contentEquals(bytes)) return reply(Response.Status.BAD_REQUEST, "invalid-encoding")
            val event = try { Json.decodeFromString<BotEnvelope>(body).also { it.validate() } }
            catch (_: IllegalArgumentException) { return reply(Response.Status.BAD_REQUEST, "invalid-event") }
            if (!accepting) return reply(Response.Status.SERVICE_UNAVAILABLE, "unavailable")
            try { authorize() } catch (_: Exception) { return reply(Response.Status.FORBIDDEN, "disabled") }
            return when (queue.accept(event, now)) {
                InboxDecision.QUEUED -> reply(Response.Status.ACCEPTED, "queued")
                InboxDecision.DUPLICATE -> reply(Response.Status.OK, "duplicate")
                InboxDecision.CONFLICT -> reply(Response.Status.CONFLICT, "conflict")
                InboxDecision.FULL -> reply(Response.Status.SERVICE_UNAVAILABLE, "inbox-full")
            }
        } catch (_: Exception) {
            return reply(Response.Status.SERVICE_UNAVAILABLE, "unavailable")
        } finally { slots.release() }
    }
}

internal class BotInbox(private val context: Context) {
    private val gate = OperationsControl.get(context).gate
    private val storage = EncryptedCredentialStore(context, "bot-inbox-v1")
    private val queue by lazy {
        runCatching {
            val records = storage.get("events")?.let { Json.decodeFromString<List<BotInboxRecord>>(it) }.orEmpty()
            BotInboxQueue(records) { rows ->
                storage.putCommitted("events", Json.encodeToString(rows))
            }
        }
    }
    private val unavailable = MutableStateFlow<List<BotInboxRecord>>(emptyList()).asStateFlow()
    val pending get() = queue.getOrNull()?.pending ?: unavailable
    private val mutable = MutableStateFlow("Stopped")
    val status = mutable.asStateFlow()
    private var server: BotInboxListener? = null
    val port = 18896

    @Synchronized fun secret(): String = storage.get("secret") ?: ByteArray(32)
        .also { SecureRandom().nextBytes(it) }.hex().also { storage.putCommitted("secret", it) }

    @Synchronized fun requestStart() {
        requireAllowed()
        check(queue.isSuccess) { "Cannot read the encrypted bot inbox" }
        if (server != null || mutable.value == "Starting") return
        check(mutable.value != "Stopping") { "Wait for the receiver to stop" }
        mutable.value = "Starting"
        try { ContextCompat.startForegroundService(context, Intent(context, BotInboxService::class.java)) }
        catch (error: Exception) { mutable.value = "Could not start"; throw error }
    }

    @Synchronized fun open() {
        requireAllowed()
        check(mutable.value == "Starting") { "Start was cancelled" }
        val next = BotInboxListener(port, secret(), queue.getOrThrow(), ::requireAllowed)
        try {
            next.start(4_000, false)
            server = next
            mutable.value = "Listening on 127.0.0.1:$port"
        } catch (error: Exception) {
            next.disable(); next.stop()
            mutable.value = "Listener could not bind"
            throw error
        }
    }

    fun closed() {
        val current = synchronized(this) {
            val old = server ?: run {
                if (mutable.value == "Starting") mutable.value = "Stopped"
                return
            }
            server = null
            old.disable()
            mutable.value = "Stopping"
            old
        }
        try { current.stop() }
        finally { synchronized(this) { mutable.value = "Stopped" } }
    }

    fun stop() {
        closed()
        context.stopService(Intent(context, BotInboxService::class.java))
    }

    fun rotate() {
        stop()
        synchronized(this) {
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }.hex()
            storage.putCommitted("secret", key)
        }
    }

    fun dismiss(record: BotInboxRecord) = queue.getOrThrow().dismiss(record)

    private fun requireAllowed() {
        check(!com.meshlit.BuildProfile.coreCandidate && !com.meshlit.BuildConfig.PLAY_REVIEW)
        gate.requireAllowed(ManagedFeature.AUTOMATION)
    }
}
