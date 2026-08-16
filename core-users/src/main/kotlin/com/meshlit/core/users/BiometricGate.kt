package com.meshlit.core.users

/**
 * Pure-Kotlin interface for biometric / device-credential unlock.
 * The Android implementation lives in `:app` and wraps `BiometricPrompt`
 * + `KeyguardManager`; this interface is what the UI calls and what
 * tests can stub.
 *
 * Why a thin abstraction: we want the unlock gate to be testable in
 * plain JUnit (no Robolectric, no FragmentActivity) and we want the
 * ability to mock a "device without biometric" fallback on emulators.
 */
interface BiometricGate {
    /** True when the device has at least one biometric / device-credential configured. */
    fun isAvailable(): Boolean

    /**
     * Suspend until the user authenticates. Returns true on success,
     * false on cancel/failure. Implementations should call the
     * supplied [onUnavailable] when no biometric is configured so
     * the UI can fall back to a PIN prompt.
     */
    suspend fun requireUnlock(reason: String): BiometricResult
}

sealed interface BiometricResult {
    object Success : BiometricResult
    object Failure : BiometricResult
    object Cancelled : BiometricResult
    data class Unavailable(val reason: String) : BiometricResult
}
