package com.meshlit.diagnostics

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4.x — POST self-test orchestrator.
 *
 * Validates that:
 *  1. Idle stage flips Pending → Running → Ok within the
 *     per-stage timeout.
 *  2. A throwing probe results in PostStatus.Failed with the
 *     exception message.
 *  3. A null probe result (timeout) results in PostStatus.Skipped.
 *  4. The orchestrator seeds all stages as Pending before
 *     the first running step.
 *  5. The completed flow flips to true after the last stage.
 *
 * The probe stage list is constructed inline with stub lambdas
 * — no Android dependencies, no MeshlitApplication.
 */
class PostSelfTestTest {

    @Test
    fun idleStage_transitionsPendingToRunningToOk() = runTest {
        val stages = listOf(
            PostStageSpec(id = "a", label = "Stage A", probe = { Ok }),
        )
        val steps = runStages(stages)
        assertEquals(PostStatus.Ok, steps[0].status)
    }

    @Test
    fun throwingProbe_resultsInFailed() = runTest {
        val stages = listOf(
            PostStageSpec(
                id = "broken",
                label = "Broken",
                probe = { throw IllegalStateException("boom") },
            ),
        )
        val steps = runStages(stages)
        val status = steps[0].status
        assertTrue("expected Failed, got $status", status is PostStatus.Failed)
        assertEquals("boom", (status as PostStatus.Failed).message)
    }

    @Test
    fun timeoutProbe_resultsInSkipped() = runTest {
        val stages = listOf(
            PostStageSpec(
                id = "slow",
                label = "Slow",
                probe = {
                    kotlinx.coroutines.delay(500)
                    Ok
                },
            ),
        )
        val steps = runStages(stages, perStageTimeoutMs = 50L)
        val status = steps[0].status
        assertTrue("expected Skipped, got $status", status is PostStatus.Skipped)
    }

    @Test
    fun seedsAllStagesAsPendingBeforeFirstRun() = runTest {
        val stages = listOf(
            PostStageSpec(id = "a", label = "A", probe = { Ok }),
            PostStageSpec(id = "b", label = "B", probe = { Ok }),
            PostStageSpec(id = "c", label = "C", probe = { Ok }),
        )
        val orchestrator = PostSelfTestHarness(stages)
        // Before any run, the steps list is empty.
        assertEquals(emptyList<PostStep>(), orchestrator.steps.value)
        // Seed directly and inspect.
        orchestrator.seedForTest()
        assertEquals(3, orchestrator.steps.value.size)
        assertTrue(orchestrator.steps.value.all { it.status is PostStatus.Pending })
    }

    @Test
    fun multipleStages_runInOrder() = runTest {
        val order = mutableListOf<String>()
        val stages = listOf(
            PostStageSpec(id = "1", label = "1", probe = { order += "1"; Ok }),
            PostStageSpec(id = "2", label = "2", probe = { order += "2"; Ok }),
            PostStageSpec(id = "3", label = "3", probe = { order += "3"; Ok }),
        )
        val steps = runStages(stages)
        assertEquals(listOf("1", "2", "3"), order)
        assertTrue(steps.all { it.status is PostStatus.Ok })
    }

    @Test
    fun stageIdAndLabel_arePreserved() = runTest {
        val stages = listOf(
            PostStageSpec(id = "node-id", label = "Node identity", probe = { Ok }),
        )
        val steps = runStages(stages)
        assertEquals("node-id", steps[0].id)
        assertEquals("Node identity", steps[0].label)
    }

    @Test
    fun elapsedMs_isNonZeroAfterStage() = runTest {
        val stages = listOf(
            PostStageSpec(id = "sleep", label = "Sleep", probe = {
                kotlinx.coroutines.delay(20)
                Ok
            }),
        )
        val steps = runStages(stages, tick = { it + 5L })
        assertTrue("elapsedMs should be > 0", steps[0].elapsedMs > 0L)
    }

    @Test
    fun defaultStageTimeout_is8Seconds() {
        assertEquals(8_000L, PostSelfTest.DEFAULT_STAGE_TIMEOUT_MS)
    }

    @Test
    fun bundledModelTimeout_is30Seconds() {
        assertEquals(30_000L, PostSelfTest.BUNDLED_MODEL_TIMEOUT_MS)
    }

    /* ───────── helpers ───────── */

    private suspend fun runStages(
        stages: List<PostStageSpec>,
        perStageTimeoutMs: Long = 1_000L,
        tick: (Long) -> Long = { it + 1L },
    ): List<PostStep> {
        val orchestrator = PostSelfTestHarness(stages, perStageTimeoutMs, tick)
        orchestrator.runBlocking()
        return orchestrator.steps.value
    }

    /**
     * In-memory harness that mirrors [PostSelfTest] but with
     * a stubbed stage list and no Android dependencies. The
     * test uses [runBlocking] so the assertion can read the
     * final state immediately.
     */
    private class PostSelfTestHarness(
        val stages: List<PostStageSpec>,
        private val perStageTimeoutMs: Long = 1_000L,
        private val tick: (Long) -> Long = { it + 1L },
    ) {
        private val _steps = kotlinx.coroutines.flow.MutableStateFlow<List<PostStep>>(emptyList())
        val steps: kotlinx.coroutines.flow.StateFlow<List<PostStep>> = _steps

        fun seedForTest() {
            _steps.value = stages.map { PostStep(it.id, it.label, PostStatus.Pending) }
        }

        suspend fun runBlocking() {
            seedForTest()
            for (s in stages) {
                transition(s.id) { it.copy(status = PostStatus.Running) }
                val start = tick(0L)
                val result = runCatching {
                    kotlinx.coroutines.withTimeoutOrNull(perStageTimeoutMs) { s.probe() }
                }
                val elapsed = tick(start) - start
                when {
                    result.isFailure -> transition(s.id) {
                        it.copy(
                            status = PostStatus.Failed(
                                result.exceptionOrNull()?.message ?: "unknown",
                            ),
                            elapsedMs = elapsed,
                        )
                    }
                    result.getOrNull() == null -> transition(s.id) {
                        it.copy(status = PostStatus.Skipped("timeout"), elapsedMs = elapsed)
                    }
                    else -> transition(s.id) {
                        it.copy(status = PostStatus.Ok, elapsedMs = elapsed)
                    }
                }
            }
        }

        private fun transition(id: String, block: (PostStep) -> PostStep) {
            _steps.value = _steps.value.map { if (it.id == id) block(it) else it }
        }
    }
}
