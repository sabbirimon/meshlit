package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import kotlinx.coroutines.*

/** Human-only controls; unlocking never enables network access or selects a credential. */
@Composable internal fun CredentialControls(
    vault: DesktopCredentials, endpoint: String, protocol: HostProtocol, token: String,
    invalidate: () -> Unit, use: (DesktopCredentials.Lease, String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(vault.isUnlocked()) }
    var working by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    fun action(success: String, operation: () -> Unit) {
        if (working) return
        working = true; message = ""; invalidate()
        scope.launch {
            try { withContext(Dispatchers.IO) { operation() }; message = success }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { message = "Could not complete credential action. Check the host, passphrase and vault access." }
            finally { unlocked = vault.isUnlocked(); working = false }
        }
    }
    TextButton({ expanded = !expanded }, enabled = !working) { Text(if (expanded) "Hide saved credentials" else "Saved credentials (optional)") }
    if (!expanded) return
    Text("Encrypted on this device with a separate passphrase. No recovery if it is lost. Keys are bound to this host, protocol and interactive inference; they do not grant agent access.", style = MaterialTheme.typography.caption)
    if (!unlocked) {
        OutlinedTextField(password, { password = it.take(256) }, enabled = !working,
            label = { Text("Credential passphrase · 12–256 characters") },
            visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Button({
            val chars = password.toCharArray(); password = ""
            action("Credential vault unlocked") { try { vault.unlock(chars) } finally { chars.fill('\u0000') } }
        }, enabled = !working && password.length >= 12) { Text("Unlock / create vault") }
    } else {
        Row {
            TextButton({ action("Saved for this host and protocol") { vault.save(HostEndpoint.parse(endpoint, protocol), token) } }, enabled = !working && token.isNotEmpty()) { Text("Save current token") }
            TextButton({
                invalidate()
                try {
                    val host = HostEndpoint.parse(endpoint, protocol)
                    val lease = vault.lease(host)
                    use(lease, vault.resolve(lease, host)); message = "Loaded for this host; enable access and connect explicitly"
                } catch (_: Exception) { message = "No usable saved credential for this host and protocol" }
            }, enabled = !working) { Text("Use saved token") }
        }
        Row {
            TextButton({ action("Saved credential removed") { vault.remove(HostEndpoint.parse(endpoint, protocol)) } }, enabled = !working) { Text("Forget this host") }
            TextButton({ invalidate(); vault.lock(); unlocked = false; password = ""; message = "Credentials locked" }, enabled = !working) { Text("Lock and clear token") }
        }
    }
    if (message.isNotEmpty()) Text(message, style = MaterialTheme.typography.caption)
}
