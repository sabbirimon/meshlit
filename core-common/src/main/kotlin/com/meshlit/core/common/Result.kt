package com.meshlit.core.common

/**
 * A sealed result type used by every core-* module. Every public API that
 * can fail returns MeshlitResult instead of throwing — explicit failure
 * is part of the cluster contract (a node can vanish mid-call).
 */
sealed interface MeshlitResult<out T> {
    data class Success<T>(val value: T) : MeshlitResult<T>
    data class Failure(val error: MeshlitError) : MeshlitResult<Nothing>

    fun getOrNull(): T? = (this as? Success<T>)?.value
    fun errorOrNull(): MeshlitError? = (this as? Failure)?.error

    companion object {
        inline fun <T> runCatching(block: () -> T): MeshlitResult<T> = try {
            Success(block())
        } catch (t: Throwable) {
            Failure(MeshlitError.Unknown(t))
        }
    }
}

/**
 * Failure categories used across all cluster operations. Every error in
 * the project is one of these — never a bare exception in a public API.
 *
 * The `tag` is stable for telemetry, logging, and the build guide's
 * instrumentation (Phase 5 adaptive scheduler reads these).
 */
sealed class MeshlitError(val tag: String, cause: Throwable? = null) : Throwable(cause) {
    /** Network or transport failure (timeout, refused, NSD lost). */
    class Network(tag: String, cause: Throwable? = null) : MeshlitError(tag, cause)

    /** Auth/permission failure (trust tier, token, signature). */
    class Auth(tag: String, cause: Throwable? = null) : MeshlitError(tag, cause)

    /** Resource exhausted (RAM, disk, FGS cap hit). */
    class Resource(tag: String, cause: Throwable? = null) : MeshlitError(tag, cause)

    /** Invalid input from caller (bad URL, malformed model file, etc.). */
    class Invalid(tag: String, cause: Throwable? = null) : MeshlitError(tag, cause)

    /** The peer went dark mid-operation. The router should retry. */
    class NodeGone(nodeId: String) :
        MeshlitError("node_gone:$nodeId")

    /** Native library / JNI failure (llama.cpp, etc.). */
    class Native(tag: String, cause: Throwable? = null) : MeshlitError(tag, cause)

    /** Anything we did not anticipate. Should be rare — log with stack. */
    class Unknown(cause: Throwable) :
        MeshlitError("unknown:${cause.javaClass.simpleName}", cause)
}