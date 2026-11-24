package com.meshlit.ui.modern

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.meshlit.providers.UsageHistory
import com.meshlit.workspace.*
import kotlinx.coroutines.*

@Composable internal fun UsageDashboard() {
    val context = LocalContext.current.applicationContext
    val scope = rememberCoroutineScope()
    var ledger by remember { mutableStateOf<UsageLedger?>(null) }
    var state by remember { mutableStateOf<UsageSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var currency by remember { mutableStateOf("USD") }
    var month by remember { mutableStateOf(UsageLedger.month(System.currentTimeMillis())) }
    var freeOnly by remember { mutableStateOf(false) }
    var monthly by remember { mutableStateOf("") }
    var perRequest by remember { mutableStateOf("") }
    var page by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    fun action(block: (UsageLedger) -> Unit) { scope.launch {
        busy = true
        try { withContext(Dispatchers.IO) { block(checkNotNull(ledger)) }; state = withContext(Dispatchers.IO) { ledger!!.snapshot() }; error = null }
        catch (e: CancellationException) { throw e } catch (e: Exception) { error = e.message ?: "Usage storage unavailable" }
        finally { busy = false }
    } }
    LaunchedEffect(Unit) {
        try { ledger = withContext(Dispatchers.IO) { UsageHistory.get(context) }; state = withContext(Dispatchers.IO) { ledger!!.snapshot() }; freeOnly = state!!.freeOnly }
        catch (e: Exception) { if(e is CancellationException) throw e; error = "Usage history unavailable; requests fail closed" }
    }
    LaunchedEffect(currency, state?.budgets) {
        state?.budgets?.firstOrNull { it.currency == currency }.let { monthly = it?.monthly?.let(UsageMoney::format).orEmpty(); perRequest = it?.perRequest?.let(UsageMoney::format).orEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Usage & budgets", style = MaterialTheme.typography.headlineSmall)
        Text("Encrypted history of online text requests, including model routes and delegated calls. On-device Android generation and media are not included.")
        Text(UsagePresentation.scope, style = MaterialTheme.typography.bodySmall)
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        state?.let { current ->
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(current.freeOnly) "Saved policy: reviewed free models only" else "Saved policy: free and paid models allowed")
                Text(UsagePresentation.totals(current, currency, month), style = MaterialTheme.typography.titleMedium)
                OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency (separate budget)") }, singleLine = true)
                OutlinedTextField(month, { month = it.take(7); page = 0 }, label = { Text("History month YYYY-MM (UTC)") }, singleLine = true)
                Row { Switch(freeOnly, { freeOnly = it }); Text("Only reviewed free direct models") }
                Text("Free-only blocks unknown prices, stale rates and gateways. Zero rates are your reviewed declaration, not account verification.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(monthly, { monthly = it.take(24) }, label = { Text("Monthly allowance (blank disables)") }, singleLine = true)
                OutlinedTextField(perRequest, { perRequest = it.take(24) }, label = { Text("Reserve per request") }, singleLine = true)
                Text("Paid requests reserve this full allowance before sending. Missing usage and interruptions retain reservations until you reconcile. Budgets apply to future text requests and cannot cancel charges already incurred.", style = MaterialTheme.typography.bodySmall)
                Button(enabled = !busy, onClick = { action { it.setPolicy(freeOnly,
                    if(monthly.isBlank()) null else UsageBudget(currency, UsageMoney.parse(monthly), UsageMoney.parse(perRequest)), currency) } }) { Text("Save budget policy") }
            } }
            OutlinedButton(enabled = !busy, onClick = { action { } }) { Text("Refresh history") }
            val all = current.attempts.filter { it.price.currency == currency && UsageLedger.month(it.startedAt) == month }.asReversed()
            Text("${all.size} requests · latest first", style = MaterialTheme.typography.titleMedium)
            if(all.isEmpty()) Text("No recorded requests in this currency and month.")
            all.groupBy { it.route }.entries.take(50).forEach { (_, requests) ->
                Text(UsagePresentation.routeSummary(requests), style = MaterialTheme.typography.bodySmall)
            }
            all.drop(page * 20).take(20).forEach { a -> key(a.id) {
                var amount by remember { mutableStateOf("") }
                var editing by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.model, style = MaterialTheme.typography.titleMedium)
                    Text("${java.time.Instant.ofEpochMilli(a.startedAt)} · ${a.route.take(10)}", style = MaterialTheme.typography.labelSmall)
                    Text(UsagePresentation.detail(a))
                    if(a.status != "pending") TextButton(onClick = { editing = !editing }) { Text("Reconcile with provider bill") }
                    if(editing) {
                        OutlinedTextField(amount, { amount = it.take(24) }, label = { Text("Actual amount in ${a.price.currency}") }, singleLine = true)
                        Text("Enter the charge for this attempt only, including zero if confirmed. This is a manual record; no bill is fetched.", style = MaterialTheme.typography.bodySmall)
                        Button(enabled = !busy, onClick = { action { it.reconcile(a.id, UsageMoney.parse(amount)) } }) { Text("Record confirmed amount") }
                    }
                } }
            } }
            Row { TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous") }; TextButton(enabled = (page+1)*20 < all.size, onClick = { page++ }) { Text("Next") } }
        }
    }
}
