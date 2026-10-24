package com.meshlit.core.observability

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class AuditJournalTest {
    class Store:AuditStorage { var data:String?=null;var fail=false
        override suspend fun read()=data
        override suspend fun write(jsonl:String){if(fail)error("disk failed");data=jsonl}
    }
    @Test fun retentionAndRestartAndFiltering()=runTest {
        val store=Store();val now=2_000_000_000L;val journal=AuditJournal(store){now};journal.open();journal.configure(100,1)
        journal.append((0..110).map{AuditRecord(timeMs=now-it,source=AuditSource.TASKS,action="task.updated",actor=AuditActor.HUMAN)}+AuditRecord(timeMs=now-86_400_001,source=AuditSource.TASKS,action="expired"))
        assertEquals(100,journal.records.value.size)
        val reopened=AuditJournal(store){now};reopened.open()
        assertEquals(journal.records.value,reopened.records.value)
        assertEquals(100,reopened.snapshot(AuditFilter(actor=AuditActor.HUMAN)).size)
        assertTrue(reopened.snapshot(AuditFilter(source=AuditSource.NETWORK)).isEmpty())
        reopened.clear();assertEquals("",store.data)
    }
    @Test fun savedLargerRetentionIsAppliedBeforeOpening()=runTest {
        val store=Store();val now=3_000_000_000L;val first=AuditJournal(store){now};first.open(5000,30)
        first.append((1..2500).map{AuditRecord(timeMs=now-8*86_400_000L-it,source=AuditSource.DEVICE,action="device.sample")})
        val reopened=AuditJournal(store){now};reopened.open(5000,30)
        assertEquals(2500,reopened.records.value.size)
        assertEquals(first.records.value,reopened.records.value)
    }
    @Test fun failedCommitDoesNotPublishSuccess()=runTest {
        val store=Store();val journal=AuditJournal(store);journal.open();store.fail=true
        try{journal.append(listOf(AuditRecord(source=AuditSource.AGENT,action="command.denied")));fail()}catch(_:IllegalStateException){}
        assertTrue(journal.records.value.isEmpty());assertTrue(journal.status.value.contains("failed"))
    }
    @Test fun denySecretsAndNonFiniteMeasurements() {
        val r=AuditRecord(source=AuditSource.INFERENCE,action="https://server/?token=secret",targetHash="password",measurements=mapOf("prompt" to 10.0,"bytes" to 2.0,"output_tokens" to Double.NaN))
        assertEquals("event.redacted",r.safe().action);assertNull(r.safe().targetHash)
        assertEquals(mapOf("bytes" to 2.0),r.safe().measurements)
        assertFalse(r.jsonLine().contains("secret"));assertEquals(r.safe(),AuditRecord.parse(r.jsonLine()))
        assertEquals(64,AuditRecord.hashTarget("local model").length)
        val privateId=r.copy(id="private identifier").safe()
        assertFalse(privateId.jsonLine().contains("private identifier"))
        assertEquals(privateId,privateId.safe())
        assertEquals(privateId,AuditRecord.parse(privateId.jsonLine()))
    }
    @Test fun endpointPreventsSecretUrlsAndRemoteCleartext() {
        listOf("http://cloud.example:4318","https://user:token@example.org","https://example.org/?token=x","https://example.org/#x").forEach { try{TelemetryPrivacy.endpoint(it);fail(it)}catch(_:IllegalArgumentException){} }
        assertEquals("https://example.org/otlp",TelemetryPrivacy.endpoint("https://example.org/otlp/"))
        assertEquals("http://127.0.0.1:4318",TelemetryPrivacy.endpoint("http://127.0.0.1:4318"))
    }
    @Test fun csvAndJsonRoundtripPreserveOnlyMetadata() {
        val r=AuditRecord(source=AuditSource.MODELS,action="download.completed",measurements=mapOf("bytes" to 1024.0))
        assertEquals(r,AuditRecord.parse(r.jsonLine()));assertTrue(AuditExport.csv(r).contains("download.completed"))
    }
}
