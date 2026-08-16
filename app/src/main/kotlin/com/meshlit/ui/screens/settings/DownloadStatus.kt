package com.meshlit.ui.screens.settings

/**
 * Per-row download state, shared by the alternative-models and
 * RunAnywhere-backed catalog rows in [ModelsScreen]. Driven by
 * `mutableStateMapOf` so any row that flips to a new state
 * recomposes immediately.
 *
 * The state machine is:
 *  - [Idle] — no download has been started for this row.
 *  - [Running] — fetch in progress; `progress` is 0..100. The user
 *    can cancel via the row's action button which transitions back
 *    to [Idle].
 *  - [Done] — successful fetch; `absolutePath` is the on-disk path
 *    of the downloaded model. The user can delete it via the
 *    row's action button which transitions back to [Idle].
 *  - [Failed] — aborted by an error; `reason` is human-readable.
 *    The user can retry which clears the status back to [Idle].
 */
sealed interface DownloadStatus {
    data object Idle : DownloadStatus
    data class Running(val progress: Int) : DownloadStatus
    data class Done(val absolutePath: String) : DownloadStatus
    data class Failed(val reason: String) : DownloadStatus
}