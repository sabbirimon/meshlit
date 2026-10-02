package com.meshlit.ui.v2.screens

import androidx.lifecycle.viewModelScope
import com.meshlit.inference.MetricsRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for the v2 `ClusterViewModel`. Confirms the
 * sealed UiState + lifecycle-aware collect pattern is wired
 * correctly:
 *  - `Loading` initial state transitions to `Ready` once the
 *    `MetricsRegistry.snapshot()` produces a non-empty reading.
 *  - `Ready.queueDepth` mirrors the snapshot's `queueDepth`.
 *  - `Ready.tokensTotal` mirrors the snapshot's
 *    `totalTokensGenerated`.
 *
 * The registry is a real `MetricsRegistry` — no mocking needed
 * because it has no Android dependency. The unit test exercises
 * the same polling loop the v2 ViewModel uses.
 *
 * The ViewModel runs a `while (true) { … delay(1000L) }` polling
 * loop on `init { … }` that never returns, so `runTest` waits
 * forever for the test scope to drain. The tests advance virtual
 * time past the delay to make each poll iteration run, then cancel
 * the ViewModel's `viewModelScope` so `runTest` can return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ClusterViewModelTest {

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
    fun `Loading → Ready on first snapshot`() = runTest(testDispatcher) {
        val registry = MetricsRegistry()
        val vm = ClusterViewModel(registry = registry)
        assertEquals(ClusterUiState.Loading, vm.uiState.value)
        // Advance past the first 1 s `delay(1000L)` so the loop's
        // first body runs and projects a `Ready` snapshot, then
        // `runCurrent()` flushes the state emission.
        advanceTimeBy(1001L)
        runCurrent()
        val state = vm.uiState.value as ClusterUiState.Ready
        assertEquals(0, state.queueDepth)
        assertEquals(0L, state.tokensTotal)
        vm.viewModelScope.cancel()
    }

    @Test
    fun `Ready surfaces queueDepth and tokensTotal`() = runTest(testDispatcher) {
        val registry = MetricsRegistry()
        val vm = ClusterViewModel(registry = registry)
        // First poll → Ready (empty registry).
        advanceTimeBy(1001L)
        runCurrent()
        // Drive the registry directly to simulate a workload.
        val token = registry.recordJobStart()
        registry.recordJobEnd(
            token = token,
            outcome = MetricsRegistry.JobOutcome.Success(tokens = 42, tokensPerSecond = 21f),
        )
        // Second poll → Ready reflects new metrics.
        advanceTimeBy(1001L)
        runCurrent()
        val state = vm.uiState.value as ClusterUiState.Ready
        assertEquals(0, state.queueDepth)
        assertEquals(42L, state.tokensTotal)
        vm.viewModelScope.cancel()
    }
}
