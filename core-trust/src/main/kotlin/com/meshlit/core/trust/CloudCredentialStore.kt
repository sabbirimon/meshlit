package com.meshlit.core.trust

import android.content.Context

/**
 * Namespaced credential store for cloud-MCP providers. Every key
 * is prefixed with `cloud-mcp/<providerId>/` so the keystore
 * cleanly separates cloud tokens from other encrypted secrets
 * (auth tokens, etc.) that may land in the same backing file.
 *
 * The providerId is the same id used in
 * [com.meshlit.core.cloudmcp.ProviderConfig.id] — usually a slug
 * like `aws-prod`, `do-team-a`, `corp-vpn`. The credentialRef is
 * stored inside `ProviderConfig` and resolved at connect time.
 *
 * Stored values are encrypted via
 * [EncryptedCredentialStore] (Android Keystore + AES256/GCM).
 */
class CloudCredentialStore(
    context: Context,
    prefsName: String = "cloud_mcp_credentials",
) : EncryptedCredentialStore(context, prefsName) {

    private fun keyFor(providerId: String, name: String): String =
        "$NAMESPACE$providerId/$name"

    private fun parseProvider(providerId: String, raw: String): String =
        raw.substringAfter("$NAMESPACE$providerId/")

    /**
     * Persist a credential for [providerId] under [name].
     * Common names: `token`, `access_token`, `refresh_token`,
     * `access_key_id`, `secret_access_key`.
     */
    fun put(providerId: String, name: String, value: String) {
        put(keyFor(providerId, name), value)
    }

    /**
     * Resolve a credential previously stored by [put]. Returns
     * null if the provider or name is unknown.
     */
    fun get(providerId: String, name: String): String? =
        get(keyFor(providerId, name))

    /** Drop a single credential entry. */
    fun remove(providerId: String, name: String) {
        remove(keyFor(providerId, name))
    }

    /**
     * List every credential name currently stored under
     * [providerId]. Used by the Cloud Hub UI to show "AWS: 2
     * credentials stored".
     */
    fun listFor(providerId: String): Set<String> = list()
        .filter { it.startsWith("$NAMESPACE$providerId/") }
        .map { parseProvider(providerId, it) }
        .toSet()

    /** Drop every credential for [providerId]. Called on provider delete. */
    fun purgeProvider(providerId: String) {
        val prefix = "$NAMESPACE$providerId/"
        list().filter { it.startsWith(prefix) }.forEach { remove(it) }
    }

    companion object {
        const val NAMESPACE = "cloud-mcp/"
    }
}