package com.meshlit.core.federation.recovery

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.security.*
import java.security.spec.X509EncodedKeySpec
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString
import java.util.UUID

/** Fixed membership, crash-fault consensus. No membership changes, leases or tool execution.
 * Every promise/accept/commit must be durably persisted before acknowledging it. */
@Serializable data class ReplicaMember(val id: String, val publicKey: String)
@Serializable data class ReplicaGroup(val id: String, val members: List<ReplicaMember>) {
    val quorum get() = members.size / 2 + 1
    fun validate() {
        require(id.matches(ID) && members.size in setOf(3, 5))
        require(members.map { it.id }.distinct().size == members.size)
        members.forEach { require(it.id.matches(ID)); publicKey(it.publicKey) }
    }
    fun fingerprint(): String = hash(Json.encodeToString(copy(members = members.sortedBy { it.id })))
}
@Serializable data class Ballot(val round: Long, val proposer: String) : Comparable<Ballot> {
    override fun compareTo(other: Ballot): Int = compareValuesBy(this, other, { it.round }, { it.proposer })
    fun validate() { require(round in 1..Long.MAX_VALUE - 1 && proposer.matches(ID)) }
}
@Serializable enum class JournalAction { ACQUIRE, SNAPSHOT, COMPLETE }
@Serializable enum class JournalContent { INFERENCE, JOB_METADATA }
@Serializable data class JournalEntry(
    val requestId: String, val taskId: String, val owner: String, val epoch: Long,
    val action: JournalAction, val offset: Long = 0, val modelSha256: String = "",
    val checkpointSha256: String = "", val replayText: String = "", val content: JournalContent = JournalContent.INFERENCE,
) {
    fun validate() {
        require(requestId.matches(ID) && taskId.matches(ID) && owner.matches(ID) && epoch > 0 && offset >= 0)
        require(modelSha256.isEmpty() || modelSha256.matches(SHA))
        require(checkpointSha256.isEmpty() || checkpointSha256.matches(SHA))
        require(replayText.toByteArray().size <= 8192)
        require(action != JournalAction.ACQUIRE || offset == 0L && replayText.isEmpty() && checkpointSha256.isEmpty() && modelSha256.isEmpty())
    }
}
@Serializable data class JournalValue(val slot: Int, val previous: String, val entry: JournalEntry) {
    fun digest(): String = hash(Json.encodeToString(this))
}
@Serializable data class ReplicaVote(val member: String, val signature: String)
@Serializable data class JournalCommit(val value: JournalValue, val ballot: Ballot, val votes: List<ReplicaVote>)
@Serializable data class AcceptedValue(val ballot: Ballot, val value: JournalValue)
@Serializable data class ReplicaState(
    val groupFingerprint: String, val commits: List<JournalCommit> = emptyList(),
    val promised: Ballot? = null, val accepted: AcceptedValue? = null,
)
@Serializable data class ReplicaRequest(
    val groupFingerprint: String, val method: String, val slot: Int = 0,
    val ballot: Ballot? = null, val value: JournalValue? = null, val commit: JournalCommit? = null,
)
@Serializable data class ReplicaReply(val member: String, val state: ReplicaState, val vote: ReplicaVote? = null)
interface ReplicaPersistence { fun load(): ReplicaState?; fun save(state: ReplicaState) }
fun interface ReplicaTransport { suspend fun call(member: ReplicaMember, request: ReplicaRequest): ReplicaReply }

private val ID = Regex("[A-Za-z0-9_-]{1,80}")
private val SHA = Regex("[a-f0-9]{64}")
private val codec = Json { ignoreUnknownKeys = false }
private fun hash(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
private fun publicKey(encoded: String): PublicKey {
    require(encoded.length <= 512)
    return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(requireNotNull(encoded.decodeBase64()).toByteArray()))
}
private fun voteBytes(group: ReplicaGroup, ballot: Ballot, value: JournalValue) =
    "meshlit-journal-1\n${group.fingerprint()}\n${ballot.round}\n${ballot.proposer}\n${value.digest()}".toByteArray()

fun ReplicaGroup.verify(commit: JournalCommit) {
    commit.ballot.validate(); commit.value.entry.validate()
    require(commit.value.slot in 0 until 128 && commit.value.previous.matches(SHA))
    require(commit.votes.size in quorum..members.size && commit.votes.map { it.member }.distinct().size == commit.votes.size)
    commit.votes.forEach { vote ->
        require(vote.signature.length <= 256)
        val member = members.single { it.id == vote.member }
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initVerify(publicKey(member.publicKey)); signature.update(voteBytes(this, commit.ballot, commit.value))
        require(signature.verify(requireNotNull(vote.signature.decodeBase64()).toByteArray())) { "Invalid durable acceptance signature" }
    }
}

/** Replayed state-machine validation also fences stale owners and terminal resurrection. */
fun validateAppend(commits: List<JournalCommit>, value: JournalValue) {
    value.entry.validate()
    require(value.slot == commits.size && value.slot < 128)
    require(value.previous == (commits.lastOrNull()?.value?.digest() ?: "0".repeat(64))) { "History mismatch" }
    require(commits.none { it.value.entry.requestId == value.entry.requestId }) { "Duplicate request ID" }
    val entry = value.entry
    val prior = commits.lastOrNull { it.value.entry.taskId == entry.taskId }?.value?.entry
    require(prior?.action != JournalAction.COMPLETE) { "Task is terminal" }
    when (entry.action) {
        JournalAction.ACQUIRE -> require(entry.epoch == (prior?.epoch ?: 0) + 1) { "Ownership epoch must advance" }
        else -> {
            require(prior != null && entry.owner == prior.owner && entry.epoch == prior.epoch) { "Stale task owner" }
            val checkpoint = commits.lastOrNull { it.value.entry.taskId == entry.taskId && it.value.entry.action != JournalAction.ACQUIRE }?.value?.entry
            require(entry.offset >= (checkpoint?.offset ?: 0)) { "Output offset regressed" }
            require(checkpoint?.modelSha256.isNullOrEmpty() || entry.modelSha256 == checkpoint?.modelSha256) { "Model identity changed" }
            require(checkpoint == null || entry.content == checkpoint.content) { "Journal content type changed" }
            if (entry.content == JournalContent.INFERENCE) require(entry.modelSha256.matches(SHA)) { "Inference snapshot needs a verified model identity" }
            else require(entry.modelSha256.isEmpty() && entry.checkpointSha256.isEmpty() && entry.offset == 0L) { "Job metadata must not claim model/KV/stream evidence" }
        }
    }
}

class ReplicaAcceptor(private val group: ReplicaGroup, private val memberId: String,
    private val signingKey: PrivateKey, private val persistence: ReplicaPersistence) {
    private var state: ReplicaState
    init {
        group.validate(); require(group.members.any { it.id == memberId })
        // Fail closed on corrupted/incompatible durable state, never restart with an empty promise.
        state = persistence.load() ?: ReplicaState(group.fingerprint())
        check(state.groupFingerprint == group.fingerprint())
        var prefix = emptyList<JournalCommit>()
        state.commits.forEach { group.verify(it); validateAppend(prefix, it.value); prefix = prefix + it }
        state.promised?.validate()
        state.accepted?.let { it.ballot.validate(); validateAppend(prefix, it.value); require(state.promised != null && state.promised!! >= it.ballot) }
        // Check private/public identity before accepting a request.
        val challenge = JournalValue(0, "0".repeat(64), JournalEntry("identity", "identity", memberId, 1, JournalAction.ACQUIRE))
        val b = Ballot(1, "identity")
        val s = sign(b, challenge)
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey(group.members.single { it.id == memberId }.publicKey)); verifier.update(voteBytes(group, b, challenge))
        check(verifier.verify(requireNotNull(s.signature.decodeBase64()).toByteArray()))
    }
    private fun persist(next: ReplicaState) { require(codec.encodeToString(next).toByteArray().size <= 2 * 1024 * 1024); persistence.save(next); state = next }
    private fun sign(ballot: Ballot, value: JournalValue): ReplicaVote {
        val signature = Signature.getInstance("SHA256withECDSA")
        signature.initSign(signingKey); signature.update(voteBytes(group, ballot, value))
        return ReplicaVote(memberId, signature.sign().toByteString().base64())
    }
    @Synchronized fun handle(request: ReplicaRequest): ReplicaReply {
        require(request.groupFingerprint == group.fingerprint())
        when (request.method) {
            "status" -> return ReplicaReply(memberId, state)
            "commit" -> {
                val commit = requireNotNull(request.commit); group.verify(commit)
                if (commit.value.slot < state.commits.size) {
                    require(state.commits[commit.value.slot].value == commit.value); return ReplicaReply(memberId, state)
                }
                validateAppend(state.commits, commit.value)
                persist(state.copy(commits = state.commits + commit, promised = null, accepted = null))
            }
            "prepare", "accept" -> {
                require(request.slot == state.commits.size && request.slot < 128)
                val ballot = requireNotNull(request.ballot); ballot.validate()
                require(state.promised == null || ballot >= state.promised!!) { "Higher ballot promised" }
                if (request.method == "prepare") persist(state.copy(promised = ballot))
                else {
                    val value = requireNotNull(request.value); validateAppend(state.commits, value)
                    require(state.accepted?.ballot != ballot || state.accepted?.value == value) { "Ballot reused" }
                    persist(state.copy(promised = ballot, accepted = AcceptedValue(ballot, value)))
                    return ReplicaReply(memberId, state, sign(ballot, value))
                }
            }
            else -> throw IllegalArgumentException("Unknown replica method")
        }
        return ReplicaReply(memberId, state)
    }
}

/** A fresh proposer identity per client prevents ballot reuse after restart. Calls are bounded;
 * loss of quorum fails visibly. A chosen value from an interrupted proposer is adopted first. */
class ReplicaJournal(private val group: ReplicaGroup, private val transport: ReplicaTransport) {
    private val proposer = UUID.randomUUID().toString()
    private val mutex = Mutex()
    private var round = 0L
    private suspend fun calls(request: ReplicaRequest): List<ReplicaReply> = coroutineScope {
        group.members.map { member -> async {
            try { withTimeout(5000) { transport.call(member, request).also {
                require(it.member == member.id && it.state.groupFingerprint == group.fingerprint())
            } } } catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e; null }
            catch (_: Exception) { null }
        } }.awaitAll().filterNotNull()
    }
    private fun request(method: String, slot: Int = 0, ballot: Ballot? = null, value: JournalValue? = null, commit: JournalCommit? = null) =
        ReplicaRequest(group.fingerprint(), method, slot, ballot, value, commit)
    private suspend fun synchronize(): List<JournalCommit> {
        val states = calls(request("status")); check(states.size >= group.quorum) { "Recovery blocked: no replica quorum" }
        val prefix = states.maxBy { it.state.commits.size }.state.commits
        var verified = emptyList<JournalCommit>()
        prefix.forEach { group.verify(it); validateAppend(verified, it.value); verified = verified + it }
        states.forEach { reply ->
            require(reply.state.commits.size <= prefix.size)
            require(reply.state.commits.map { it.value } == prefix.take(reply.state.commits.size).map { it.value }) { "Conflicting committed history" }
            for (commit in prefix.drop(reply.state.commits.size)) {
                // Sync only authenticated, majority-signed commits. Offline peers catch up on rejoin.
                try { withTimeout(5000) { transport.call(group.members.single { it.id == reply.member }, request("commit", commit = commit)) } }
                catch (e: CancellationException) { if (e !is TimeoutCancellationException) throw e }
                catch (_: Exception) { }
            }
        }
        round = maxOf(round, states.maxOf { it.state.promised?.round ?: 0 })
        return prefix
    }
    suspend fun read(): List<JournalCommit> = mutex.withLock { synchronize() }
    suspend fun append(entry: JournalEntry): JournalCommit = mutex.withLock {
        group.validate(); entry.validate()
        withTimeout(60000) {
            repeat(8) {
                val prefix = synchronize()
                prefix.firstOrNull { it.value.entry.requestId == entry.requestId }?.let { existing ->
                    require(existing.value.entry == entry) { "Request ID reused" }; return@withTimeout existing
                }
                val desired = JournalValue(prefix.size, prefix.lastOrNull()?.value?.digest() ?: "0".repeat(64), entry)
                // Recover a previously chosen value before validating a fresh stale-owner request.
                val ballot = Ballot(Math.addExact(round, 1).also { round = it }, proposer)
                val promises = calls(request("prepare", prefix.size, ballot)).filter { it.state.promised == ballot }
                if (promises.size < group.quorum) return@repeat
                val value = promises.mapNotNull { it.state.accepted }.maxByOrNull { it.ballot }?.value ?: desired
                validateAppend(prefix, value)
                val accepts = calls(request("accept", prefix.size, ballot, value)).mapNotNull { it.vote }
                if (accepts.size < group.quorum) return@repeat
                val commit = JournalCommit(value, ballot, accepts); group.verify(commit)
                // Acceptance means chosen, but report success only after durable learner quorum.
                val learned = calls(request("commit", commit = commit)).count { it.state.commits.getOrNull(value.slot)?.value == value }
                check(learned >= group.quorum) { "Commit acknowledgement uncertain; retry same request ID" }
                if (value == desired) return@withTimeout commit
            }
            error("Recovery contention; inspect journal and retry same request ID")
        }
    }
}
