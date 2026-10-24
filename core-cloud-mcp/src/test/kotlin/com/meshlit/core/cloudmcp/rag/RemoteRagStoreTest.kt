package com.meshlit.core.cloudmcp.rag

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test

class RemoteRagStoreTest {
    @Test fun unconfiguredBridgeNeverLooksUpCredentialsOrPretendsSuccess()=runBlocking {
        var credentialsRead=false
        val store=RemoteRagStore(OkHttpClient(),{_,_->credentialsRead=true;"secret"})
        try { store.query("qdrant","private",floatArrayOf(1f));fail("No configured bridge") }
        catch(e:IllegalStateException){assertTrue(e.message!!.contains("not configured"))}
        assertFalse(credentialsRead)
    }
    @Test fun insecureEndpointAndNonFiniteVectorsRejectBeforeCredentialAccess()=runBlocking {
        var credentialsRead=false
        val store=RemoteRagStore(OkHttpClient(),{_,_->credentialsRead=true;"secret"},{RagBridgeEndpoint("http://example.com","saved")})
        try { store.query("bridge","docs",floatArrayOf(Float.NaN));fail("NaN") } catch(_:IllegalArgumentException){}
        try { store.query("bridge","docs",floatArrayOf(1f));fail("HTTP") } catch(_:IllegalArgumentException){}
        assertFalse(credentialsRead)
    }
}
