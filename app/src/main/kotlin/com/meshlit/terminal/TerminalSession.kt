package com.meshlit.terminal

import android.content.Context
import com.meshlit.MeshlitApplication
import com.meshlit.core.common.logger
import com.meshlit.core.inference.CoordinatorState
import com.meshlit.observability.LogBuffer
import com.meshlit.terminal.vt.Screen
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * One-line terminal entry. Rendered as a `MonoSpace` row in the
 * terminal screen. `kind` is used to pick the text color:
 *
 *  - `INPUT`    — user echo (the prompt they typed)
 *  - `STDOUT`   — command output (the default)
 *  - `INFO`     — informational / welcome banner (dim)
 *  - `ERROR`    — error / unknown command (red)
 *  - `STREAM`   — a streaming token (cyan) from a `run` job
 *  - `HEADER`   — section banner (subtle accent)
 *  - `KEY`      — colored key in a `key: value` pair
 *  - `SUCCESS`  — green confirmation
 */
data class TerminalLine(
    val text: String,
    val kind: Kind = Kind.STDOUT,
    val groupId: Long = 0L,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Kind { INPUT, STDOUT, INFO, ERROR, STREAM, HEADER, KEY, SUCCESS }
}

/**
 * Visual grouping unit for the terminal. Each command produces one
 * group; the input line, all output, and the trailing success line
 * share a `groupId`. The screen renders groups as collapsible
 * "cards" with a left accent border so the user can see at a glance
 * where one command ends and the next begins.
 */
data class TerminalGroup(
    val id: Long,
    val command: String,
    val startedAtMs: Long,
    val lines: List<TerminalLine>,
)

/**
 * In-app command line for Meshlit. Backed by a `MutableStateFlow<List<TerminalGroup>>`
 * that the screen renders as a LazyColumn. Commands are dispatched
 * through [execute] which routes to one of the built-in handlers
 * (help, status, peers, metrics, model, logs, clear, whoami, version,
 * run, echo).
 *
 * Groups:
 *  - Every `execute()` call starts a fresh group (new id, new
 *    command name).
 *  - Subsequent `appendLine` / `appendAll` calls tag each line with
 *    that group's id.
 *  - The screen renders one row per group with all of its lines
 *    stacked under a left accent stripe.
 *
 * Designed to be cheap on a phone:
 *  - lines are appended, never mutated in place, so the StateFlow
 *    just emits a new immutable list
 *  - the list is bounded at MAX_GROUPS / MAX_LINES_PER_GROUP so long
 *    sessions don't OOM
 *  - all heavy work (log tail, metrics snapshot, dispatch) happens
 *    on the caller's coroutine context
 *
 * What this is NOT:
 *  - a real shell. No pipes, no redirects, no `&&`.
 *  - a remote shell. Local-only.
 */
class TerminalSession(
    private val context: Context,
    private val app: MeshlitApplication,
) {

    private val log = logger("TerminalSession")

    /** Underlying VT emulator. [execute] pipes every appended line
     *  through this so the [TerminalView] sees identical output with
     *  proper SGR colors. */
    val screen: Screen = Screen(cols = 80, rows = 24, maxScrollback = 5000)

    private val _groups = MutableStateFlow<List<TerminalGroup>>(emptyList())
    val groups: StateFlow<List<TerminalGroup>> = _groups.asStateFlow()

    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private var currentGroupId: Long = 0L
    private var currentGroupStartedAtMs: Long = 0L
    private val currentGroupLines = mutableListOf<TerminalLine>()
    private var currentGroupCommand: String = ""

    /**
     * Monotonic counter for `currentGroupId`. We used to call
     * `System.currentTimeMillis()` here, but two `startGroup()` calls
     * in the same millisecond (e.g. typing fast or batched dispatches
     * in tests) produced duplicate ids, which crashed the
     * LazyColumn key check on the screen with
     * `Key "X" was already used`. Phase 2.1 keeps the timestamp for
     * the visible `startedAtMs` field but uses this counter for the
     * Compose key.
     */
    private val groupIdCounter = java.util.concurrent.atomic.AtomicLong(0L)

    init {
        // Welcome banner. Cheap; just one group with one line.
        startGroup("welcome")
        appendLineInternal(TerminalLine(text = WELCOME, kind = TerminalLine.Kind.INFO))
        finishGroup()
        screen.process("\u001b[2m${WELCOME}\u001b[0m\r\n")
    }

    private fun startGroup(command: String) {
        currentGroupId = groupIdCounter.incrementAndGet()
        currentGroupStartedAtMs = System.currentTimeMillis()
        currentGroupCommand = command
        currentGroupLines.clear()
    }

    private fun finishGroup() {
        if (currentGroupLines.isEmpty()) return
        val group = TerminalGroup(
            id = currentGroupId,
            command = currentGroupCommand,
            startedAtMs = currentGroupStartedAtMs,
            lines = currentGroupLines.toList(),
        )
        val current = _groups.value
        val next = if (current.size >= MAX_GROUPS) {
            current.drop(current.size - MAX_GROUPS + 1) + group
        } else {
            current + group
        }
        _groups.value = next
        currentGroupLines.clear()
    }

    /** Append a single line to the active group. */
    private fun appendLineInternal(line: TerminalLine) {
        val tagged = line.copy(groupId = currentGroupId)
        currentGroupLines.add(tagged)
        // Mirror into the state flow so the screen sees partial groups
        // (streaming tokens, etc.) without waiting for finishGroup().
        val partial = TerminalGroup(
            id = currentGroupId,
            command = currentGroupCommand,
            startedAtMs = currentGroupStartedAtMs,
            lines = currentGroupLines.toList(),
        )
        val current = _groups.value
        if (current.lastOrNull()?.id == currentGroupId) {
            _groups.value = current.dropLast(1) + partial
        } else {
            _groups.value = current + partial
        }
        // Mirror to the VT emulator with SGR colours derived from the
        // line's Kind. Strip CR/LF; the screen feeds `\r\n` itself.
        screen.process(sgrFor(line.kind) + sanitize(line.text) + "\u001b[0m\r\n")
    }

    private fun sanitize(text: String): String =
        text.replace("\r", "").replace("\n", "")

    /** Map a [TerminalLine.Kind] to an SGR-prefixed escape sequence. */
    private fun sgrFor(kind: TerminalLine.Kind): String = when (kind) {
        TerminalLine.Kind.INPUT -> "\u001b[1;36m"
        TerminalLine.Kind.STDOUT -> "\u001b[0m"
        TerminalLine.Kind.INFO -> "\u001b[2;37m"
        TerminalLine.Kind.ERROR -> "\u001b[1;31m"
        TerminalLine.Kind.STREAM -> "\u001b[36m"
        TerminalLine.Kind.HEADER -> "\u001b[1;35m"
        TerminalLine.Kind.KEY -> "\u001b[33m"
        TerminalLine.Kind.SUCCESS -> "\u001b[1;32m"
    }

    /** Append multiple lines in one shot (e.g. help table rows). */
    private fun appendAllInternal(lines: List<TerminalLine>) {
        lines.forEach { appendLineInternal(it.copy(groupId = currentGroupId)) }
    }

    fun clear() {
        _groups.value = emptyList()
        screen.reset()
        startGroup("welcome")
        appendLineInternal(TerminalLine(text = WELCOME, kind = TerminalLine.Kind.INFO))
        finishGroup()
    }

    /**
     * Parse and run a single command line. Splits on whitespace, picks
     * a handler by the first token, and passes the rest as args. The
     * `run` command streams tokens into the active group until the
     * inference call returns.
     */
    suspend fun execute(raw: String) {
        val line = raw.trim()
        if (line.isEmpty()) return

        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return
        val cmd = parts[0].lowercase(Locale.US)
        val args = parts.drop(1)

        startGroup(line)
        appendLineInternal(TerminalLine(text = line, kind = TerminalLine.Kind.INPUT))

        try {
            when (cmd) {
                "help", "?" -> cmdHelp()
                "clear" -> {
                    clear()
                    return
                }
                "status" -> cmdStatus()
                "peers" -> cmdPeers()
                "metrics" -> cmdMetrics()
                "model" -> cmdModel()
                "logs" -> cmdLogs(args)
                "whoami" -> cmdWhoAmI()
                "version" -> cmdVersion()
                "echo" -> cmdEcho(args)
                "run" -> cmdRun(args.joinToString(" "))
                else -> appendLineInternal(
                    TerminalLine(
                        text = context.getString(
                            com.meshlit.R.string.terminal_unknown_cmd,
                            cmd,
                        ),
                        kind = TerminalLine.Kind.ERROR,
                    ),
                )
            }
        } catch (t: Throwable) {
            appendLineInternal(
                TerminalLine(
                    text = "error: ${t.javaClass.simpleName}: ${t.message}",
                    kind = TerminalLine.Kind.ERROR,
                ),
            )
            log.warn("terminal.cmd_fail", "command failed: $cmd", mapOf("err" to (t.message ?: "")))
        } finally {
            finishGroup()
        }
    }

    private fun cmdHelp() {
        val rows = listOf(
            "help" to com.meshlit.R.string.terminal_cmd_help_desc,
            "status" to com.meshlit.R.string.terminal_cmd_status_desc,
            "peers" to com.meshlit.R.string.terminal_cmd_peers_desc,
            "metrics" to com.meshlit.R.string.terminal_cmd_metrics_desc,
            "model" to com.meshlit.R.string.terminal_cmd_model_desc,
            "logs [n]" to com.meshlit.R.string.terminal_cmd_logs_desc,
            "clear" to com.meshlit.R.string.terminal_cmd_clear_desc,
            "whoami" to com.meshlit.R.string.terminal_cmd_whoami_desc,
            "version" to com.meshlit.R.string.terminal_cmd_version_desc,
            "run <prompt>" to com.meshlit.R.string.terminal_cmd_run_desc,
            "echo <text>" to com.meshlit.R.string.terminal_cmd_echo_desc,
        )
        appendLineInternal(
            TerminalLine(
                text = context.getString(com.meshlit.R.string.terminal_help_header),
                kind = TerminalLine.Kind.HEADER,
            ),
        )
        rows.forEach { (cmdName, descRes) ->
            appendLineInternal(
                TerminalLine(
                    text = "  ${cmdName.padEnd(18)}",
                    kind = TerminalLine.Kind.KEY,
                ),
            )
            appendLineInternal(
                TerminalLine(
                    text = "  " + " ".repeat(20) + context.getString(descRes),
                    kind = TerminalLine.Kind.STDOUT,
                ),
            )
        }
    }

    private fun appendKeyValue(key: String, value: String, valueKind: TerminalLine.Kind = TerminalLine.Kind.STDOUT) {
        appendLineInternal(TerminalLine("  $key :", kind = TerminalLine.Kind.KEY))
        appendLineInternal(TerminalLine("          $value", kind = valueKind))
    }

    private fun cmdStatus() {
        val tier = app.capabilityTier
        val coordState = app.inferenceCoordinator.state.value
        val stateStr = when (coordState) {
            is CoordinatorState.Idle -> "idle"
            is CoordinatorState.Loading -> "loading ${coordState.modelPath.substringAfterLast('/')}"
            is CoordinatorState.Starting -> "starting"
            is CoordinatorState.Ready -> "ready (${coordState.model.modelName})"
            is CoordinatorState.Generating -> "generating"
            is CoordinatorState.Error -> "error: ${coordState.message}"
        }
        val modelPath = app.bundledModelPath()?.absolutePath
        appendLineInternal(TerminalLine("status", kind = TerminalLine.Kind.HEADER))
        appendKeyValue("device", app.oemDetection.profile.tag)
        appendKeyValue("tier", tier.name)
        appendKeyValue("host_os", app.hostOS.tag)
        appendKeyValue(
            "coordinator",
            stateStr,
            valueKind = if (stateStr.startsWith("error")) TerminalLine.Kind.ERROR
                        else if (stateStr.startsWith("ready")) TerminalLine.Kind.SUCCESS
                        else TerminalLine.Kind.STDOUT,
        )
        appendKeyValue(
            "model_path",
            modelPath ?: "(none — bundled still extracting?)",
            valueKind = if (modelPath == null) TerminalLine.Kind.INFO else TerminalLine.Kind.STDOUT,
        )
    }

    private fun cmdPeers() {
        val cache = app.activePeerHealthCache()
        if (cache == null) {
            appendLineInternal(TerminalLine("(no peer cache — FGS not running yet)", kind = TerminalLine.Kind.INFO))
            return
        }
        val peers = cache.state.value
        if (peers.isEmpty()) {
            appendLineInternal(TerminalLine("(no peers discovered)", kind = TerminalLine.Kind.INFO))
            return
        }
        appendLineInternal(
            TerminalLine(
                "  ${"ip".padEnd(20)} ${"ok".padEnd(4)} ${"modelLoaded".padEnd(12)} msAgo",
                kind = TerminalLine.Kind.HEADER,
            ),
        )
        peers.forEach { (ip, h) ->
            val okKind = if (h.ok) TerminalLine.Kind.SUCCESS else TerminalLine.Kind.ERROR
            appendLineInternal(
                TerminalLine(
                    "  ${ip.padEnd(20)} " +
                        "${(if (h.ok) "yes" else " no").padEnd(4)} " +
                        "${if (h.modelLoaded) "yes" else " no"}".padEnd(12) +
                        " ${System.currentTimeMillis() - h.asOfMs}",
                    kind = okKind,
                ),
            )
        }
    }

    private fun cmdMetrics() {
        val reg = app.metricsRegistry
        val snap = reg.snapshot()
        appendLineInternal(TerminalLine("metrics", kind = TerminalLine.Kind.HEADER))
        appendKeyValue("queueDepth", snap.queueDepth.toString())
        appendKeyValue("totalJobs", snap.totalJobs.toString())
        appendKeyValue(
            "successJobs",
            snap.successJobs.toString(),
            valueKind = TerminalLine.Kind.SUCCESS,
        )
        appendKeyValue("tokensGenerated", snap.totalTokensGenerated.toString())
        appendKeyValue("avgTokensPerSecond", "%.2f".format(snap.avgTokensPerSecond))
        appendKeyValue("uptimeSeconds", snap.uptimeSeconds.toString())
        if (snap.failureTags.isNotEmpty()) {
            appendLineInternal(TerminalLine("  failureTags :", kind = TerminalLine.Kind.KEY))
            snap.failureTags.entries.sortedByDescending { it.value }.forEach { (tag, count) ->
                appendLineInternal(
                    TerminalLine(
                        "          $tag -> $count",
                        kind = TerminalLine.Kind.ERROR,
                    ),
                )
            }
        }
    }

    private fun cmdModel() {
        val state = app.inferenceCoordinator.state.value
        appendLineInternal(TerminalLine("model", kind = TerminalLine.Kind.HEADER))
        when (state) {
            is CoordinatorState.Ready -> {
                appendKeyValue("name", state.model.modelName)
                appendKeyValue("path", state.model.modelPath)
                appendKeyValue("params", "${state.model.parameterCount / 1_000_000}M")
                appendKeyValue("contextSize", state.model.contextSize.toString())
                appendKeyValue("quantization", state.model.quantization)
                appendKeyValue("engine", app.inferenceCoordinator.engineTag)
            }
            else -> appendLineInternal(
                TerminalLine("(no model loaded)", kind = TerminalLine.Kind.INFO),
            )
        }
    }

    private fun cmdLogs(args: List<String>) {
        val n = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, 200) ?: 20
        val buffer: LogBuffer = app.logBuffer
        val tail = buffer.entries.value.takeLast(n)
        if (tail.isEmpty()) {
            appendLineInternal(TerminalLine("(no log lines buffered)", kind = TerminalLine.Kind.INFO))
            return
        }
        appendLineInternal(TerminalLine("logs (last $n)", kind = TerminalLine.Kind.HEADER))
        val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        tail.forEach { entry ->
            val levelKind = when (entry.level.name) {
                "WARN", "ERROR" -> TerminalLine.Kind.ERROR
                "INFO" -> TerminalLine.Kind.KEY
                else -> TerminalLine.Kind.STDOUT
            }
            appendLineInternal(
                TerminalLine(
                    "${fmt.format(Date(entry.timestampMs))} " +
                        "[${entry.level.name.take(4)}] " +
                        "${entry.tag.padEnd(28)} " +
                        "${entry.message}",
                    kind = levelKind,
                ),
            )
        }
    }

    private fun cmdWhoAmI() {
        val profile = app.oemDetection.profile
        appendLineInternal(TerminalLine("whoami", kind = TerminalLine.Kind.HEADER))
        appendKeyValue("display", profile.displayName)
        appendKeyValue("oem", profile.tag)
        appendKeyValue("tier", app.capabilityTier.name)
        appendKeyValue("abi", app.hostOSDetection.abi)
        appendKeyValue("kernel", app.hostOSDetection.kernelVersion)
    }

    private fun cmdVersion() {
        appendLineInternal(TerminalLine("version", kind = TerminalLine.Kind.HEADER))
        appendKeyValue("meshlit", com.meshlit.BuildConfig.VERSION_NAME)
        appendKeyValue("build", com.meshlit.BuildConfig.BUILD_TYPE)
        appendKeyValue("tier", app.capabilityTier.name)
        appendKeyValue("engine", app.inferenceCoordinator.engineTag)
    }

    private fun cmdEcho(args: List<String>) {
        val text = if (args.isEmpty()) "" else args.joinToString(" ")
        appendLineInternal(TerminalLine(text, kind = TerminalLine.Kind.STDOUT))
    }

    private suspend fun cmdRun(prompt: String) {
        if (prompt.isBlank()) {
            appendLineInternal(
                TerminalLine("usage: run <prompt>", kind = TerminalLine.Kind.ERROR),
            )
            return
        }
        val state = app.inferenceCoordinator.state.value
        if (state !is CoordinatorState.Ready) {
            appendLineInternal(
                TerminalLine(
                    "no model loaded — open Jobs and try again after the FGS auto-load finishes",
                    kind = TerminalLine.Kind.ERROR,
                ),
            )
            return
        }
        appendLineInternal(
            TerminalLine(
                context.getString(com.meshlit.R.string.terminal_dispatch_started, prompt),
                kind = TerminalLine.Kind.INFO,
            ),
        )
        _isRunning.value = true
        try {
            val started = System.currentTimeMillis()
            var tokens = 0
            // Use a single STDOUT line that grows in place so we don't
            // drown the terminal in one row per token. This is the
            // "grouping" pattern: the run command's output is a single
            // streaming line, not 256 separate rows.
            val liveLine = TerminalLine("", kind = TerminalLine.Kind.STREAM)
            appendLineInternal(liveLine)
            val liveIndex = currentGroupLines.lastIndex

            val request = com.meshlit.core.inference.InferenceRequest(
                prompt = prompt,
                maxTokens = 256,
                temperature = 0.7f,
                onToken = { token ->
                    tokens++
                    val updated = liveLine.copy(
                        text = currentGroupLines[liveIndex].text + token,
                    )
                    currentGroupLines[liveIndex] = updated
                    // Re-mirror to the state flow.
                    val current = _groups.value
                    if (current.lastOrNull()?.id == currentGroupId) {
                        val last = current.last()
                        val newLines = last.lines.toMutableList().also {
                            if (liveIndex in it.indices) it[liveIndex] = updated
                        }
                        _groups.value = current.dropLast(1) + last.copy(lines = newLines)
                    }
                    // Pipe token to VT emulator too.
                    screen.process(sgrFor(TerminalLine.Kind.STREAM) + sanitize(token))
                },
                onComplete = { _ ->
                    val elapsed = System.currentTimeMillis() - started
                    appendLineInternal(
                        TerminalLine(
                            text = context.getString(
                                com.meshlit.R.string.terminal_dispatch_done,
                                elapsed.toInt(),
                                tokens,
                            ),
                            kind = TerminalLine.Kind.SUCCESS,
                        ),
                    )
                },
            )
            app.inferenceCoordinator.infer(request)
        } finally {
            _isRunning.value = false
        }
    }

    companion object {
        private const val MAX_GROUPS = 80
        private const val WELCOME =
            "Meshlit terminal — type `help` for commands"
    }
}
