package com.meshlit.observability

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.observability.*
import com.meshlit.core.trust.EncryptedCredentialStore
import com.meshlit.core.mcp.control.*
import com.meshlit.control.AgentBackend
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import com.meshlit.MainActivity
import com.meshlit.legal.LegalAgreementStore
import org.junit.runner.RunWith
import org.koin.core.context.GlobalContext
import java.util.UUID

/** Real Android samples, real typed task mutation, encrypted journal reopening; no remote account required. */
@RunWith(AndroidJUnit4::class)
class AuditAndroidTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    @Test fun realDeviceAndHumanTaskAuditPersistsAndExportsWithoutTaskContent()=runBlocking {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        if (!LegalAgreementStore(context).accepted()) {
            compose.onNodeWithTag("legal-continue").performScrollTo().assertIsNotEnabled()
            compose.onNodeWithTag("accept-terms").performClick()
            compose.onNodeWithTag("legal-continue").assertIsNotEnabled()
            compose.onNodeWithTag("accept-privacy").performClick()
            compose.onNodeWithTag("legal-continue").performClick()
            compose.waitUntil(5_000) { LegalAgreementStore(context).accepted() }
        }
        val audit=GlobalContext.get().get<AuditTelemetry>();val backend=GlobalContext.get().get<AgentBackend>()
        val settings=GlobalContext.get().get<SettingsRepository>();val original=audit.policy.value;val mode=settings.tracingModeFlow.first()
        val createId=UUID.randomUUID().toString();val privateTitle="Private audit test ${UUID.randomUUID()}";var task:JsonObject?=null
        try {
            withTimeout(60_000){audit.ready.await()};settings.setTracingMode(com.meshlit.settings.TracingMode.Local)
            audit.configure(original.copy(enabled=true,intervalSeconds=15))
            task=backend.executeHuman(AgentCommand(createId,AgentOperation.TASK_CREATE,task=TaskMutation(title=privateTitle))).jsonObject
            val records=withTimeout(30_000){audit.journal.records.first{list->list.any{it.source==AuditSource.DEVICE && it.measurements.containsKey("ram_available_bytes")} && list.any{it.actor==AuditActor.HUMAN && it.targetHash==AuditRecord.hashTarget(createId) && it.outcome==AuditOutcome.SUCCEEDED}}}
            val raw=withContext(Dispatchers.IO){EncryptedCredentialStore(context,"audit-telemetry-journal").get("records")!!}
            assertFalse(raw.contains(privateTitle));assertFalse(raw.contains(createId))
            val reopened=AuditJournal(object:AuditStorage{override suspend fun read()=raw;override suspend fun write(jsonl:String)=Unit})
            reopened.open();assertTrue(reopened.records.value.any{it.targetHash==AuditRecord.hashTarget(createId)})
            val exported=records.joinToString("\n"){AuditExport.csv(it)}
            assertFalse(exported.contains(privateTitle));assertTrue(exported.contains("ram_available_bytes"))
            println("MESHLIT_AUDIT realDeviceSample=true humanTypedTask=true encryptedReopen=true contentExcluded=true csvAndJsonlMetadata=true hostedGrafanaProof=false")
        } finally {
            withContext(NonCancellable){task?.let{backend.executeHuman(AgentCommand(UUID.randomUUID().toString(),AgentOperation.TASK_DELETE,task=TaskMutation(id=it.getValue("id").jsonPrimitive.content,expectedRevision=Json.decodeFromJsonElement<ManagedTask>(it).revision)))}
                audit.configure(original);settings.setTracingMode(mode)}
        }
    }
}
