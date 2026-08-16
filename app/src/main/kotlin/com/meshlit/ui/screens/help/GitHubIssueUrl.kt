package com.meshlit.ui.screens.help

import android.net.Uri

/**
 * URL-builder for the GitHub Issues "new issue" page.
 *
 * The Feedback screen calls [build] with the user's title + body
 * + type + the repo slug from [com.meshlit.settings.SettingsRepository].
 * Returns an [https://github.com/.../issues/new?labels=...&title=...&body=...]
 * URL that the system browser can open directly.
 */
object GitHubIssueUrl {

    enum class Kind(val label: String) {
        Bug("bug"),
        FeatureRequest("enhancement"),
    }

    /**
     * @param repoSlug "owner/name" — defaults to meshlit/meshlit-android.
     * @param kind Bug or FeatureRequest.
     * @param title short title.
     * @param body long-form description; pre-fill appended before the user
     *             edits it.
     */
    fun build(
        repoSlug: String,
        kind: Kind,
        title: String,
        body: String,
    ): String {
        val safeSlug = repoSlug.trim().ifBlank { "meshlit/meshlit-android" }
        return Uri.Builder()
            .scheme("https")
            .authority("github.com")
            .appendPath("$safeSlug/issues/new")
            .appendQueryParameter("labels", kind.label)
            .appendQueryParameter("title", title)
            .appendQueryParameter("body", body)
            .build()
            .toString()
    }
}