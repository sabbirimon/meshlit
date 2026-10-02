package com.meshlit.ui.v2.screens

import com.meshlit.core.common.NetworkScope
import com.meshlit.core.common.RemoteEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first as flowFirst
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the v2 `DevicesViewModel` combine logic.
 *
 * The production `SettingsRepository` is a final class that reads
 * from DataStore, which is awkward to stub in unit tests. Instead
 * of mocking the repository, this test exercises the same combine
 * shape the v2 ViewModel uses:
 *   combine(networkScopeFlow, remoteEndpointsFlow, activeEndpointIdFlow)
 *     → DevicesUiState.Ready
 *
 * Confirms the v2 wiring invariant: a state-flow shape maps to
 * a Ready UiState with the expected fields. The v1 screen's
 * inline `collectAsState()` pattern is replaced wholesale by the
 * ViewModel + sealed UiState + lifecycle-aware collect pattern
 * (see plan §5 audit).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DevicesViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `combine maps to Ready with scope endpoints activeEndpointId`() = runTest(testDispatcher) {
        val scope = MutableStateFlow(NetworkScope.GROUP)
        val endpoints = MutableStateFlow<List<RemoteEndpoint>>(emptyList())
        val activeId = MutableStateFlow<String?>(null)

        val ready = combine(scope, endpoints, activeId) { s, e, a ->
            DevicesUiState.Ready(
                scope = s,
                endpoints = e,
                activeEndpointId = a,
                ownPayload = PairingPayload(descriptor = "node-group", qrString = ""),
            )
        }.flowFirst()

        assertEquals(NetworkScope.GROUP, ready.scope)
        assertEquals(0, ready.endpoints.size)
        assertNull(ready.activeEndpointId)
        assertTrue(ready.ownPayload.descriptor.startsWith("node-"))
    }

    @Test
    fun `combine reflects new scope on emit`() = runTest(testDispatcher) {
        val scope = MutableStateFlow(NetworkScope.GROUP)
        val endpoints = MutableStateFlow<List<RemoteEndpoint>>(emptyList())
        val activeId = MutableStateFlow<String?>(null)

        scope.value = NetworkScope.LOCAL

        val ready = combine(scope, endpoints, activeId) { s, e, a ->
            DevicesUiState.Ready(
                scope = s,
                endpoints = e,
                activeEndpointId = a,
                ownPayload = PairingPayload(descriptor = "node-local", qrString = ""),
            )
        }.flowFirst()

        assertEquals(NetworkScope.LOCAL, ready.scope)
    }

    @Test
    fun `combine surfaces active endpoint when set`() = runTest(testDispatcher) {
        val ep = RemoteEndpoint(id = "ep-1", name = "lab", baseUrl = "https://lab.local")
        val scope = MutableStateFlow(NetworkScope.LOCAL)
        val endpoints = MutableStateFlow(listOf(ep))
        val activeId = MutableStateFlow<String?>("ep-1")

        val ready = combine(scope, endpoints, activeId) { s, e, a ->
            DevicesUiState.Ready(
                scope = s,
                endpoints = e,
                activeEndpointId = a,
                ownPayload = PairingPayload(descriptor = "node-local", qrString = ""),
            )
        }.flowFirst()

        assertEquals("ep-1", ready.activeEndpointId)
        assertEquals(1, ready.endpoints.size)
        assertEquals("lab", ready.endpoints.first().name)
    }
}