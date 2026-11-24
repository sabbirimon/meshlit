package com.meshlit.ui.modern

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

private data class ShieldReport(val bytes: ByteArray, val rows: List<Pair<String,String>>)

private fun readShieldReport(input: InputStream): ShieldReport {
    val bytes = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = input.read(buffer)
        if (size < 0) break
        require(bytes.size() + size <= 1024 * 1024) { "Choose a report of at most 1 MiB." }
        bytes.write(buffer, 0, size)
    }
    val doc = JSONObject(bytes.toString("UTF-8"))
    require(doc.get("schema") == 1 && doc.get("host") is String && doc.get("is_target") is Boolean)
    val verdict = doc.getString("status")
    require(verdict in setOf("ok","tampered","lsm-off","init-pending","reboot-pending","degraded"))
    val known = setOf("LsmAttached","KeyringSealed","ChainIntact","UpatchesMeasured","TpmMeasured","ConfigActive","XdpCovered")
    val conditions = doc.getJSONArray("conditions")
    require(conditions.length() <= known.size)
    val seen = mutableSetOf<String>()
    val alertCounts = mutableMapOf<String,Int>()
    val details = buildList {
        add("Reported status" to verdict)
        add("Host" to doc.getString("host").filter { !it.isISOControl() && Character.getType(it) != Character.FORMAT.toInt() }.take(128))
        add("Target" to if (doc.getBoolean("is_target")) "Yes" else "No")
        add("Loaded shields" to doc.getJSONArray("upatches_loaded").length().toString())
        repeat(conditions.length()) { index ->
            val condition = conditions.getJSONObject(index)
            val name = condition.getString("type")
            val state = condition.getString("status")
            require(name in known && seen.add(name) && state in setOf("OK","WARN","ALERT"))
            alertCounts[state] = (alertCounts[state] ?: 0) + 1
            add(name to state)
        }
        add("TPM attestation" to "Unverified")
    }
    val scan = listOf(
        "Scan summary" to "${alertCounts["ALERT"] ?: 0} alerts · ${alertCounts["WARN"] ?: 0} warnings · ${alertCounts["OK"] ?: 0} passed",
        "Missing checks" to ((known - seen).joinToString().ifEmpty { "None" })
    )
    return ShieldReport(bytes.toByteArray(), details.take(2) + scan + details.drop(2))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun ShirudoScreen(back: () -> Unit) {
    val resolver = LocalContext.current.contentResolver
    val scope = rememberCoroutineScope()
    var report by remember { mutableStateOf<ShieldReport?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            loading = true
            report = null
            error = null
            try {
                report = withContext(Dispatchers.IO) { requireNotNull(resolver.openInputStream(uri)).use(::readShieldReport) }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Choose a readable schema 1 Shirudo status JSON file of at most 1 MiB."
            } finally {
                loading = false
            }
        }
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val current = report
        if (uri != null && current != null) scope.launch {
            error = null
            loading = true
            try {
                withContext(Dispatchers.IO) {
                    requireNotNull(resolver.openOutputStream(uri,"wt")).use { it.write(current.bytes) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                error = "Could not write the report to that destination."
            } finally { loading = false }
        }
    }
    Scaffold(topBar = { CenterAlignedTopAppBar(title = { Text("Shirudo shields") }, navigationIcon = {
        IconButton(onClick = back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
    }) }) { insets ->
        Column(Modifier.padding(insets).verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Linux node report", style = MaterialTheme.typography.titleLarge)
            Text("On your Shirudo target, export shirudo status --json. Open that file here to inspect its shields and security checks.")
            Button(onClick = { picker.launch(arrayOf("application/json","text/plain","application/octet-stream")) }, enabled = !loading) {
                Text("Import and scan report")
            }
            if (report != null) OutlinedButton(onClick = { exporter.launch("shirudo-status.json") }, enabled = !loading) {
                Text("Export original JSON")
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            report?.rows.orEmpty().forEach { (label,value) ->
                ListItem(headlineContent = { Text(label) }, supportingContent = { Text(value) })
            }
        }
    }
}
