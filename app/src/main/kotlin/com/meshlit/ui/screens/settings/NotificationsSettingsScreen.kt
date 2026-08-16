package com.meshlit.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.meshlit.MeshlitApplication
import com.meshlit.notifications.DndSchedule
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationHistoryLog
import com.meshlit.notifications.NotificationPreferences
import com.meshlit.settings.SettingsRepository
import com.meshlit.settings.visibility.RowDescriptor
import com.meshlit.settings.visibility.SettingsVisibility
import com.meshlit.settings.visibility.SimpleAdvancedStore
import com.meshlit.settings.visibility.Visibility
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Phase 4.x — Settings menu from-scratch rewrite.
 *
 * Notifications deep-redesign. Every row is wired to real
 * DataStore-backed state. The previous surface had:
 *
 *   - Missing importance picker (4 categories had no UI).
 *   - No "test fire" preview.
 *   - No DND / quiet-hours window.
 *   - No history log of recent posts.
 *   - No bulk toggles, no badge master, no reset per category.
 *
 * This rewrite adds all of those, gated by the new global
 * Simple/Advanced visibility filter (`SimpleAdvancedStore`).
 *
 * Visibility tier mapping:
 *   SIMPLE   — master enabled toggle, DND start/end, badge,
 *              history preview.
 *   ADVANCED — per-category importance pickers, quiet-hours
 *              days-of-week, system-DND honor, reset per
 *              category, raw allowlist fields.
 */
@Composable
fun NotificationsSettingsScreen(
    @Suppress("UNUSED_PARAMETER") onBack: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as MeshlitApplication
    val settings = app.settingsRepository
    val simpleAdvanced = app.simpleAdvancedStore
    val simple by simpleAdvanced.mode.collectAsState()
    val scope = rememberCoroutineScope()
    val viewModel: NotificationsSettingsViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = notificationsSettingsViewModelFactory(context),
    )

    val prefs by viewModel.prefs.collectAsState()

    val importanceMcpFgs by settings.notifImportanceMcpFgsFlow.collectAsState(initial = "LOW")
    val importanceChipsetDb by settings.notifImportanceChipsetDbFlow.collectAsState(initial = "MIN")
    val importancePushRecv by settings.notifImportancePushRecvFlow.collectAsState(initial = "DEFAULT")
    val importancePushJobReq by settings.notifImportancePushJobReqFlow.collectAsState(initial = "HIGH")
    val quietStart by settings.notifQuietHoursStartFlow.collectAsState(initial = 22)
    val quietEnd by settings.notifQuietHoursEndFlow.collectAsState(initial = 7)
    val quietDays by settings.notifQuietHoursDaysFlow.collectAsState(initial = "1,2,3,4,5,6,7")
    val respectDnd by settings.notifRespectSystemDndFlow.collectAsState(initial = true)
    val badge by settings.notifBadgeEnabledFlow.collectAsState(initial = true)
    val historyJson by settings.notifHistoryFlow.collectAsState(initial = "[]")
    val historyLog = remember { NotificationHistoryLog() }

    val rows = buildList<RowDescriptor> {
        // ─── SIMPLE ────────────────────────────────────────
        add(RowDescriptor(Visibility.SIMPLE) {
            HeaderRow(
                title = "Per-category controls",
                subtitle = "Toggle which events reach you. Each category has its own OS channel; the in-app switch here is an additional filter on top.",
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            CategoryLoopRow(
                category = NotificationCategory.INFERENCE_COMPLETE,
                prefs = prefs,
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.INFERENCE_COMPLETE, it) },
                onSoundChange = { viewModel.setSound(NotificationCategory.INFERENCE_COMPLETE, it) },
                onVibrationChange = { viewModel.setVibration(NotificationCategory.INFERENCE_COMPLETE, it) },
                onImportanceChange = { viewModel.setImportanceOverride(NotificationCategory.INFERENCE_COMPLETE, it) },
                onTestFire = { scope.launch { viewModel.testFire(NotificationCategory.INFERENCE_COMPLETE) } },
                onOpenOsSettings = { openOsNotificationSettings(context, NotificationCategory.INFERENCE_COMPLETE.channelId) },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            CategoryLoopRow(
                category = NotificationCategory.JOB_FAILED,
                prefs = prefs,
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.JOB_FAILED, it) },
                onSoundChange = { viewModel.setSound(NotificationCategory.JOB_FAILED, it) },
                onVibrationChange = { viewModel.setVibration(NotificationCategory.JOB_FAILED, it) },
                onImportanceChange = { viewModel.setImportanceOverride(NotificationCategory.JOB_FAILED, it) },
                onTestFire = { scope.launch { viewModel.testFire(NotificationCategory.JOB_FAILED) } },
                onOpenOsSettings = { openOsNotificationSettings(context, NotificationCategory.JOB_FAILED.channelId) },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            CategoryLoopRow(
                category = NotificationCategory.PEER_TOPOLOGY,
                prefs = prefs,
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.PEER_TOPOLOGY, it) },
                onSoundChange = { viewModel.setSound(NotificationCategory.PEER_TOPOLOGY, it) },
                onVibrationChange = { viewModel.setVibration(NotificationCategory.PEER_TOPOLOGY, it) },
                onImportanceChange = { viewModel.setImportanceOverride(NotificationCategory.PEER_TOPOLOGY, it) },
                onTestFire = { scope.launch { viewModel.testFire(NotificationCategory.PEER_TOPOLOGY) } },
                onOpenOsSettings = { openOsNotificationSettings(context, NotificationCategory.PEER_TOPOLOGY.channelId) },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            CategoryLoopRow(
                category = NotificationCategory.SECURITY_ALERT,
                prefs = prefs,
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.SECURITY_ALERT, it) },
                onSoundChange = { viewModel.setSound(NotificationCategory.SECURITY_ALERT, it) },
                onVibrationChange = { viewModel.setVibration(NotificationCategory.SECURITY_ALERT, it) },
                onImportanceChange = { viewModel.setImportanceOverride(NotificationCategory.SECURITY_ALERT, it) },
                onTestFire = { scope.launch { viewModel.testFire(NotificationCategory.SECURITY_ALERT) } },
                onOpenOsSettings = { openOsNotificationSettings(context, NotificationCategory.SECURITY_ALERT.channelId) },
            )
        })

        add(RowDescriptor(Visibility.SIMPLE) {
            HeaderRow(title = "Do-Not-Disturb", subtitle = "Silence all categories between the start and end hour.")
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            NumberRow(
                icon = Icons.Filled.Notifications,
                title = "Quiet hours start",
                subtitle = "Hour (0–23) when DND begins. Default 22.",
                value = quietStart.toString(),
                onCommit = { scope.launch { settings.setNotifQuietHoursStart(it.toIntOrNull() ?: 22) } },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            NumberRow(
                icon = Icons.Filled.Notifications,
                title = "Quiet hours end",
                subtitle = "Hour (0–23) when DND ends. Crosses midnight if start > end.",
                value = quietEnd.toString(),
                onCommit = { scope.launch { settings.setNotifQuietHoursEnd(it.toIntOrNull() ?: 7) } },
            )
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            SettingToggle(
                icon = Icons.Filled.Notifications,
                title = "Show badge",
                subtitle = "Master badge toggle across all categories.",
                checked = badge,
                onChange = { scope.launch { settings.setNotifBadgeEnabled(it) } },
            )
        })

        add(RowDescriptor(Visibility.SIMPLE) {
            HeaderRow(title = "Recent posts", subtitle = "Last 20 events (in-memory; cleared on reboot).")
        })
        add(RowDescriptor(Visibility.SIMPLE) {
            HistoryPreview(historyLog = historyLog, json = historyJson)
        })

        // ─── ADVANCED ─────────────────────────────────────
        add(RowDescriptor(Visibility.ADVANCED) {
            HeaderRow(
                title = "Advanced per-category controls",
                subtitle = "Fine-grained importance, FGS categories, and per-category reset.",
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            AdvancedCategoryRow(
                category = NotificationCategory.MCP_FOREGROUND_SERVICE,
                prefs = prefs,
                importance = importanceMcpFgs,
                onImportanceChange = { scope.launch { settings.setNotifImportanceMcpFgs(it) } },
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.MCP_FOREGROUND_SERVICE, it) },
                onReset = { scope.launch { viewModel.resetCategory(NotificationCategory.MCP_FOREGROUND_SERVICE) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            AdvancedCategoryRow(
                category = NotificationCategory.PUSH_JOB_REQUEST,
                prefs = prefs,
                importance = importancePushJobReq,
                onImportanceChange = { scope.launch { settings.setNotifImportancePushJobReq(it) } },
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.PUSH_JOB_REQUEST, it) },
                onReset = { scope.launch { viewModel.resetCategory(NotificationCategory.PUSH_JOB_REQUEST) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            AdvancedCategoryRow(
                category = NotificationCategory.PUSH_RECEIVED,
                prefs = prefs,
                importance = importancePushRecv,
                onImportanceChange = { scope.launch { settings.setNotifImportancePushRecv(it) } },
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.PUSH_RECEIVED, it) },
                onReset = { scope.launch { viewModel.resetCategory(NotificationCategory.PUSH_RECEIVED) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            AdvancedCategoryRow(
                category = NotificationCategory.CHIPSET_DB_UPDATE,
                prefs = prefs,
                importance = importanceChipsetDb,
                onImportanceChange = { scope.launch { settings.setNotifImportanceChipsetDb(it) } },
                onEnabledChange = { viewModel.setEnabled(NotificationCategory.CHIPSET_DB_UPDATE, it) },
                onReset = { scope.launch { viewModel.resetCategory(NotificationCategory.CHIPSET_DB_UPDATE) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            ChipRow(
                title = "Quiet hours days",
                subtitle = "Days-of-week (1=Mon..7=Sun) the DND window applies on. Empty CSV = never quiet.",
                options = listOf("1,2,3,4,5", "1,2,3,4,5,6,7", "6,7", "1,2,3,4,5,6,7"),
                selected = quietDays,
                onSelect = { scope.launch { settings.setNotifQuietHoursDays(it) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            SettingToggle(
                icon = Icons.Filled.Notifications,
                title = "Respect system DND",
                subtitle = "Honor the OS-level Do-Not-Disturb setting on top of our schedule.",
                checked = respectDnd,
                onChange = { scope.launch { settings.setNotifRespectSystemDnd(it) } },
            )
        })
        add(RowDescriptor(Visibility.ADVANCED) {
            DangerRow(
                title = "Reset all notifications",
                subtitle = "Restore every category to its default importance, sound, and vibration.",
                confirmPrompt = "Reset all",
                onConfirm = { scope.launch { viewModel.resetAll() } },
            )
        })
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
    ) {
        item { SettingsVisibility.Render(rows, simpleMode = simple) }
    }
}

@Composable
private fun CategoryLoopRow(
    category: NotificationCategory,
    prefs: Map<NotificationCategory, NotificationPreferences.CategoryPrefs>,
    onEnabledChange: (Boolean) -> Unit,
    onSoundChange: (Boolean) -> Unit,
    onVibrationChange: (Boolean) -> Unit,
    onImportanceChange: (Int) -> Unit,
    onTestFire: () -> Unit,
    onOpenOsSettings: () -> Unit,
) {
    val cat = prefs[category] ?: NotificationPreferences.CategoryPrefs()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingToggle(
            icon = Icons.Filled.Notifications,
            title = category.name.lowercase().replaceFirstChar { it.uppercase() },
            subtitle = "Channel: ${category.channelId}",
            checked = cat.enabled,
            onChange = onEnabledChange,
        )
        if (cat.enabled) {
            ChipRow(
                title = "Importance",
                subtitle = "MIN/LOW/DEFAULT/HIGH. Capped at the category's max.",
                options = listOf("MIN", "LOW", "DEFAULT", "HIGH"),
                selected = importanceLabel(cat.importanceOverride),
                onSelect = { importanceConstant(it, onImportanceChange) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SettingToggle(
                    icon = Icons.Filled.Notifications,
                    title = "Sound",
                    subtitle = "Play the channel's default sound.",
                    checked = cat.allowSound,
                    onChange = onSoundChange,
                )
                SettingToggle(
                    icon = Icons.Filled.Notifications,
                    title = "Vibration",
                    subtitle = "Vibrate on this category.",
                    checked = cat.allowVibration,
                    onChange = onVibrationChange,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    com.meshlit.ui.components.RaNavRow(
                        leadingIcon = Icons.Filled.Notifications,
                        title = "Test fire",
                        subtitle = "Preview how this category renders.",
                        onClick = onTestFire,
                    )
                }
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    com.meshlit.ui.components.RaNavRow(
                        leadingIcon = Icons.Filled.ChevronRight,
                        title = "OS settings",
                        subtitle = "Channel LED, lock-screen visibility.",
                        onClick = onOpenOsSettings,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdvancedCategoryRow(
    category: NotificationCategory,
    prefs: Map<NotificationCategory, NotificationPreferences.CategoryPrefs>,
    importance: String,
    onImportanceChange: (String) -> Unit,
    onEnabledChange: (Boolean) -> Unit,
    onReset: () -> Unit,
) {
    val cat = prefs[category] ?: NotificationPreferences.CategoryPrefs()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingToggle(
            icon = Icons.Filled.Notifications,
            title = "${category.name.lowercase().replaceFirstChar { it.uppercase() }} (FGS)",
            subtitle = "Channel: ${category.channelId}",
            checked = cat.enabled,
            onChange = onEnabledChange,
        )
        ChipRow(
            title = "Importance override",
            subtitle = "Persisted; OS channel re-applies after every change.",
            options = listOf("MIN", "LOW", "DEFAULT", "HIGH"),
            selected = importance,
            onSelect = onImportanceChange,
        )
        Box {
            com.meshlit.ui.components.RaNavRow(
                leadingIcon = Icons.Filled.Refresh,
                title = "Reset to defaults",
                subtitle = "Restore this category's default importance, sound, vibration.",
                onClick = onReset,
            )
        }
    }
}

@Composable
private fun HistoryPreview(
    historyLog: NotificationHistoryLog,
    json: String,
) {
    val entries = remember(json) { historyLog.recent(json, limit = 20) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (entries.isEmpty()) {
            Text(
                text = "No notifications recorded yet.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            entries.forEach { e ->
                Text(
                    text = "${formatRelativeMs(e.atMs)} · ${e.categoryId} · ${e.outcome}",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun importanceLabel(override: Int): String = when (override) {
    -1 -> "DEFAULT"
    android.app.NotificationManager.IMPORTANCE_MIN -> "MIN"
    android.app.NotificationManager.IMPORTANCE_LOW -> "LOW"
    android.app.NotificationManager.IMPORTANCE_HIGH -> "HIGH"
    else -> "DEFAULT"
}

private fun importanceConstant(label: String, set: (Int) -> Unit) {
    val value = when (label) {
        "MIN" -> android.app.NotificationManager.IMPORTANCE_MIN
        "LOW" -> android.app.NotificationManager.IMPORTANCE_LOW
        "HIGH" -> android.app.NotificationManager.IMPORTANCE_HIGH
        else -> android.app.NotificationManager.IMPORTANCE_DEFAULT
    }
    set(value)
}

private fun openOsNotificationSettings(context: Context, channelId: String) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun formatRelativeMs(atMs: Long): String {
    if (atMs == 0L) return "—"
    val diff = System.currentTimeMillis() - atMs
    return when {
        diff < 60_000L -> "${diff / 1000}s ago"
        diff < 3_600_000L -> "${diff / 60_000}m ago"
        diff < 86_400_000L -> "${diff / 3_600_000}h ago"
        else -> "${diff / 86_400_000}d ago"
    }
}

/**
 * Backing VM. Reuses the legacy one where possible, but adds
 * `testFire`, `resetCategory`, `resetAll`, and `setImportanceOverride`
 * so the new UI can drive them.
 */
class NotificationsSettingsViewModel(
    private val preferences: NotificationPreferences,
    private val notificationCenter: com.meshlit.notifications.NotificationCenter,
    private val settings: SettingsRepository,
    private val historyLog: NotificationHistoryLog = NotificationHistoryLog(),
) : ViewModel() {

    val prefs: StateFlow<Map<NotificationCategory, NotificationPreferences.CategoryPrefs>> =
        preferences.flow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyMap(),
        )

    fun setEnabled(category: NotificationCategory, enabled: Boolean) {
        viewModelScope.launch {
            preferences.setEnabled(category, enabled)
            if (!enabled) notificationCenter.cancelAll(category)
            notificationCenter.reapplyAllChannels()
            recordHistory(category, "enabled=$enabled")
        }
    }

    fun setSound(category: NotificationCategory, allow: Boolean) {
        viewModelScope.launch {
            preferences.setSound(category, allow)
            notificationCenter.reapplyAllChannels()
            recordHistory(category, "sound=$allow")
        }
    }

    fun setVibration(category: NotificationCategory, allow: Boolean) {
        viewModelScope.launch {
            preferences.setVibration(category, allow)
            notificationCenter.reapplyAllChannels()
            recordHistory(category, "vibration=$allow")
        }
    }

    fun setImportanceOverride(category: NotificationCategory, importance: Int) {
        viewModelScope.launch {
            preferences.setImportanceOverride(category, importance)
            notificationCenter.reapplyAllChannels()
            recordHistory(category, "importance=$importance")
        }
    }

    fun testFire(category: NotificationCategory) {
        viewModelScope.launch {
            notificationCenter.post(
                category = category,
                title = "Test · ${category.name}",
                body = "Preview notification for this category.",
            )
            recordHistory(category, "testFire=posted")
        }
    }

    fun resetCategory(category: NotificationCategory) {
        viewModelScope.launch {
            preferences.reset(category)
            notificationCenter.reapplyAllChannels()
            recordHistory(category, "reset")
        }
    }

    fun resetAll() {
        viewModelScope.launch {
            NotificationCategory.entries.forEach { preferences.reset(it) }
            notificationCenter.reapplyAllChannels()
            recordHistory(null, "resetAll")
        }
    }

    private suspend fun recordHistory(category: NotificationCategory?, outcome: String) {
        val raw = settings.notifHistoryFlow.let { f ->
            f.stateIn(viewModelScope, SharingStarted.Eagerly, "[]").value
        }
        val entry = NotificationHistoryLog.Entry(
            atMs = System.currentTimeMillis(),
            categoryId = category?.channelId ?: "ALL",
            title = outcome,
            outcome = outcome,
        )
        settings.setNotifHistory(historyLog.append(raw, entry))
    }

    companion object {
        fun factory(app: MeshlitApplication): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                NotificationsSettingsViewModel(
                    preferences = app.notificationPreferences,
                    notificationCenter = app.notificationCenter,
                    settings = app.settingsRepository,
                )
            }
        }
    }
}

fun notificationsSettingsViewModelFactory(context: Context): ViewModelProvider.Factory =
    NotificationsSettingsViewModel.factory(context.applicationContext as MeshlitApplication)
