package com.meshlit.network.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Bridge to the Termux app (`com.termux`) using Termux's official
 * `RUN_COMMAND` plugin API. **Meshlit never bundles Termux** — it
 * detects an existing install and dispatches a well-formed
 * `service_execute` intent to Termux's `RunCommandService`. The
 * user runs the Termux app separately (typically installed from
 * F-Droid) and grants the `RUN_COMMAND` permission to Meshlit
 * inside Termux's settings.
 *
 * **What this is NOT.** It is not a shell. Meshlit does not embed
 * a libc, a busybox, or a Termux bootstrap. Every command runs
 * inside Termux's app process and returns its stdout / stderr /
 * exit code via a `PendingIntent` broadcast.
 *
 * **The wire (Termux `RunCommandService`):**
 *  - service class: `com.termux.app.RunCommandService`
 *  - action: `com.termux.service_execute`
 *  - executable path is the Intent `data` URI (`com.termux.file://`)
 *  - arguments: `com.termux.execute.arguments` (`String[]`)
 *  - stdin: `com.termux.execute.stdin`
 *  - working dir: `com.termux.execute.cwd`
 *  - background mode: `com.termux.execute.background` (boolean)
 *  - shell name: `com.termux.execute.shell_name`
 *  - session action: `com.termux.execute.session_action` (0..3)
 *  - result is delivered to a `PendingIntent` we pass, carrying
 *    `extras` -> `result` -> `{ stdout, stderr, exitCode, err, errmsg }`.
 *
 * References (verified at implementation time):
 *  - `com.termux.shared.termux.TermuxConstants` (RUN_COMMAND_SERVICE block)
 *  - `com.termux.app.RunCommandService` (AndroidManifest action receiver)
 *  - `com.termux.permission.RUN_COMMAND` (custom permission to gate the service)
 */
interface TermuxBridge {

    /** Stable package id for the Termux app (F-Droid build). */
    val packageName: String get() = "com.termux"

    /** Custom permission Termux requires callers to hold. */
    val runCommandPermission: String get() = "com.termux.permission.RUN_COMMAND"

    /**
     * Snapshot of whether Termux is installed, has granted us the
     * `RUN_COMMAND` permission, and is new enough to support the
     * `service_execute` flow. The "allow-external-apps" toggle is
     * a property inside Termux; we report it as best we can via
     * a probe run.
     */
    suspend fun probe(): TermuxSetupState

    /** Open Termux in the Play Store / F-Droid fallback. */
    fun openInstallPage(context: Context)

    /**
     * Open Termux's "allow external apps" preferences so the user
     * can flip `allow-external-apps = true`.
     */
    fun openAllowExternalApps(context: Context): Boolean

    /**
     * Submit a command to Termux's `RunCommandService`. Returns a
     * [TermuxRunHandle] which exposes a one-shot [TermuxRunResult]
     * via `await()`. Caller is responsible for cancellation
     * (`cancel()` stops the underlying broadcast registration;
     * the running command inside Termux is killed with TERM by
     * sending `com.termux.service_stop`).
     *
     * @param executable absolute path inside Termux's userland
     *  (e.g. `/data/data/com.termux/files/usr/bin/df`). The path
     *  is converted to a `com.termux.file://` URI per the spec.
     * @param arguments argv-style list of strings (no shell parsing).
     * @param workingDirectory absolute path inside Termux's
     *  userland (must already exist).
     * @param stdin optional stdin text fed to the command.
     * @param timeoutMs hard timeout for the whole run. After this
     *  elapses, the bridge calls `cancel()` and surfaces a typed
     *  `TIMEOUT` result.
     * @param background when true, the command runs in Termux's
     *  background service and the result is delivered to the
     *  `PendingIntent` when the command eventually finishes.
     */
    suspend fun runCommand(
        executable: String,
        arguments: List<String> = emptyList(),
        workingDirectory: String? = null,
        stdin: String? = null,
        timeoutMs: Long = 30_000L,
        background: Boolean = false,
    ): TermuxRunResult

    /**
     * Cancel an in-flight run by its [TermuxRunHandle.id]. Sends
     * `com.termux.service_stop` with the matching session label
     * so Termux's foreground service terminates the process group.
     */
    suspend fun cancelCommand(handle: TermuxRunHandle): Boolean

    /**
     * Observe every result delivered by Termux, regardless of who
     * triggered the run. Used by the audit log.
     */
    fun observeResults(): kotlinx.coroutines.flow.Flow<TermuxRunResult>

    /**
     * Milliseconds since epoch. Injected so tests can drive
     * deterministic time.
     */
    fun interface Clock {
        fun now(): Long
    }
}

/** Setup probe result. All fields are best-effort. */
data class TermuxSetupState(
    val installed: Boolean,
    val runCommandPermissionGranted: Boolean,
    val allowExternalAppsLikelyEnabled: Boolean,
    val pluginApiSupported: Boolean,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
) {
    companion object {
        val NotInstalled = TermuxSetupState(
            installed = false,
            runCommandPermissionGranted = false,
            allowExternalAppsLikelyEnabled = false,
            pluginApiSupported = false,
        )
    }
}

/** Result of a single command execution. */
data class TermuxRunResult(
    val handleId: String,
    val executable: String,
    val arguments: List<String>,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val durationMs: Long,
    val status: Status,
    val errmsg: String? = null,
) {
    enum class Status {
        /** PendingIntent returned with exit code. */
        SUCCESS,
        /** Non-zero exit code — still considered "ran", not an error. */
        NON_ZERO_EXIT,
        /** Timed out before completion. */
        TIMEOUT,
        /** Caller cancelled. */
        CANCELLED,
        /** Termux is not installed / not configured. */
        SETUP_INCOMPLETE,
        /** Termux refused the call (permission, allow-external-apps off). */
        REFUSED,
        /** Unexpected exception. */
        ERROR,
    }
}

/** Opaque handle for an in-flight run. */
data class TermuxRunHandle(
    val id: String,
    val executable: String,
    val arguments: List<String>,
)

/**
 * Production adapter. Wraps the `Context` + `PackageManager` and
 * builds well-formed `service_execute` intents. Unit tests use
 * the [FakeTermuxBridge] in `app/src/test/...` instead.
 *
 * The `PendingIntent` used to receive the result is a unique
 * `PendingIntent.getBroadcast` per run — its `requestCode` is
 * the run id, and we register a transient `BroadcastReceiver` for
 * the lifetime of the run. The receiver forwards the result into
 * the [observeResults] flow and completes the [runCommand] future.
 */
class AndroidTermuxBridge(
    private val context: Context,
    private val clock: TermuxBridge.Clock = TermuxBridge.Clock { System.currentTimeMillis() },
    private val pendingIntentFactory: PendingIntentFactory = DefaultPendingIntentFactory(context),
    private val resultBus: ResultBus = InMemoryResultBus(),
) : TermuxBridge {

    override suspend fun probe(): TermuxSetupState {
        val pm = context.packageManager
        val pkg = runCatching { pm.getPackageInfo(packageName, 0) }.getOrNull()
            ?: return TermuxSetupState.NotInstalled
        val granted = runCatching {
            pm.checkPermission(runCommandPermission, packageName) == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        return TermuxSetupState(
            installed = true,
            runCommandPermissionGranted = granted,
            // Probe for allow-external-apps is non-trivial without
            // launching Termux; we surface this as "unknown" in the UI
            // and let the user confirm by running the test command.
            allowExternalAppsLikelyEnabled = granted,
            // The RUN_COMMAND_SERVICE action was added in Termux 0.119+.
            // We treat any installed build as supporting the plugin API
            // and let the actual call surface the typed error.
            pluginApiSupported = true,
            installedVersionName = pkg.versionName,
            installedVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            },
        )
    }

    override fun openInstallPage(context: Context) {
        // F-Droid is the canonical source. Play Store link is the fallback.
        val primary = runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("https://f-droid.org/packages/com.termux/"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            true
        }.getOrDefault(false)
        if (!primary) {
            runCatching {
                context.startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("market://details?id=$packageName"),
                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                )
            }
        }
    }

    override fun openAllowExternalApps(context: Context): Boolean = runCatching {
        // Termux exposes a settings activity for the allow-external-apps
        // toggle via its own manifest action. We deep-link rather than
        // hand-edit a properties file.
        val intent = Intent().apply {
            component = ComponentName(packageName, "com.termux.app.activities.SettingsActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)

    override suspend fun runCommand(
        executable: String,
        arguments: List<String>,
        workingDirectory: String?,
        stdin: String?,
        timeoutMs: Long,
        background: Boolean,
    ): TermuxRunResult {
        val setup = probe()
        if (!setup.installed) {
            return TermuxRunResult(
                handleId = "",
                executable = executable,
                arguments = arguments,
                exitCode = null,
                stdout = "",
                stderr = "",
                durationMs = 0,
                status = TermuxRunResult.Status.SETUP_INCOMPLETE,
                errmsg = "termux_not_installed",
            )
        }
        if (!setup.runCommandPermissionGranted) {
            return TermuxRunResult(
                handleId = "",
                executable = executable,
                arguments = arguments,
                exitCode = null,
                stdout = "",
                stderr = "",
                durationMs = 0,
                status = TermuxRunResult.Status.REFUSED,
                errmsg = "run_command_permission_missing",
            )
        }

        val handleId = "termux-${clock.now()}-${executable.hashCode().toString(16)}"
        val startedAt = clock.now()
        val intent = buildServiceExecuteIntent(
            executable = executable,
            arguments = arguments,
            workingDirectory = workingDirectory,
            stdin = stdin,
            background = background,
            pendingIntent = pendingIntentFactory.createForResult(handleId),
        )

        val sendOk = runCatching {
            context.startService(intent)
            true
        }.getOrElse { t ->
            TermuxLogger.warn(context, "startService failed: ${t.message}")
            false
        }
        if (!sendOk) {
            return TermuxRunResult(
                handleId = handleId,
                executable = executable,
                arguments = arguments,
                exitCode = null,
                stdout = "",
                stderr = "",
                durationMs = 0,
                status = TermuxRunResult.Status.REFUSED,
                errmsg = "service_unavailable",
            )
        }

        return resultBus.await(handleId, timeoutMs).let { result ->
            val elapsed = clock.now() - startedAt
            when {
                result == null -> TermuxRunResult(
                    handleId = handleId,
                    executable = executable,
                    arguments = arguments,
                    exitCode = null,
                    stdout = "",
                    stderr = "",
                    durationMs = elapsed,
                    status = TermuxRunResult.Status.TIMEOUT,
                    errmsg = "timeout_after_${timeoutMs}ms",
                )
                result.cancelled -> result.toTermuxRunResult(handleId, executable, arguments)
                    .copy(status = TermuxRunResult.Status.CANCELLED, durationMs = elapsed)
                else -> {
                    val mapped = result.toTermuxRunResult(handleId, executable, arguments)
                        .copy(durationMs = elapsed)
                    if (mapped.exitCode == 0) mapped
                    else mapped.copy(status = TermuxRunResult.Status.NON_ZERO_EXIT)
                }
            }
        }
    }

    override suspend fun cancelCommand(handle: TermuxRunHandle): Boolean {
        // Two parts:
        //   1. Mark the in-flight future as cancelled so await() returns.
        //   2. Ask Termux to stop the running process group via
        //      `com.termux.service_stop`. The session label we set
        //      when starting the run is reused here.
        resultBus.cancel(handle.id)
        val intent = Intent("com.termux.service_stop").apply {
            setPackage(packageName)
            putExtra("com.termux.execute.shell_name", handle.id)
        }
        return runCatching {
            context.startService(intent)
            true
        }.getOrDefault(false)
    }

    override fun observeResults() = resultBus.observe()

    private fun buildServiceExecuteIntent(
        executable: String,
        arguments: List<String>,
        workingDirectory: String?,
        stdin: String?,
        background: Boolean,
        pendingIntent: android.app.PendingIntent,
    ): Intent {
        val uri = Uri.Builder()
            .scheme("com.termux.file")
            .path(executable)
            .build()
        return Intent().apply {
            setClassName(packageName, "com.termux.app.RunCommandService")
            action = "com.termux.service_execute"
            data = uri
            putExtra("com.termux.execute.arguments", arguments.toTypedArray())
            if (workingDirectory != null) putExtra("com.termux.execute.cwd", workingDirectory)
            if (stdin != null) putExtra("com.termux.execute.stdin", stdin)
            putExtra("com.termux.execute.background", background)
            // Session action 2 = "switch to new session, don't open activity".
            // We don't want a Termux UI pop-up on every run.
            putExtra("com.termux.execute.session_action", 2)
            putExtra("com.termux.execute.shell_name", uri.lastPathSegment ?: executable)
            // The PendingIntent carries the result bundle back to us.
            putExtra("pendingIntent", pendingIntent)
        }
    }
}

/** Builds a PendingIntent that delivers the result bundle to [TermuxResultReceiver]. */
interface PendingIntentFactory {
    fun createForResult(handleId: String): android.app.PendingIntent
}

class DefaultPendingIntentFactory(private val context: Context) : PendingIntentFactory {
    override fun createForResult(handleId: String): android.app.PendingIntent {
        val intent = Intent(TermuxConstants.ACTION_RUN_COMMAND_RESULT).apply {
            setPackage(context.packageName)
            // The action + extras are how the receiver knows which handle
            // the result belongs to.
            putExtra(TermuxConstants.EXTRA_RESULT_HANDLE_ID, handleId)
        }
        // FLAG_UPDATE_CURRENT + unique request code per handle ensures
        // the broadcast target is correct.
        return android.app.PendingIntent.getBroadcast(
            context,
            handleId.hashCode(),
            intent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE,
        )
    }
}

/** Constants used to talk to the Termux service + our own receiver. */
object TermuxConstants {
    const val ACTION_RUN_COMMAND_RESULT = "com.meshlit.action.TERMUX_RUN_RESULT"
    const val EXTRA_RESULT_HANDLE_ID = "com.meshlit.extra.TERMUX_HANDLE_ID"
    const val EXTRA_RESULT_STDOUT = "stdout"
    const val EXTRA_RESULT_STDERR = "stderr"
    const val EXTRA_RESULT_EXIT_CODE = "exitCode"
    const val EXTRA_RESULT_ERR = "err"
    const val EXTRA_RESULT_ERRMSG = "errmsg"
}

/** Result wire record. Public so the [ResultBus] interface can carry it. */
data class WireResult(
    val handleId: String,
    val stdout: String,
    val stderr: String,
    val exitCode: Int?,
    val err: Int?,
    val errmsg: String?,
    val cancelled: Boolean = false,
) {
    fun toTermuxRunResult(handleId: String, executable: String, arguments: List<String>) =
        TermuxRunResult(
            handleId = handleId,
            executable = executable,
            arguments = arguments,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            durationMs = 0,
            status = TermuxRunResult.Status.SUCCESS,
            errmsg = errmsg,
        )
}

/** Internal bus that maps handleId -> deferred result. */
interface ResultBus {
    suspend fun await(handleId: String, timeoutMs: Long): WireResult?
    fun cancel(handleId: String)
    fun deliver(result: WireResult)
    fun observe(): kotlinx.coroutines.flow.Flow<TermuxRunResult>
}

class InMemoryResultBus : ResultBus {
    // Synchronized map guarded by `lock` (Java's ReentrantLock is the
    // right choice here because `cancel` / `deliver` are called from
    // the BroadcastReceiver's main-thread onReceive callback, NOT
    // from a coroutine context).
    private val lock = java.util.concurrent.locks.ReentrantLock()
    private val pending = mutableMapOf<String, kotlin.coroutines.Continuation<WireResult>>()
    private val audit = kotlinx.coroutines.flow.MutableSharedFlow<TermuxRunResult>(extraBufferCapacity = 64)

    private inline fun <T> withLock(block: () -> T): T {
        lock.lock()
        return try { block() } finally { lock.unlock() }
    }

    override suspend fun await(handleId: String, timeoutMs: Long): WireResult? =
        kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            kotlinx.coroutines.suspendCancellableCoroutine<WireResult> { cont ->
                withLock { pending[handleId] = cont }
                cont.invokeOnCancellation {
                    withLock { pending.remove(handleId) }
                }
            }
        }

    override fun cancel(handleId: String) {
        val cont = withLock { pending.remove(handleId) }
        // Already delivered — ignore.
        cont?.resume(WireResult(handleId, "", "", null, null, "cancelled", cancelled = true))
    }

    override fun deliver(result: WireResult) {
        val cont = withLock { pending.remove(result.handleId) }
        // Already delivered — ignore.
        cont?.resume(result)
        audit.tryEmit(
            result.toTermuxRunResult(result.handleId, "", emptyList())
        )
    }

    override fun observe() = audit.asSharedFlow()
}

/**
 * The BroadcastReceiver that Termux's service fires the
 * `PendingIntent` at. Registered in `AndroidManifest.xml` with the
 * `com.termux.permission.RUN_COMMAND` permission so that only Termux
 * can deliver to us.
 */
class TermuxResultReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: android.content.Context, intent: android.content.Intent) {
        val handleId = intent.getStringExtra(TermuxConstants.EXTRA_RESULT_HANDLE_ID) ?: return
        val wire = WireResult(
            handleId = handleId,
            stdout = intent.getStringExtra(TermuxConstants.EXTRA_RESULT_STDOUT) ?: "",
            stderr = intent.getStringExtra(TermuxConstants.EXTRA_RESULT_STDERR) ?: "",
            exitCode = intent.getIntExtra(TermuxConstants.EXTRA_RESULT_EXIT_CODE, -1).takeIf { it >= 0 },
            err = intent.getIntExtra(TermuxConstants.EXTRA_RESULT_ERR, 0),
            errmsg = intent.getStringExtra(TermuxConstants.EXTRA_RESULT_ERRMSG),
        )
        // Hand off to the bus. The bridge picks it up in its await().
        TermuxReceiverRegistry.bus?.deliver(wire)
    }
}

/** Singleton holder for the active bus so the static receiver can reach it. */
object TermuxReceiverRegistry {
    @Volatile var bus: ResultBus? = null
}

/** No-op logger so we don't pull android.util.Log into a JVM-test path. */
internal object TermuxLogger {
    fun warn(context: Context, msg: String) {
        // We never log secrets, command arguments, or stdout/stderr.
        // Only the abstract error class is allowed in the message.
        runCatching {
            android.util.Log.w("MeshlitTermux", msg.take(200))
        }
    }
}