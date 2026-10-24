package com.meshlit.peering

import android.content.Context
import android.util.AtomicFile
import com.meshlit.core.trust.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import java.io.File

data class ContributionState(val credits: Long = 0, val jobs: Int = 0, val enabled: Boolean = false, val error: String? = null, val walletId: String = "", val recent: List<ContributionReceipt> = emptyList())
class ContributionStore(context: Context, private val identity: DeviceIdentity, private val nodeId: () -> String) {
    private val file = AtomicFile(File(context.noBackupFilesDir, "contributions.json"))
    private val prefs = context.getSharedPreferences("mesh_rewards", Context.MODE_PRIVATE)
    private val serializer = ListSerializer(ContributionReceipt.serializer())
    private val mutable = MutableStateFlow(ContributionState(enabled = prefs.getBoolean("enabled", false)))
    val state = mutable.asStateFlow()
    private fun snapshot(entries: List<ContributionReceipt>, key: RecoverableIdentity) = ContributionState(
        entries.sumOf { it.generatedTokens.toLong() }, entries.size,
        prefs.getBoolean("enabled", false), walletId = PeerCrypto.fingerprint(key.publicKey),
        recent = entries.takeLast(8).reversed())
    @Synchronized fun refresh() {
        try {
            val key = identity.get(nodeId()); val entries = read()
            check(ContributionLedger.verify(entries, key.nodeId, key.publicKey)) { "Receipt verification failed" }
            mutable.value = snapshot(entries, key)
        } catch (e: Exception) { mutable.value = ContributionState(enabled = prefs.getBoolean("enabled", false), error = e.message ?: "Could not read rewards") }
    }
    @Synchronized fun setEnabled(enabled: Boolean) {
        check(prefs.edit().putBoolean("enabled", enabled).commit())
        mutable.value = mutable.value.copy(enabled = enabled)
        refresh()
    }
    @Synchronized fun record(jobId: String, tokens: Int) {
        if (!prefs.getBoolean("enabled", false) || tokens <= 0) return
        try {
            val key = identity.get(nodeId()); val entries = read()
            val updated = ContributionLedger.append(entries, key, jobId, tokens)
            if (updated.size == entries.size) return
            check(updated.size <= 10000) { "Contribution receipt limit reached" }
            val stream = file.startWrite()
            try { stream.write(PeerCrypto.json.encodeToString(serializer, updated).toByteArray()); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
            mutable.value = snapshot(updated, key)
        } catch (e: Exception) { mutable.value = mutable.value.copy(error = e.message ?: "Could not save receipt") }
    }
    @Synchronized fun exportReceipts(): String {
        val key = identity.get(nodeId()); val entries = read()
        check(ContributionLedger.verify(entries, key.nodeId, key.publicKey)) { "Receipt verification failed" }
        return PeerCrypto.json.encodeToString(serializer, entries)
    }
    @Synchronized fun importReceipts(raw: String) {
        require(raw.length <= 16 * 1024 * 1024)
        val key = identity.get(nodeId())
        val incoming = PeerCrypto.json.decodeFromString(serializer, raw)
        require(incoming.size <= 10000 && ContributionLedger.verify(incoming, key.nodeId, key.publicKey)) { "Backup does not match this identity or receipt signatures" }
        val existing = read()
        require(ContributionLedger.verify(existing, key.nodeId, key.publicKey)) { "Current ledger needs repair" }
        require(existing.take(minOf(existing.size, incoming.size)) == incoming.take(minOf(existing.size, incoming.size))) { "Receipt history conflicts with this device" }
        if (incoming.size > existing.size) {
            val stream = file.startWrite()
            try { stream.write(raw.toByteArray()); file.finishWrite(stream) }
            catch (e: Exception) { file.failWrite(stream); throw e }
        }
        refresh()
    }
    private fun read(): List<ContributionReceipt> {
        if (!file.baseFile.exists()) return emptyList()
        require(file.baseFile.length() <= 16 * 1024 * 1024) { "Contribution ledger too large" }
        return file.openRead().use { stream ->
            val bytes = com.meshlit.util.readBounded(stream, 16777216)
            require(bytes.size <= 16 * 1024 * 1024) { "Contribution ledger too large" }
            PeerCrypto.json.decodeFromString(serializer, bytes.toString(Charsets.UTF_8))
        }
    }
}
