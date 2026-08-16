package com.meshlit.core.users

import com.meshlit.core.common.MeshlitError
import com.meshlit.core.common.MeshlitResult
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure in-memory store. Used in tests and as the seed for the file
 * store before the file is read.
 */
class InMemoryUserStore(seed: User? = null) : UserStore {
    private val map = LinkedHashMap<String, User>()
    private var current: String? = seed?.id

    init {
        if (seed != null) map[seed.id] = seed
    }

    override fun current(): User = map[current ?: map.keys.firstOrNull()]
        ?: MeshlitUserFallback

    override fun list(): List<User> = map.values.toList()

    override fun add(name: String): MeshlitResult<User> {
        val user = User.fresh(name)
        map[user.id] = user
        current = user.id
        return MeshlitResult.Success(user)
    }

    override fun switchTo(userId: String): MeshlitResult<Unit> {
        if (userId !in map) return MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        current = userId
        return MeshlitResult.Success(Unit)
    }

    override fun rename(userId: String, displayName: String): MeshlitResult<Unit> {
        val user = map[userId] ?: return MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        map[userId] = user.copy(displayName = displayName)
        return MeshlitResult.Success(Unit)
    }

    override fun remove(userId: String): MeshlitResult<Unit> {
        if (map.remove(userId) == null) {
            return MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        }
        if (current == userId) current = map.keys.firstOrNull()
        return MeshlitResult.Success(Unit)
    }
}

/**
 * File-backed store. JSON shape is `[{"id": ..., "displayName": ...,
 * ...}, ...]` plus a sibling file containing the active user id.
 * Falls back to a placeholder owner if the store is empty.
 */
class FileBackedUserStore(
    private val storeDir: File,
    private val fileName: String = "users.json",
) : UserStore {
    private val map = LinkedHashMap<String, User>()
    private val activeFile: File = File(storeDir, "active_user.txt")
    private var current: String? = null
    private val lock = Any()

    init {
        storeDir.mkdirs()
        load()
    }

    override fun current(): User = map[current ?: map.keys.firstOrNull()] ?: MeshlitUserFallback

    override fun list(): List<User> = map.values.toList()

    override fun add(name: String): MeshlitResult<User> = synchronized(lock) {
        val user = User.fresh(name)
        map[user.id] = user
        current = user.id
        persist()
        MeshlitResult.Success(user)
    }

    override fun switchTo(userId: String): MeshlitResult<Unit> = synchronized(lock) {
        if (userId !in map) return@synchronized MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        current = userId
        persist()
        MeshlitResult.Success(Unit)
    }

    override fun rename(userId: String, displayName: String): MeshlitResult<Unit> = synchronized(lock) {
        val user = map[userId] ?: return@synchronized MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        map[userId] = user.copy(displayName = displayName)
        persist()
        MeshlitResult.Success(Unit)
    }

    override fun remove(userId: String): MeshlitResult<Unit> = synchronized(lock) {
        if (map.remove(userId) == null) {
            return@synchronized MeshlitResult.Failure(MeshlitError.Invalid("users.unknown_id"))
        }
        if (current == userId) current = map.keys.firstOrNull()
        persist()
        MeshlitResult.Success(Unit)
    }

    // ---- persistence ---------------------------------------------------

    private fun load() {
        val file = File(storeDir, fileName)
        if (file.exists()) {
            val text = runCatching { file.readText() }.getOrNull().orEmpty()
            val parsed = runCatching {
                kotlinx.serialization.json.Json {
                    ignoreUnknownKeys = true
                }.decodeFromString(
                    kotlinx.serialization.builtins.ListSerializer(User.serializer()),
                    text,
                )
            }.getOrNull().orEmpty()
            parsed.forEach { map[it.id] = it }
        }
        if (activeFile.exists()) {
            current = runCatching { activeFile.readText().trim() }.getOrNull()
        }
        if (current !in map) current = map.keys.firstOrNull()
    }

    private fun persist() {
        val list = map.values.toList()
        val text = kotlinx.serialization.json.Json {
            encodeDefaults = true
        }.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(User.serializer()),
            list,
        )
        val target = File(storeDir, fileName)
        val tmp = File(storeDir, "$fileName.tmp")
        tmp.writeText(text)
        if (target.exists()) target.delete()
        if (!tmp.renameTo(target)) {
            target.writeText(text)
            tmp.delete()
        }
        activeFile.writeText(current.orEmpty())
    }
}

/**
 * Placeholder used when the store is empty. Gives the app something
 * to display before the first run setup creates a real user. The
 * placeholder is intentionally marked as `createdAtMs = 0` so the UI
 * can detect "no real user yet" without an explicit flag.
 */
val MeshlitUserFallback: User = User(
    id = "owner-placeholder",
    displayName = "Owner",
    role = "owner",
    createdAtMs = 0L,
)
