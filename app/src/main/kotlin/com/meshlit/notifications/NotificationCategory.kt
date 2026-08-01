package com.meshlit.notifications

import com.meshlit.R

/**
 * Notification event categories. Each category maps to its own
 * [android.app.NotificationChannel] so the user can tune importance,
 * sound, vibration, and lock-screen visibility per category from
 * Settings → Notifications.
 *
 * Why one channel per category instead of one mega-channel: Android
 * groups settings per channel, and a busy cluster produces a mix of
 * routine events (peer joined, job done) and security-critical ones
 * (jailbreak attempt, unknown peer auth). Lumping them together
 * forces the user to either accept all notifications or mute all —
 * which is the wrong default.
 *
 * Categories are deliberately coarse. If we find we need sub-categories
 * ("inference complete vs. inference failed"), we split them at that
 * point — never add a hidden sub-channel.
 */
enum class NotificationCategory(
    val channelId: String,
    val titleRes: Int,
    val descRes: Int,
    /** Importance floor; we don't post above this regardless of user override. */
    val maxImportance: Int,
    /** Whether this notification survives a phone reboot of importance. */
    val defaultImportance: Int,
    val showBadge: Boolean,
    val allowSound: Boolean,
    val allowVibration: Boolean,
    val groupKey: String,
) {
    /** Persistent "this phone is hosting the cluster" notification. NOT user-dismissible. */
    FOREGROUND_SERVICE(
        channelId = "fgs_host",
        titleRes = R.string.notif_cat_fgs_host,
        descRes = R.string.notif_cat_fgs_host_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "host",
    ),

    /** Inference job finished; result ready in the app. */
    INFERENCE_COMPLETE(
        channelId = "inference_complete",
        titleRes = R.string.notif_cat_inference_complete,
        descRes = R.string.notif_cat_inference_complete_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = true,
        allowSound = true,
        allowVibration = false,
        groupKey = "inference",
    ),

    /** A new peer appeared or an existing peer went offline. */
    PEER_TOPOLOGY(
        channelId = "peer_topology",
        titleRes = R.string.notif_cat_peer_topology,
        descRes = R.string.notif_cat_peer_topology_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "cluster",
    ),

    /** Job failed (OOM, native crash, network). User should see. */
    JOB_FAILED(
        channelId = "job_failed",
        titleRes = R.string.notif_cat_job_failed,
        descRes = R.string.notif_cat_job_failed_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        showBadge = true,
        allowSound = true,
        allowVibration = true,
        groupKey = "jobs",
    ),

    /** BRAIN-role device going down on low battery — cluster needs new BRAIN. */
    BRAIN_BATTERY_LOW(
        channelId = "brain_battery_low",
        titleRes = R.string.notif_cat_brain_battery_low,
        descRes = R.string.notif_cat_brain_battery_low_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        showBadge = true,
        allowSound = true,
        allowVibration = true,
        groupKey = "cluster",
    ),

    /** Model import finished — new weights available. */
    MODEL_IMPORT_COMPLETE(
        channelId = "model_import",
        titleRes = R.string.notif_cat_model_import,
        descRes = R.string.notif_cat_model_import_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        showBadge = true,
        allowSound = true,
        allowVibration = false,
        groupKey = "models",
    ),

    /** Security alert: jailbreak attempt, unknown peer auth, signature mismatch. */
    SECURITY_ALERT(
        channelId = "security_alert",
        titleRes = R.string.notif_cat_security_alert,
        descRes = R.string.notif_cat_security_alert_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_HIGH,
        showBadge = true,
        allowSound = true,
        allowVibration = true,
        groupKey = "security",
    ),

    /** WAN tunnel (Tailscale/WG) state change. */
    TUNNEL_STATE(
        channelId = "tunnel_state",
        titleRes = R.string.notif_cat_tunnel_state,
        descRes = R.string.notif_cat_tunnel_state_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "network",
    ),

    /** Public-side API: an external caller used one of our bearer tokens. */
    PUBLIC_TOKEN_USED(
        channelId = "public_token_used",
        titleRes = R.string.notif_cat_public_token_used,
        descRes = R.string.notif_cat_public_token_used_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "public",
    ),

    /** Custom-training milestone (loss plateaus, checkpoint saved). */
    TRAINING_MILESTONE(
        channelId = "training_milestone",
        titleRes = R.string.notif_cat_training_milestone,
        descRes = R.string.notif_cat_training_milestone_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_LOW,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "training",
    ),

    /** Chipset-DB snapshot updated (background sync). */
    CHIPSET_DB_UPDATE(
        channelId = "chipset_db_update",
        titleRes = R.string.notif_cat_chipset_db_update,
        descRes = R.string.notif_cat_chipset_db_update_desc,
        maxImportance = android.app.NotificationManager.IMPORTANCE_DEFAULT,
        defaultImportance = android.app.NotificationManager.IMPORTANCE_MIN,
        showBadge = false,
        allowSound = false,
        allowVibration = false,
        groupKey = "system",
    );

    companion object {
        fun fromChannelId(id: String): NotificationCategory? =
            entries.firstOrNull { it.channelId == id }
    }
}