package com.meshlit.chat

import kotlinx.coroutines.CancellationException

internal const val DRAFT_SAVE_ERROR="Draft could not be saved. Keep this chat open and try again."

/** A newer keystroke cancels the previous save; cancellation is not a storage failure. */
internal suspend fun saveChatDraft(save:suspend ()->Unit):Boolean = try {
    save()
    true
} catch(cancelled:CancellationException) {
    throw cancelled
} catch(_:Exception) {
    false
}
