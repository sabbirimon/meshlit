package com.meshlit.setup

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.meshlit.core.common.OemProfile
import com.meshlit.core.common.OemSetupStep
import com.meshlit.core.common.logger
import com.meshlit.notifications.NotificationCategory
import com.meshlit.notifications.NotificationCenter
import kotlinx.coroutines.flow.first

/**
 * Bridges the data layer (which OEM was detected, which steps are
 * done) to the platform layer (which Intents to fire for each step)
 * and the UI layer (whether to show the wizard).
 *
 * Two roles:
 *  1. Decide whether [shouldShowWizard] is true at startup
 *  2. Provide the correct [Intent] for each step so the "Take me
 *     there" button can launch it
 *
 * Step → Intent mapping:
 *  - NOTIFICATION_PERMISSION   → AOSP Settings (Android 13+ POST_NOTIFICATIONS)
 *  - BATTERY_WHITELIST         → AOSP ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
 *  - BATTERY_SAVER_DISABLE     → OEM deep link (e.g. MIUI's protected-apps screen)
 *  - AUTOSTART_PERMISSION      → OEM deep link (e.g. MIUI's autostart screen)
 *  - MI_PUSH_OPT_IN            → AOSP Settings → Apps → Meshlit → Notifications
 *  - HMS_PUSH_OPT_IN           → AOSP Settings → Apps → Meshlit → Notifications
 *  - HARMONYOS_COMPAT_LAYER_CHECK → AOSP Settings (just a flag — no system intent)
 */
class SetupCoordinator(
    private val context: Context,
    private val repository: FirstRunSetupRepository,
    private val notificationCenter: NotificationCenter,
) {

    private val log = logger("SetupCoordinator")

    /** True when at least one required step is incomplete. The
     *  MainActivity calls this at startup and shows the wizard. */
    suspend fun shouldShowWizard(profile: OemProfile): Boolean {
        if (profile.setupSteps.isEmpty()) return false
        val done = repository.completedStepsFlow.first()
        return profile.setupSteps.any { step -> step !in done }
    }

    /** Build the platform Intent for a given step. Returns `null`
     *  if no actionable Intent exists (the step is just a checkbox). */
    fun intentForStep(step: OemSetupStep, profile: OemProfile): Intent? {
        return when (step) {
            OemSetupStep.NOTIFICATION_PERMISSION -> notificationSettingsIntent()
            OemSetupStep.BATTERY_WHITELIST -> batteryOptimizationIntent()
            OemSetupStep.BATTERY_SAVER_DISABLE -> batterySaverIntent(profile)
            OemSetupStep.AUTOSTART_PERMISSION -> autostartIntent(profile)
            OemSetupStep.MI_PUSH_OPT_IN,
            OemSetupStep.HMS_PUSH_OPT_IN -> notificationSettingsIntent()
            OemSetupStep.HARMONYOS_COMPAT_LAYER_CHECK -> null  // no system intent; manual flag
        }
    }

    /** Mark a step done after the user confirms. We don't auto-detect
     *  completion — the user is the source of truth. */
    suspend fun completeStep(step: OemSetupStep) {
        repository.markStepDone(step)
        log.info("setup.step_done", "marked step done", mapOf("step" to step.tag))
    }

    /** Mark all remaining steps done (e.g. "Skip setup for now"). */
    suspend fun completeAll(profile: OemProfile) {
        profile.setupSteps.forEach { repository.markStepDone(it) }
        repository.setFirstRunFinished(true)
        log.info("setup.skip_all", "skipped remaining steps", mapOf("count" to profile.setupSteps.size))
    }

    /** Reset everything. Wired to Settings → OEM Setup → Reset. */
    suspend fun reset() {
        repository.resetAll()
        log.info("setup.reset", "first-run setup reset")
    }

    /** User is done with the wizard. Persists the flag. */
    suspend fun finish() {
        repository.setFirstRunFinished(true)
    }

    /** Diagnostic: list of incomplete steps for the current profile. */
    suspend fun remainingSteps(profile: OemProfile): List<OemSetupStep> {
        val done = repository.completedStepsFlow.first()
        return profile.setupSteps.filter { step -> step !in done }
    }

    /** Undo a single step (used by the "Undo" button in the wizard). */
    suspend fun undoStep(step: OemSetupStep) {
        repository.markStepUndone(step)
    }

    // --- Intent builders --------------------------------------------------

    private fun notificationSettingsIntent(): Intent =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", context.packageName, null))
        }

    private fun batteryOptimizationIntent(): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            .setData(Uri.parse("package:${context.packageName}"))

    /** Battery-saver disable is OEM-specific. Stock Android only has
     *  the AOSP battery-optimization screen (handled above). MIUI has
     *  a deeper "protected apps" screen. */
    private fun batterySaverIntent(profile: OemProfile): Intent? {
        val intentString = profile.batteryProtectedAppsIntent ?: return null
        return runCatching { Intent(intentString).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
    }

    private fun autostartIntent(profile: OemProfile): Intent? {
        val intentString = profile.autostartSettingsIntent ?: return null
        return runCatching { Intent(intentString).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }.getOrNull()
    }

    companion object {
        /** Notification categories that are gated behind setup. Used
         *  by [NotificationCenter] to suppress noise during setup. */
        val SUPPRESSED_DURING_SETUP: Set<NotificationCategory> = setOf(
            NotificationCategory.MODEL_IMPORT_COMPLETE,
            NotificationCategory.TRAINING_MILESTONE,
            NotificationCategory.CHIPSET_DB_UPDATE,
        )
    }
}