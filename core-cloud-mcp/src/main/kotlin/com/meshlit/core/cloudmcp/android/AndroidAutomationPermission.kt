package com.meshlit.core.cloudmcp.android

/**
 * Per-action permission policy for Android in-app automation.
 * Mirrors [com.meshlit.core.cloudmcp.browser.BrowserActionPermission]'s
 * shape so the agent loop emits the same permission-request
 * event regardless of whether the action targets the browser
 * or the device.
 *
 * Decision tree:
 *   1. **Sensitive target** — `app_type` into a password field,
 *      or any tool call into a high-risk package
 *      ([SettingsRepository.androidAutomationHighRiskPackagesFlow]).
 *      Always `Ask` (no allowlist override).
 *   2. **Allowlist hit** — when the [targetPackage] is in
 *      [SettingsRepository.androidAutomationAllowlistFlow], `Allow`.
 *   3. Default — `Ask`.
 *
 * The agent loop emits an [McpEvent.PermissionRequest] for
 * every `Ask` and waits for the user's response. The UI's
 * `AndroidAutomationConfirmDialog` shows the action verb,
 * the target app, and the parameters inline.
 */
class AndroidAutomationPermission(
    private val allowlist: Set<String> = emptySet(),
    private val highRiskPackages: Set<String> = emptySet(),
) {

    enum class Decision { Allow, Ask, Deny }

    /**
     * Evaluate [request] against the policy. Returns the
     * decision; the agent loop surfaces `Ask` to the user.
     */
    fun evaluate(request: AutomationRequest): Decision {
        // 1. Sensitive targets.
        if (request is AutomationRequest.TypeRequest && request.isPasswordField) {
            return Decision.Ask
        }
        if (request.targetPackage in highRiskPackages) {
            return Decision.Ask
        }
        // 2. Allowlist hit.
        if (request.targetPackage in allowlist) {
            return Decision.Allow
        }
        // 3. Default — confirm.
        return Decision.Ask
    }
}