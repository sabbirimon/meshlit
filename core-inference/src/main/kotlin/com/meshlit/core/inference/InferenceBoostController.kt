package com.meshlit.core.inference

import android.os.Process
import com.meshlit.core.common.DeviceProfile
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
 * Controls the "Boost" quick action (Phase Observability 1).
 *
 * When the user taps Boost on the drawer we:
 *   1. Build a dedicated inference thread pool sized to the device's
 *      CPU count.
 *   2. Pin each thread's OS priority to [Process.THREAD_PRIORITY_URGENT_DISPLAY]
 *      (-8) so the kernel scheduler prefers inference work over
 *      background housekeeping when both are runnable.
 *   3. Hand the wrapped [CoroutineDispatcher] back to the coordinator
 *      so `infer()` runs on the boosted pool instead of
 *      [Dispatchers.Default].
 *
 * The controller is idempotent — calling [enable] twice keeps the
 * same pool and just refreshes the priority in case the OS reset it.
 * [disable] tears down the pool and reverts to [Dispatchers.Default].
 *
 * It deliberately does *not* touch the GPU backend selection. The
 * device-profile NPU detector already chooses the best accelerator
 * at load time; boost only changes the OS-level thread priority.
 */
class InferenceBoostController(
    private val onDispatcherChange: (CoroutineDispatcher) -> Unit,
    private val log: com.meshlit.core.common.MeshlitLogger = logger("InferenceBoostController"),
) {

    @Volatile private var boostedExecutor: ExecutorService? = null

    /** Returns true when the controller has an active boosted pool. */
    val isActive: Boolean get() = boostedExecutor != null

    /**
     * Build the boosted pool. [profile] is consulted so the NPU
     * hint can be logged (and so a future revision can switch to the
     * NPU dispatcher when present). The thread priority bump is
     * applied unconditionally — it improves tail latency even on
     * devices without an NPU.
     */
    fun enable(profile: DeviceProfile) {
        // Always rebuild the pool so the priority bump is fresh —
        // the OS resets thread priorities when the process is
        // backgrounded and may not restore them on foreground.
        teardown()
        val effective = profile.effective
        val cores = effective.cpuCoreCount.coerceAtLeast(2)
        val executor = Executors.newFixedThreadPool(cores, BoostThreadFactory())
        boostedExecutor = executor
        val dispatcher = executor.asCoroutineDispatcher()
        onDispatcherChange(dispatcher)
        log.info(
            "boost.enable",
            "inference thread pool: cores=$cores priority=${BOOST_PRIORITY}" +
                if (effective.hasNpu) " npu=present" else " npu=absent",
        )
    }

    /**
     * Disable boost. Reverts the coordinator to [Dispatchers.Default].
     * No-op when boost is not active.
     */
    fun disable() {
        val exec = boostedExecutor ?: return
        teardown()
        onDispatcherChange(Dispatchers.Default)
        log.info("boost.disable", "reverted to Dispatchers.Default")
    }

    private fun teardown() {
        val exec = boostedExecutor ?: return
        boostedExecutor = null
        runCatching { exec.shutdown() }
    }

    private class BoostThreadFactory : ThreadFactory {
        private val counter = AtomicInteger(0)
        override fun newThread(r: Runnable): Thread {
            val t = Thread(r, "meshlit-boost-${counter.getAndIncrement()}")
            t.isDaemon = true
            // Pin to URGENT_DISPLAY (-8). On Android the kernel
            // scheduler favours audio/video pipelines that use this
            // priority; inference that drives a chat UI benefits
            // from the same treatment. The setPriority call must
            // happen *before* start() — once a thread is running,
            // changing its priority is best-effort.
            runCatching { Process.setThreadPriority(BOOST_PRIORITY) }
            return t
        }
    }

    companion object {
        /** OS-level priority shared by the audio / display pipeline. */
        const val BOOST_PRIORITY: Int = Process.THREAD_PRIORITY_URGENT_DISPLAY
    }
}