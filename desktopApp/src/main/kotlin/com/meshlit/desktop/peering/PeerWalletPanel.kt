package com.meshlit.desktop.peering

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.meshlit.core.trust.*
import kotlinx.coroutines.*
import java.nio.file.Path
import javax.swing.JFileChooser

@Composable fun PeerWalletPanel() {
    val wallet = remember { PeerWallet(Path.of(System.getProperty("meshlit.desktop.dataDir", Path.of(System.getProperty("user.home"), ".meshlit", "desktop").toString())).resolve("mesh-wallet")) }
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var identity by remember { mutableStateOf<RecoverableIdentity?>(null) }
    var receipts by remember { mutableStateOf(emptyList<ContributionReceipt>()) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    DisposableEffect(wallet) { onDispose { wallet.lock() } }
    fun run(action: () -> Unit) {
        busy = true; message = null
        scope.launch {
            try { withContext(Dispatchers.IO) { action() }; identity = wallet.identity; receipts = withContext(Dispatchers.IO) { wallet.verifiedReceipts() }; passphrase = "" }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { message = e.message ?: "Wallet operation failed" }
            finally { busy = false }
        }
    }
    fun pick(save: Boolean): Path? {
        val picker = JFileChooser().apply { dialogTitle = if (save) "Save encrypted identity or signed receipts" else "Choose a mesh backup" }
        return if ((if (save) picker.showSaveDialog(null) else picker.showOpenDialog(null)) == JFileChooser.APPROVE_OPTION) picker.selectedFile.toPath() else null
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Your mesh identity", style = MaterialTheme.typography.h5)
        Text("One encrypted identity. Verifiable local contribution receipts. Explicit host access remains separate.")
        if (identity == null) {
            OutlinedTextField(passphrase, { passphrase = it }, label = { Text("Vault passphrase · at least 12 characters") }, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), enabled = !busy)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button({ val chars = passphrase.toCharArray(); run { try { if (wallet.exists) wallet.unlock(chars) else wallet.create(chars) } finally { chars.fill('\u0000') } } }, enabled = !busy && passphrase.length >= 12) { Text(if (wallet.exists) "Unlock identity" else "Create identity") }
                OutlinedButton({ pick(false)?.let { path -> val chars = passphrase.toCharArray(); run { try { require(java.nio.file.Files.size(path) <= 32768); wallet.restore(java.nio.file.Files.readString(path), chars) } finally { chars.fill('\u0000') } } } }, enabled = !busy && !wallet.exists && passphrase.length >= 12) { Text("Restore identity") }
            }
        } else {
            val key = identity!!
            Surface(elevation = 1.dp) { Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${receipts.sumOf { it.generatedTokens.toLong() }}", style = MaterialTheme.typography.h3)
                Text("Local contribution credits · ${receipts.size} signed jobs")
                SelectionContainer { Text("Node ${key.nodeId}\nFingerprint ${PeerCrypto.fingerprint(key.publicKey)}", style = MaterialTheme.typography.caption) }
            } }
            Text("Credits are self-attestations, not spendable MCC or proof of payment. Desktop hosting is not yet connected to this ledger; personal chat does not mint hosting rewards.")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton({ pick(true)?.let { path -> run { java.nio.file.Files.writeString(path, wallet.exportIdentity()) } } }, enabled = !busy) { Text("Back up encrypted identity") }
                OutlinedButton({ pick(true)?.let { path -> run { java.nio.file.Files.writeString(path, wallet.exportReceipts()) } } }, enabled = !busy) { Text("Export receipts") }
                TextButton({ pick(false)?.let { path -> run { require(java.nio.file.Files.size(path) <= 16 * 1024 * 1024); wallet.importReceipts(java.nio.file.Files.readString(path)) } } }, enabled = !busy) { Text("Import verified receipts") }
            }
            TextButton({ wallet.lock(); identity = null; receipts = emptyList(); passphrase = "" }, enabled = !busy) { Text("Lock identity") }
        }
        Text("Keep the encrypted backup file and passphrase together in your recovery plan. They restore the same node ID and signing key on Android or desktop.", style = MaterialTheme.typography.caption)
        message?.let { Text(it, color = MaterialTheme.colors.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
    }
}
