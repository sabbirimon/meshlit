package com.meshlit.providers

import android.content.Context
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.workspace.UsageLedger

/** One ledger authority for all Android text provider consumers, including route steps and agents. */
object UsageHistory {
    @Volatile private var instance: UsageLedger? = null
    fun get(context: Context): UsageLedger = instance ?: synchronized(this) {
        instance ?: run {
            val store = EncryptedCredentialStore(context.applicationContext, "model-usage-history")
            UsageLedger({ store.get("journal") }, { store.putCommitted("journal", it) })
                .also { it.recoverInterrupted(); instance = it }
        }
    }
}
