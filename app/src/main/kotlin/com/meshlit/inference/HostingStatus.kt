package com.meshlit.inference
sealed interface HostingStatus {
    data object Stopped : HostingStatus
    data object Starting : HostingStatus
    data object Active : HostingStatus
    data class Failed(val reason: String) : HostingStatus
}
