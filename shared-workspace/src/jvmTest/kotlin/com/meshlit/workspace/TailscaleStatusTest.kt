package com.meshlit.workspace

import kotlin.test.*

class TailscaleStatusTest {
    @Test fun parsesReportedStatusWithoutSecretsOrReachabilityClaims() {
        val s = TailscaleStatus.parse("""{"BackendState":"Running","TUN":true,"AuthURL":"secret-login-token","User":{"1":{"LoginName":"private@example.invalid"}},"TailscaleIPs":["100.64.0.1","fd7a:115c:a1e0::1"],"Self":{"HostName":"owner","Online":true},"Peer":{"node-key":{"HostName":"peer","Online":false,"TailscaleIPs":["100.64.0.2"]}},"ExitNodeStatus":{"Online":false,"TailscaleIPs":["100.64.0.2/32"]},"Health":[]}""", 100)
        assertEquals("Running", s.backend); assertEquals(false, s.exitNode!!.online)
        assertEquals(1, s.peers!!.size); assertEquals(0, s.healthIssues)
        assertFalse(s.toString().contains("secret-login")); assertFalse(s.toString().contains("private@example")); assertFalse(s.toString().contains("node-key"))
    }
    @Test fun missingFieldsStayUnknownAndUserspaceDoesNotClaimOsVpn() {
        val s = TailscaleStatus.parse("""{"BackendState":"Running","TUN":false}""", 100)
        assertNull(s.self); assertNull(s.peers); assertNull(s.healthIssues); assertTrue(s.summary.contains("userspace"))
        assertEquals("Sign in through Tailscale", TailscaleStatus.parse("""{"BackendState":"NeedsLogin"}""", 100).summary)
    }
    @Test fun malformedUnknownOversizedAndDeepResponsesFailClosed() {
        listOf("{}", """{"BackendState":"FutureState"}""", """{"BackendState":"Running","TUN":"true"}""",
            """{"BackendState":"Running","TailscaleIPs":["host.invalid"]}""", """{"BackendState":"Running","TailscaleIPs":["999.1.1.1"]}""",
            "[".repeat(40)+"]".repeat(40), " ".repeat(TailscaleStatus.MAX_BYTES+1)).forEach { assertFails { TailscaleStatus.parse(it, 100) } }
    }
    @Test fun androidVpnAndInstalledAppDoNotImplyProviderIdentity() {
        val observation = VpnNetworkObservation(100, true, true, false, true, "tun0", listOf("100.64.0.1"), true)
        assertEquals("VPN active for Meshlit", observation.summary)
        assertTrue(observation.provider.contains("unavailable"))
        assertEquals("No VPN on Meshlit's active network", observation.copy(vpnForApp = false).summary)
        assertEquals("Network status unavailable", observation.copy(hasNetwork = null).summary)
        assertEquals("No active network for Meshlit", observation.copy(hasNetwork = false).summary)
    }
    @Test fun staleAndFutureObservationsCannotAppearFresh() {
        assertTrue(vpnSnapshotIsFresh(100, 101)); assertFalse(vpnSnapshotIsFresh(100, 60101)); assertFalse(vpnSnapshotIsFresh(101, 100))
    }
}
