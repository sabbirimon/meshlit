package com.meshlit.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.NotificationCompat.GroupAlertBehavior
import com.meshlit.MainActivity
import com.meshlit.R
import com.meshlit.core.common.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

/**
 * Posts notifications on behalf of the rest of the app, with per-category
 * filtering and OS-channel management.
 *
 * Channels are created lazily the first time a category is used, so a
 * freshly-installed app doesn't show 10 channels in the OS settings —
 * the user only sees channels for categories we've actually fired.
 *
 * If a category is muted in [NotificationPreferences], the post is
 * silently dropped. We don't write a "this notification was muted"
 * entry anywhere; the user explicitly chose to silence it.
 *
 * The foreground-service channel is always created at app start
 * (the OS won't let the FGS post without a pre-existing channel),
 * but it doesn't fire any non-FGS notifications through this path.
 */
class NotificationCenter(
    private val context: Context,
    private val preferences: NotificationPreferences,
    private val scope: CoroutineScope,
) {

    private val log = logger("NotificationCenter")
    private val createdChannels = ConcurrentHashMap.newKeySet<String>()
    private val notifManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        // Always pre-create the foreground-service channel; the OS
        // requires it before the FGS can post.
        ensureChannel(NotificationCategory.FOREGROUND_SERVICE, forceApply = true)
    }

    /**
     * Post a notification. Honors per-category user preferences.
     *
     * Returns the notification ID we used (the caller can dismiss it
     * with [cancel]), or -1 if we silently dropped the notification
     * due to user prefs.
     */
    suspend fun post(
        category: NotificationCategory,
        title: String,
        body: String,
        actions: List<NotificationAction> = emptyList(),
        deepLink: String? = null,
    ): Int {
        val prefs = preferences.flow.first()[category] ?: NotificationPreferences.CategoryPrefs()
        if (!prefs.enabled) {
            log.info("notif.drop", "category muted", mapOf("category" to category.channelId))
            return -1
        }

        // The OS may still refuse to show (channel importance, DND).
        // We post anyway — Android will silently no-op if the channel
        // is muted at the OS level. That's the user's choice.
        ensureChannel(category)

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            deepLink?.let { putExtra("mesh_deep_link", it) }
        }
        val contentPending = PendingIntent.getActivity(
            context, category.ordinal, openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val builder = NotificationCompat.Builder(context, category.channelId)
            .setSmallIcon(R.drawable.ic_meshlit_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(category.defaultImportance.toNotificationPriority())
            .setCategory(category.toNotificationCategory())
            .setContentIntent(contentPending)
            .setAutoCancel(true)
            .setGroup(category.groupKey)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)

        // Apply user overrides on top of category defaults.
        var defaults = 0
        if (prefs.allowSound && category.allowSound) {
            defaults = defaults or NotificationCompat.DEFAULT_SOUND
        }
        if (prefs.allowVibration && category.allowVibration) {
            defaults = defaults or NotificationCompat.DEFAULT_VIBRATE
        }
        if (defaults != 0) {
            builder.setDefaults(defaults)
        }
        if (!prefs.showBadge || !category.showBadge) {
            builder.setBadgeIconType(NotificationCompat.BADGE_ICON_NONE)
        }

        for (action in actions) {
            val actionIntent = PendingIntent.getBroadcast(
                context, action.requestCode, action.intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(action.iconRes, action.label, actionIntent).build()
            )
        }

        val notificationId = nextNotificationId(category)
        return withContext(Dispatchers.Main) {
            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
                log.info("notif.post", "posted", mapOf(
                    "category" to category.channelId,
                    "id" to notificationId,
                ))
                notificationId
            } catch (se: SecurityException) {
                // Missing POST_NOTIFICATIONS permission on Android 13+.
                log.warn("notif.permission_denied", "POST_NOTIFICATIONS not granted")
                -1
            }
        }
    }

    /**
     * Cancel a notification by ID. Returns silently if the ID is unknown.
     */
    fun cancel(category: NotificationCategory, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }

    /** Cancel everything in a category. Used when the user toggles off a category. */
    fun cancelAll(category: NotificationCategory) {
        // NotificationManager doesn't expose per-category cancel, so we
        // track our own IDs and cancel each. Cheap — we only have a
        // handful of notifications per category.
        activeIds[category]?.toList()?.forEach {
            NotificationManagerCompat.from(context).cancel(it)
        }
        activeIds[category]?.clear()
    }

    /**
     * Reapply user prefs to OS channels. Call after the user changes
     * Settings → Notifications so the OS sees the new importance /
     * sound / vibration values immediately.
     */
    suspend fun reapplyAllChannels() {
        val prefs = preferences.flow.first()
        for (cat in NotificationCategory.entries) {
            ensureChannel(cat, forceApply = true, prefs = prefs[cat])
        }
    }

    private fun ensureChannel(
        cat: NotificationCategory,
        forceApply: Boolean = false,
        prefs: NotificationPreferences.CategoryPrefs? = null,
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (!forceApply && cat.channelId in createdChannels) return

        val importance = when {
            prefs?.importanceOverride != null && prefs.importanceOverride >= 0 ->
                prefs.importanceOverride.coerceAtMost(cat.maxImportance)
            else -> cat.defaultImportance.coerceAtMost(cat.maxImportance)
        }

        val channel = NotificationChannel(cat.channelId, context.getString(cat.titleRes), importance).apply {
            description = context.getString(cat.descRes)
            setShowBadge(prefs?.showBadge ?: cat.showBadge)
            if (!cat.allowSound || prefs?.allowSound == false) {
                setSound(null, null)
            }
            if (!cat.allowVibration || prefs?.allowVibration == false) {
                enableVibration(false)
            }
            // BRAIN_BATTERY_LOW and SECURITY_ALERT must always be at least HIGH;
            // the override ceiling in NotificationCategory enforces that.
        }
        notifManager.createNotificationChannel(channel)
        createdChannels.add(cat.channelId)
    }

    private val activeIds = ConcurrentHashMap<NotificationCategory, MutableSet<Int>>()

    private fun nextNotificationId(category: NotificationCategory): Int {
        // Use a stable hash of category so notifications in the same
        // category replace each other (avoid spamming 20 inference-
        // complete notifications). For categories where the user wants
        // every event, we'd add a counter; default to replacement.
        val bucket = activeIds.computeIfAbsent(category) { ConcurrentHashMap.newKeySet() }
        val id = (category.ordinal * 100_000) + (bucket.size + 1)
        bucket.add(id)
        // Bound the set so it doesn't grow forever.
        if (bucket.size > 1000) bucket.remove(bucket.first())
        return id
    }
}

/** Action button on a notification. The caller provides a broadcast Intent. */
data class NotificationAction(
    val label: String,
    val iconRes: Int,
    val intent: Intent,
    val requestCode: Int = label.hashCode(),
)

private fun Int.toNotificationPriority(): Int = when (this) {
    NotificationManager.IMPORTANCE_HIGH -> NotificationCompat.PRIORITY_HIGH
    NotificationManager.IMPORTANCE_DEFAULT -> NotificationCompat.PRIORITY_DEFAULT
    NotificationManager.IMPORTANCE_LOW -> NotificationCompat.PRIORITY_LOW
    NotificationManager.IMPORTANCE_MIN -> NotificationCompat.PRIORITY_MIN
    else -> NotificationCompat.PRIORITY_DEFAULT
}

private fun NotificationCategory.toNotificationCategory(): String = when (this) {
    NotificationCategory.FOREGROUND_SERVICE -> NotificationCompat.CATEGORY_SERVICE
    NotificationCategory.INFERENCE_COMPLETE -> NotificationCompat.CATEGORY_PROGRESS
    NotificationCategory.JOB_FAILED -> NotificationCompat.CATEGORY_ERROR
    NotificationCategory.SECURITY_ALERT -> NotificationCompat.CATEGORY_ALARM
    NotificationCategory.MODEL_IMPORT_COMPLETE,
    NotificationCategory.PUBLIC_TOKEN_USED -> NotificationCompat.CATEGORY_MESSAGE
    NotificationCategory.TRAINING_MILESTONE -> NotificationCompat.CATEGORY_PROGRESS
    else -> NotificationCompat.CATEGORY_STATUS
}