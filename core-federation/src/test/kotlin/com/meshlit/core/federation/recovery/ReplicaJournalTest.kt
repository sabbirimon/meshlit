package com.meshlit.core.federation.recovery

import kotlinx.coroutines.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import java.security.KeyPairGenerator
import java.util.Base64

class ReplicaJournalTest {
    private class Disk : ReplicaPersistence {
        var bytes: String? = null; var fail = false
        override fun load() = bytes?.let { Json.decodeFromString<ReplicaState>(it) }
        override fun save(state: ReplicaState) { check(!fail); bytes = Json.encodeToString(state) }
    }
    private class Cluster(size: Int = 3) {
        val keys = (1..size).map { KeyPairGenerator.getInstance("EC").apply { initialize(256) }.generateKeyPair() }
        val group = ReplicaGroup("test", keys.mapIndexed { i, key -> ReplicaMember("n$i", Base64.getEncoder().encodeToString(key.public.encoded)) })
        val disks = keys.map { Disk() }
        val nodes = keys.mapIndexed { i, key -> ReplicaAcceptor(group, "n$i", key.private, disks[i]) }.toMutableList()
        val offline = mutableSetOf<String>()
        var loseCommit = false
        val transport = ReplicaTransport { member, request ->
            check(member.id !in offline)
            check(!loseCommit || request.method != "commit")
            nodes[member.id.drop(1).toInt()].handle(request)
        }
        fun client() = ReplicaJournal(group, transport)
        fun restart(i: Int) { nodes[i] = ReplicaAcceptor(group, "n$i", keys[i].private, disks[i]) }
    }
    private fun acquire(id: String = "a", owner: String = "first", epoch: Long = 1) = JournalEntry(id, "task", owner, epoch, JournalAction.ACQUIRE)
    private fun snapshot(id: String = "s", owner: String = "first", epoch: Long = 1, offset: Long = 5) =
        JournalEntry(id, "task", owner, epoch, JournalAction.SNAPSHOT, offset, "a".repeat(64), replayText = "real persisted replay input")
    private suspend fun fails(block: suspend () -> Unit) { var failed = false; try { block() } catch (_: Exception) { failed = true }; assertTrue(failed) }

    @Test fun durableRestartAndOneFailure() = runBlocking {
        val c = Cluster(); c.client().append(acquire()); c.client().append(snapshot())
        c.restart(0); c.restart(1); c.offline.add("n2")
        assertEquals(5L, c.client().read().last().value.entry.offset)
        c.client().append(acquire("takeover", "second", 2))
        fails { c.client().append(snapshot("stale")) }
        c.client().append(snapshot("new", "second", 2, 6))
        assertEquals("second", c.client().read().last().value.entry.owner)
    }
    @Test fun noQuorumNeverReportsCommit() = runBlocking {
        val c = Cluster(); c.offline.addAll(listOf("n1", "n2")); fails { c.client().append(acquire()) }
        assertTrue(c.nodes[0].handle(ReplicaRequest(c.group.fingerprint(), "status")).state.commits.isEmpty())
    }
    @Test fun acceptedBeforeProposerCrashIsRecoveredFirst() = runBlocking {
        val c = Cluster(); c.loseCommit = true; fails { c.client().append(acquire()) }
        c.restart(0); c.restart(1); c.loseCommit = false
        c.client().append(snapshot())
        assertEquals(listOf("a", "s"), c.client().read().map { it.value.entry.requestId })
    }
    @Test fun rejoiningOldNodeCannotResurrectCompletedTask() = runBlocking {
        val c = Cluster(); c.offline.add("n2"); c.client().append(acquire())
        val complete = snapshot().copy(action = JournalAction.COMPLETE)
        c.client().append(complete); c.offline.clear(); c.client().read()
        assertEquals(2, c.nodes[2].handle(ReplicaRequest(c.group.fingerprint(), "status")).state.commits.size)
        fails { c.client().append(acquire("resurrect", "third", 2)) }
    }
    @Test fun duplicateIdIsIdempotentButDifferentContentRejected() = runBlocking {
        val c = Cluster(); val first = c.client().append(acquire()); assertEquals(first.value, c.client().append(acquire()).value)
        fails { c.client().append(acquire(owner = "other")) }
    }
    @Test fun storageFailureCannotProduceVote() = runBlocking {
        val c = Cluster(); c.disks[0].fail = true; c.disks[1].fail = true
        fails { c.client().append(acquire()) }; c.restart(0)
        assertTrue(c.nodes[0].handle(ReplicaRequest(c.group.fingerprint(), "status")).state.commits.isEmpty())
    }
    @Test fun tamperedCertificateOrHistoryRejected() = runBlocking {
        val c = Cluster(); val commit = c.client().append(acquire())
        fails { c.group.verify(commit.copy(value = commit.value.copy(entry = acquire(owner = "forged")))) }
        fails { c.group.verify(commit.copy(votes = listOf(commit.votes[0], commit.votes[0]))) }
        c.disks[0].bytes = c.disks[0].bytes!!.replace("\"owner\":\"first\"", "\"owner\":\"forged\"")
        fails { c.restart(0) }
    }
    @Test fun ballotPromiseSurvivesRestartAndRefusesOldProposal() = runBlocking {
        val c = Cluster(); val fp = c.group.fingerprint(); val newer = Ballot(5, "new")
        c.nodes[0].handle(ReplicaRequest(fp, "prepare", ballot = newer)); c.restart(0)
        fails { c.nodes[0].handle(ReplicaRequest(fp, "prepare", ballot = Ballot(4, "old"))) }
    }
    @Test fun modelAndOffsetCannotRegressAcrossOwnershipTransfer() = runBlocking {
        val c = Cluster(); c.client().append(acquire()); c.client().append(snapshot()); c.client().append(acquire("next", "second", 2))
        fails { c.client().append(snapshot("back", "second", 2, 4)) }
        fails { c.client().append(snapshot("wrong", "second", 2, 7).copy(modelSha256 = "b".repeat(64))) }
    }
    @Test fun fiveMembersTolerateTwoFailures() = runBlocking {
        val c = Cluster(5); c.offline.addAll(listOf("n3", "n4")); c.client().append(acquire()); c.client().append(snapshot())
        assertEquals(2, c.client().read().size)
    }
    @Test fun concurrentProposersNeverChooseDifferentHistory() = runBlocking {
        val c = Cluster(); coroutineScope {
            listOf("first", "second").map { name -> async { runCatching { c.client().append(acquire(name, name)) } } }.awaitAll()
        }
        val histories = c.nodes.map { it.handle(ReplicaRequest(c.group.fingerprint(), "status")).state.commits.map { e -> e.value } }
        assertEquals(1, histories.toSet().size); assertEquals(1, histories.first().size)
    }
    @Test fun cancellationIsNotConvertedToUnreachablePeer() = runBlocking {
        val c = Cluster(); val journal = ReplicaJournal(c.group, ReplicaTransport { _, _ -> throw CancellationException("stop") })
        try { journal.read(); fail("Cancellation expected") } catch (_: CancellationException) { }
    }
    @Test fun jobMetadataDoesNotPretendToBeModelOrKvEvidence() = runBlocking {
        val c = Cluster(); c.client().append(acquire())
        c.client().append(snapshot().copy(modelSha256 = "", offset = 0, content = JournalContent.JOB_METADATA))
        fails { c.client().append(snapshot("invalid").copy(content = JournalContent.JOB_METADATA)) }
        fails { c.client().append(snapshot("switch")) }
    }
}
