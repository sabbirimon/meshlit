package com.meshlit.core.common

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

/**
 * Phase 7 — :core-common JVM smoke test for [MeshlitError] and
 * [MeshlitResult]. The release checklist §1 gate lists
 * `:core-common:testDebugUnitTest`; before Phase 7 the only test in
 * the module was the `CapabilityMatrixTest`, so the error taxonomy
 * that every cluster operation returns had no JVM coverage. A
 * regression in error routing here would silently downgrade typed
 * failures to generic exceptions across `:core-inference`,
 * `:core-federation`, and the agent loop.
 */
class MeshlitErrorTest {

    @Test
    fun `MeshlitError tag is the stable telemetry identifier`() {
        val net = MeshlitError.Network("timeout:13s")
        assertEquals("timeout:13s", net.tag)
        val auth = MeshlitError.Auth("trust_denied:tier0")
        assertEquals("trust_denied:tier0", auth.tag)
    }

    @Test
    fun `MeshlitError preserves the cause chain`() {
        val cause = IOException("connection refused")
        val net = MeshlitError.Network("refused", cause)
        assertSame(cause, net.cause)
        assertEquals(net.cause, net.cause)
    }

    @Test
    fun `NodeGone encodes the vanished peer id in its tag`() {
        val gone = MeshlitError.NodeGone(nodeId = "peer-7B2C")
        assertEquals("node_gone:peer-7B2C", gone.tag)
    }

    @Test
    fun `Unknown wraps the cause class name in its tag`() {
        val cause = IllegalStateException("bad state")
        val unknown = MeshlitError.Unknown(cause)
        assertEquals("unknown:IllegalStateException", unknown.tag)
        assertSame(cause, unknown.cause)
    }

    @Test
    fun `MeshlitResult runCatching wraps thrown into Failure with Unknown`() {
        val boom = IllegalArgumentException("nope")
        val r = MeshlitResult.runCatching<String> { throw boom }
        assertTrue(r is MeshlitResult.Failure)
        val err = r.errorOrNull()
        assertNotNull(err)
        assertTrue(err is MeshlitError.Unknown)
        assertSame(boom, err!!.cause)
    }

    @Test
    fun `MeshlitResult runCatching wraps non-thrown into Success`() {
        val r = MeshlitResult.runCatching { "ok" }
        assertTrue(r is MeshlitResult.Success)
        assertEquals("ok", r.getOrNull())
    }

    @Test
    fun `Success getOrNull returns the value and Failure returns null`() {
        val ok: MeshlitResult<Int> = MeshlitResult.Success(42)
        val fail: MeshlitResult<Int> = MeshlitResult.Failure(MeshlitError.Invalid("bad"))
        assertEquals(42, ok.getOrNull())
        assertEquals(null, fail.getOrNull())
    }
}