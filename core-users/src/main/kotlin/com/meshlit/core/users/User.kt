package com.meshlit.core.users

import com.meshlit.core.common.MeshlitResult
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * The single human account on a Meshlit device. v1 supports exactly
 * one user per install — multi-user is a Phase 4 concern. [role] is
 * advisory; the cluster trust posture is the actual gate.
 */
@Serializable
data class User(
    val id: String,
    val displayName: String,
    val avatarPath: String? = null,
    val role: String = "owner",
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    companion object {
        fun fresh(displayName: String): User = User(
            id = UUID.randomUUID().toString(),
            displayName = displayName,
        )
    }
}

/**
 * Persistence interface. `core-users` ships an in-memory and a
 * JSON-file impl; the app wires the file impl against `filesDir`.
 */
interface UserStore {
    fun current(): User
    fun list(): List<User>
    fun add(name: String): MeshlitResult<User>
    fun switchTo(userId: String): MeshlitResult<Unit>
    fun rename(userId: String, displayName: String): MeshlitResult<Unit>
    fun remove(userId: String): MeshlitResult<Unit>
}
