package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.meshlit.workspace.*
import kotlinx.coroutines.*

@Composable internal fun UsageHistoryDashboard(vault: DesktopUsage, route: String, model: String, gateway: Boolean,
    price: UsagePrice, onPrice: (UsagePrice) -> Unit, onLimits: () -> Unit) {
    val scope = rememberCoroutineScope()
    var ledger by remember { mutableStateOf<UsageLedger?>(null) }
    var state by remember { mutableStateOf<UsageSnapshot?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var password by remember { mutableStateOf("") }
    var unlocked by remember { mutableStateOf(vault.isUnlocked()) }
    var inputRate by remember(route) { mutableStateOf(price.inputPerMillion?.let(UsageMoney::format).orEmpty()) }
    var outputRate by remember(route) { mutableStateOf(price.outputPerMillion?.let(UsageMoney::format).orEmpty()) }
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
    LaunchedEffect(unlocked) {
        if (unlocked) {
            ledger = vault.journal(); state = withContext(Dispatchers.IO) { ledger!!.snapshot() }; freeOnly = state!!.freeOnly
        }
    }
    LaunchedEffect(currency, state?.budgets) {
        state?.budgets?.firstOrNull { it.currency == currency }.let { monthly = it?.monthly?.let(UsageMoney::format).orEmpty(); perRequest = it?.perRequest?.let(UsageMoney::format).orEmpty() }
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Usage & budgets", style = MaterialTheme.typography.h5)
        Text("Encrypted history of Studio chat requests. Local inference has zero API cost; remote pricing is reviewed for the exact selected endpoint and model.")
        if (!unlocked) {
            Text("Create or unlock the usage vault before chatting. Use the same passphrase after restart; it is never saved. Requests remain blocked while locked.")
            OutlinedTextField(password, { password = it.take(256) }, label = { Text("Usage passphrase (12–256 characters)") },
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(), singleLine = true)
            Button(enabled = !busy, onClick = { scope.launch {
                busy = true
                val secret = password.toCharArray(); password = ""
                try { withContext(Dispatchers.IO) { vault.unlock(secret) }; unlocked = true; error = null }
                catch(e: Exception) { if(e is CancellationException) throw e; error = "Cannot unlock usage vault: check passphrase, storage and other Studio processes" }
                finally { secret.fill('\u0000'); busy = false }
            } }) { Text("Unlock usage history") }
        }
        Text(UsagePresentation.scope, style = MaterialTheme.typography.caption)
        error?.let { Text(it, color = MaterialTheme.colors.error) }
        state?.let { current ->
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if(current.freeOnly) "Saved policy: reviewed free models only" else "Saved policy: free and paid models allowed")
                Text(UsagePresentation.totals(current, currency, month), style = MaterialTheme.typography.subtitle1)
                OutlinedTextField(currency, { currency = it.uppercase().take(3) }, label = { Text("Currency (separate budget)") }, singleLine = true)
                OutlinedTextField(month, { month = it.take(7); page = 0 }, label = { Text("History month YYYY-MM (UTC)") }, singleLine = true)
                Row { Switch(freeOnly, { freeOnly = it }); Text("Only reviewed free direct models") }
                Text("Free-only blocks unknown prices, stale rates and gateways. Zero rates are your reviewed declaration, not account verification.", style = MaterialTheme.typography.caption)
                OutlinedTextField(monthly, { monthly = it.take(24) }, label = { Text("Monthly allowance (blank disables)") }, singleLine = true)
                OutlinedTextField(perRequest, { perRequest = it.take(24) }, label = { Text("Reserve per request") }, singleLine = true)
                Text("Paid requests reserve this full allowance before sending. Missing usage and interruptions retain reservations until you reconcile. Budgets apply to future text requests and cannot cancel charges already incurred.", style = MaterialTheme.typography.caption)
                Button(enabled = !busy, onClick = { action { it.setPolicy(freeOnly,
                    if(monthly.isBlank()) null else UsageBudget(currency, UsageMoney.parse(monthly), UsageMoney.parse(perRequest)), currency) } }) { Text("Save budget policy") }
            } }
            if (model.isNotBlank()) {
                Text("Selected remote model: $model · ${route.take(10)}", style = MaterialTheme.typography.subtitle1)
                Text(if (gateway) "Gateway pricing is opaque. Free-only and local currency budgets block this route. Review downstream billing in your gateway." else "Review direct-model rates in the selected currency. These session rates expire after 24 hours; changing the endpoint or model requires a new review.", style = MaterialTheme.typography.caption)
                OutlinedButton(onClick = { try {
                    val selected = UsagePrice(currency = currency, source = "User selected currency", gateway = gateway)
                    selected.validate(); onPrice(selected); error = null
                } catch(e: Exception) { error = "Enter a three-letter billing currency" } }) { Text("Use currency; prices unknown") }
                if (!gateway) {
                    OutlinedTextField(inputRate, { inputRate = it.take(24) }, label = { Text("Input price per million tokens") }, singleLine = true)
                    OutlinedTextField(outputRate, { outputRate = it.take(24) }, label = { Text("Output price per million tokens") }, singleLine = true)
                    Button(onClick = { try {
                        val reviewed = UsagePrice(currency, UsageMoney.parse(inputRate), UsageMoney.parse(outputRate), "User reviewed rates", System.currentTimeMillis())
                        reviewed.validate(); onPrice(reviewed); error = null
                    } catch(e: Exception) { error = "Enter valid non-negative rates and a three-letter currency" } }) { Text("Use reviewed rates for this model") }
                    Text("Active rates: ${price.inputPerMillion?.let(UsageMoney::format) ?: "unknown"} / ${price.outputPerMillion?.let(UsageMoney::format) ?: "unknown"} ${price.currency}", style = MaterialTheme.typography.caption)
                }
            }
            TextButton(onClick = onLimits) { Text("Output token limit") }
            OutlinedButton(enabled = !busy, onClick = { action { } }) { Text("Refresh history") }
            val all = current.attempts.filter { it.price.currency == currency && UsageLedger.month(it.startedAt) == month }.asReversed()
            Text("${all.size} requests · latest first", style = MaterialTheme.typography.subtitle1)
            if(all.isEmpty()) Text("No recorded requests in this currency and month.")
            all.groupBy { it.route }.entries.take(50).forEach { (_, requests) ->
                Text(UsagePresentation.routeSummary(requests), style = MaterialTheme.typography.caption)
            }
            all.drop(page * 20).take(20).forEach { a -> key(a.id) {
                var amount by remember { mutableStateOf("") }
                var editing by remember { mutableStateOf(false) }
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(a.model, style = MaterialTheme.typography.subtitle1)
                    Text("${java.time.Instant.ofEpochMilli(a.startedAt)} · ${a.route.take(10)}", style = MaterialTheme.typography.caption)
                    Text(UsagePresentation.detail(a))
                    if(a.status != "pending") TextButton(onClick = { editing = !editing }) { Text("Reconcile with provider bill") }
                    if(editing) {
                        OutlinedTextField(amount, { amount = it.take(24) }, label = { Text("Actual amount in ${a.price.currency}") }, singleLine = true)
                        Text("Enter the charge for this attempt only, including zero if confirmed. This is a manual record; no bill is fetched.", style = MaterialTheme.typography.caption)
                        Button(enabled = !busy, onClick = { action { it.reconcile(a.id, UsageMoney.parse(amount)) } }) { Text("Record confirmed amount") }
                    }
                } }
            } }
            Row { TextButton(enabled = page > 0, onClick = { page-- }) { Text("Previous") }; TextButton(enabled = (page+1)*20 < all.size, onClick = { page++ }) { Text("Next") } }
        }
    }
}
