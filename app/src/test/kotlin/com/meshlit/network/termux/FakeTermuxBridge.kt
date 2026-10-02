package com.meshlit.network.termux

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * In-memory [TermuxBridge] for unit tests. Each test sets
 * [setup] and either [scriptedResults] (canned outcomes) or
 * [block] (suspend forever to simulate a hang).
 *
 * The fake does NOT talk to the platform's PackageManager — we
 * accept the setup as gospel so a single test can exercise the
 * full happy path without a real Termux install. Production
 * wiring must use [AndroidTermuxBridge].
 */
class FakeTermuxBridge(
    var setup: TermuxSetupState = TermuxSetupState(
        installed = true,
        runCommandPermissionGranted = true,
        allowExternalAppsLikelyEnabled = true,
        pluginApiSupported = true,
    ),
) : TermuxBridge {

    /** Per-call canned outcomes. Tests push in order; last value wins if list is empty. */
    val scriptedResults: MutableList<TermuxRunResult> = mutableListOf()

    /** When non-null, `runCommand` suspends forever instead of returning. */
    var block: Boolean = false

    /** When non-null, `runCommand` throws this exception. */
    var throwable: Throwable? = null

    /** Records every observed (executable, arguments) tuple. */
    val calls: MutableList<Pair<String, List<String>>> = mutableListOf()

    val resultsFlow: MutableSharedFlow<TermuxRunResult> = MutableSharedFlow(extraBufferCapacity = 16)

    override suspend fun probe(): TermuxSetupState = setup

    override fun openInstallPage(context: Context) = Unit
    override fun openAllowExternalApps(context: Context): Boolean = true

    override suspend fun runCommand(
        executable: String,
        arguments: List<String>,
        workingDirectory: String?,
        stdin: String?,
        timeoutMs: Long,
        background: Boolean,
    ): TermuxRunResult {
        calls += executable to arguments
        if (block) {
            // The real bridge times out after `timeoutMs`. We use
            // `kotlinx.coroutines.awaitCancellation` so the test
            // can race the dispatcher with a coroutine cancel.
            kotlinx.coroutines.awaitCancellation()
        }
        throwable?.let { throw it }
        val canned = scriptedResults.removeFirstOrNull() ?: TermuxRunResult(
            handleId = "fake-handle",
            executable = executable,
            arguments = arguments,
            exitCode = 0,
            stdout = "fake stdout for $executable",
            stderr = "",
            durationMs = 1,
            status = TermuxRunResult.Status.SUCCESS,
        )
        resultsFlow.tryEmit(canned)
        return canned
    }

    override suspend fun cancelCommand(handle: TermuxRunHandle): Boolean {
        scriptedResults += TermuxRunResult(
            handleId = handle.id,
            executable = handle.executable,
            arguments = handle.arguments,
            exitCode = null,
            stdout = "",
            stderr = "",
            durationMs = 0,
            status = TermuxRunResult.Status.CANCELLED,
        )
        return true
    }

    override fun observeResults(): Flow<TermuxRunResult> = resultsFlow.asSharedFlow()
}
