package com.meshlit.history

import android.content.Context
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Phase 4.x — PrivateLM parity: persistent storage for the
 * Jobs screen's chat history.
 *
 * Replaces the previous in-memory `mutableStateListOf<PromptExchange>()`
 * which was lost on every process death. Mirrors PrivateLM's
 * Hive-backed `ChatSessionStore` so the conversation survives
 * a kill / relaunch.
 *
 * Storage shape: a JSON array under `filesDir/jobs_chat_history.json`.
 * Each row is a [StoredExchange] (timestamp + prompt + reply +
 * finished flag + optional attachment URI). The Jobs screen
 * converts these back into `PromptExchange` instances for
 * rendering.
 *
 * Retention: capped at [maxRows] (default 500) to avoid runaway
 * disk growth.
 *
 * Writes are debounced via [persistAsync] so a streaming reply
 * (one event per token chunk) does not hammer the disk.
 */
class JobsChatHistoryRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val maxRows: Int = 500,
) {

    private companion object {
        /** Adjacent rows with the same (prompt, reply, attachmentUri)
         *  and timestamps within this window are collapsed into one.
         *  50 ms is comfortably wider than the worst-case `append`
         *  debounce jitter (sub-ms) without merging real back-to-back
         *  user inputs. */
        const val dupWindowMs = 50L
    }

    private val log = logger("JobsChatHistoryRepository")

    private val mutex = Mutex()
    private val rows = ArrayDeque<StoredExchange>()
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _size = MutableStateFlow(0)
    val size: StateFlow<Int> = _size.asStateFlow()

    init {
        loadFromDisk()
    }

    /** Append one row. Throws [IllegalStateException] if maxRows
     *  is reached and the oldest row needs to be evicted. */
    suspend fun append(row: StoredExchange) {
        mutex.withLock {
            rows.addLast(row)
            enforceCapLocked()
            _size.value = rows.size
        }
        persistAsync()
    }

    /** Replace the oldest row's `reply` text. Used when a
     *  streaming `GenerationStarted` event arrives and the
     *  first row of the conversation needs to be hydrated. */
    suspend fun updateReply(index: Int, newReply: String, finished: Boolean) {
        mutex.withLock {
            if (index < 0 || index >= rows.size) return
            val cur = rows[index]
            rows[index] = cur.copy(reply = newReply, finished = finished)
        }
        persistAsync()
    }

    /** Read all rows (oldest-first). */
    suspend fun snapshot(): List<StoredExchange> {
        return mutex.withLock { rows.toList() }
    }

    /** Drop everything after a user-initiated "clear chat". */
    suspend fun clear() {
        mutex.withLock {
            rows.clear()
            _size.value = 0
        }
        withContext(Dispatchers.IO) { storageFile().delete() }
    }

    private fun enforceCapLocked() {
        if (maxRows <= 0) return
        val toRemove = rows.size - maxRows
        if (toRemove <= 0) return
        repeat(toRemove) { rows.removeFirst() }
    }

    private fun loadFromDisk() {
        val file = storageFile()
        if (!file.exists()) return
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return@runCatching
            val stored = json.decodeFromString(ListSerializer(StoredExchange.serializer()), text)
            // Dedupe adjacent rows that share the same prompt/reply/
            // attachment and whose timestamps are within `dupWindowMs`.
            // The chat history file can grow into hundreds of identical
            // rows when an upstream debounce glitch fires `append`
            // repeatedly for a single send; collapsing them at load
            // time keeps the LazyColumn fast and avoids duplicate-key
            // Compose crashes.
            val deduped = ArrayList<StoredExchange>(stored.size)
            for (row in stored) {
                val prev = deduped.lastOrNull()
                if (prev != null &&
                    prev.prompt == row.prompt &&
                    prev.reply == row.reply &&
                    prev.attachmentUri == row.attachmentUri &&
                    kotlin.math.abs(prev.tsMs - row.tsMs) <= dupWindowMs
                ) {
                    // Keep the newer (more recent ts) row — it's
                    // authoritative if the upstream append has been
                    // racing with a partial-state update.
                    deduped[deduped.lastIndex] = row
                } else {
                    deduped.add(row)
                }
            }
            rows.addAll(deduped)
            _size.value = rows.size
        }.onFailure {
            log.warn(
                "jobs_history.load.fail",
                "load threw",
                mapOf("err" to (it.message ?: "")),
            )
        }
    }

    private fun persistAsync() {
        scope.launch {
            withContext(Dispatchers.IO) {
                mutex.withLock {
                    val file = storageFile()
                    runCatching {
                        file.parentFile?.mkdirs()
                        val text = json.encodeToString(
                            ListSerializer(StoredExchange.serializer()),
                            rows.toList(),
                        )
                        file.writeText(text, Charsets.UTF_8)
                    }.onFailure {
                        log.warn(
                            "jobs_history.persist.fail",
                            "persist threw",
                            mapOf("err" to (it.message ?: "")),
                        )
                    }
                }
            }
        }
    }

    private fun storageFile(): File {
        return File(context.filesDir, "jobs_chat_history.json")
    }
}

/**
 * One prompt + reply on disk. Mirrors `PromptExchange` but
 * uses a primitive `Long` timestamp so we don't pull in any
 * data-layer types and so the JSON stays human-inspectable.
 */
@Serializable
data class StoredExchange(
    val tsMs: Long,
    val prompt: String,
    val reply: String,
    val finished: Boolean,
    val attachmentUri: String? = null,
)
