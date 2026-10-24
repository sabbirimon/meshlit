package com.meshlit.core.trust

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContributionLedgerTest {
    private val owner = PeerCrypto.generate("host-1")

    @Test fun hexEncodingRemainsCanonical() {
        val bytes = byteArrayOf(0, 1, 15, 16, 127, -128, -1)
        assertEquals("00010f107f80ff", PeerCrypto.hex(bytes))
        assertTrue(bytes.contentEquals(PeerCrypto.bytes(PeerCrypto.hex(bytes))))
    }

    @Test fun signedChainAndDuplicateJob() {
        val receipts = (1..24).fold(emptyList<ContributionReceipt>()) { chain, n ->
            ContributionLedger.append(chain, owner, "job-$n", n)
        }
        assertTrue(ContributionLedger.verify(receipts, owner.nodeId, owner.publicKey))
        assertEquals(receipts, ContributionLedger.append(receipts, owner, "job-24", 999))
        assertEquals((1..24).sum().toLong(), receipts.sumOf { it.generatedTokens.toLong() })
    }

    @Test fun alteredAndWrongOwnerReceiptsAreRejected() {
        val first = ContributionLedger.append(emptyList(), owner, "job-1", 8)
        val second = ContributionLedger.append(first, owner, "job-2", 9)
        assertFalse(ContributionLedger.verify(second.drop(1), owner.nodeId, owner.publicKey))
        assertFalse(ContributionLedger.verify(second.updated(0) { it.copy(generatedTokens = 10) }, owner.nodeId, owner.publicKey))
        assertFalse(ContributionLedger.verify(second, "other-host", owner.publicKey))
        assertFalse(ContributionLedger.verify(second, owner.nodeId, PeerCrypto.generate("host-1").publicKey))
    }

    private fun <T> List<T>.updated(index: Int, change: (T) -> T): List<T> =
        toMutableList().also { it[index] = change(it[index]) }
}
