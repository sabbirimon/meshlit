package com.meshlit.core.inference

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4.x — regression tests for the cold-start gap fix.
 *
 * The user reported "no model despite active" — an `/v1/infer`
 * request fired during the 10-20 s `WarmingUp` window was
 * bouncing with `coord.inference.not_loaded`. The coordinator
 * now waits up to 30 s for `Ready` before failing. These tests
 * pin the new behaviour of [waitForCoordinatorReady].
 */
class CoordinatorStateWaitForReadyTest {

    @Test
    fun `idle returns false immediately`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(CoordinatorState.Idle)
        val result = state.waitForCoordinatorReady(timeoutMs = 30_000L)
        assertFalse(result)
    }

    @Test
    fun `ready returns true immediately`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(CoordinatorState.Ready(fakeModel()))
        assertTrue(state.waitForCoordinatorReady(timeoutMs = 30_000L))
    }

    @Test
    fun `error returns false immediately`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(CoordinatorState.Error("boom"))
        assertFalse(state.waitForCoordinatorReady(timeoutMs = 30_000L))
    }

    @Test
    fun `generating returns false immediately`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(
            CoordinatorState.Generating(startedAtMs = 1L),
        )
        assertFalse(state.waitForCoordinatorReady(timeoutMs = 30_000L))
    }

    @Test
    fun `loading transitions to ready returns true`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(
            CoordinatorState.Loading("/tmp/fake.gguf"),
        )
        backgroundScope.launch {
            delay(10)
            state.value = CoordinatorState.Ready(fakeModel())
        }
        assertTrue(state.waitForCoordinatorReady(timeoutMs = 5_000L))
    }

    @Test
    fun `warmingUp transitions to ready returns true the cold start gap fix`() = runTest {
        // Exact cold-start scenario: FGS auto-load is mid-warm-up
        // when `/v1/infer` lands.
        val state = MutableStateFlow<CoordinatorState>(
            CoordinatorState.WarmingUp("/tmp/fake.gguf"),
        )
        backgroundScope.launch {
            delay(10)
            state.value = CoordinatorState.Ready(fakeModel())
        }
        assertTrue(state.waitForCoordinatorReady(timeoutMs = 30_000L))
    }

    @Test
    fun `loading transitions to error returns false`() = runTest {
        val state = MutableStateFlow<CoordinatorState>(
            CoordinatorState.Loading("/tmp/fake.gguf"),
        )
        backgroundScope.launch {
            delay(10)
            state.value = CoordinatorState.Error("native load failed")
        }
        assertFalse(state.waitForCoordinatorReady(timeoutMs = 5_000L))
    }
}

private fun fakeModel(): ModelInfo = ModelInfo(
    modelPath = "/tmp/fake.gguf",
    modelName = "fake",
    contextSize = 4096,
    parameterCount = 1_000L,
    quantization = "Q8_0",
    embeddingDim = 1024,
    sizeBytes = 1_000_000L,
    loadedAtMs = 0L,
)
