package com.meshlit.qualification

import androidx.test.platform.app.InstrumentationRegistry
import ai.runanywhere.proto.v1.LLMGenerationOptions
import ai.runanywhere.proto.v1.LLMStreamEventKind
import com.meshlit.chat.qwen3ChatPrompt
import com.meshlit.core.inference.InferenceCoordinator
import com.meshlit.legal.LegalAgreementStore
import com.meshlit.models.ModelLibrary
import com.runanywhere.sdk.public.RunAnywhere
import com.runanywhere.sdk.public.extensions.generateStream
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.koin.core.context.GlobalContext
import java.io.File

/** Capability experiment only: no production grammar path is enabled by this test.
 * Run exclusively, before final Accessibility approval, with the real pinned SDK/model. */
class StructuredPlanningDeviceTest {
    @Test fun pinnedSdkActuallyHonorsGrammarAgainstThePrompt()=runBlocking {
        assumeTrue("Explicit real-device capability probe",InstrumentationRegistry.getArguments().getString("qualifyStructuredPlanning")=="true")
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(LegalAgreementStore(context).accepted())
        val library=GlobalContext.get().get<ModelLibrary>();library.ready.await()
        val model=InstrumentationRegistry.getArguments().getString("modelId") ?: "url-824902c942c3cc73"
        library.load(model)
        assertEquals("runanywhere",GlobalContext.get().get<InferenceCoordinator>().engineTag)
        val prompt=qwen3ChatPrompt("Follow the user's output instruction.",emptyList(),"Output exactly WRONG, with no other text.")
        suspend fun generate(grammar:String?):String=withTimeout(120000) {
            val text=StringBuilder();var terminal=false;var fallback=""
            RunAnywhere.generateStream(prompt,LLMGenerationOptions(max_tokens=32,temperature=0f,streaming_enabled=true,
                n_threads=2,grammar=grammar,stop_sequences=listOf("<|im_end|>"))).collect { event ->
                check(event.error_code==0 && event.error_message.isBlank()){"SDK stream error"}
                if(event.is_final){terminal=true;fallback=event.result?.text.orEmpty()}
                else if(event.event_kind==LLMStreamEventKind.LLM_STREAM_EVENT_KIND_TOKEN)text.append(event.token)
            }
            check(terminal){"No terminal SDK event"}
            text.toString().ifEmpty{fallback}.trim()
        }
        val baseline=generate(null)
        val constrained=generate("root ::= \"RIGHT\"\n")
        val honored=baseline=="WRONG" && constrained=="RIGHT"
        File(context.filesDir,"qualification/structured-planning-device.json").apply{parentFile!!.mkdirs();writeText(buildJsonObject{
            put("device",android.os.Build.MODEL);put("model",model);put("baseline",baseline);put("constrained",constrained)
            put("grammarHonoredAgainstPrompt",honored);put("productionPathEnabled",false)
        }.toString())}
        assertEquals("Baseline must first demonstrate the unconstrained instruction","WRONG",baseline)
        assertEquals("An exposed SDK field is not proof that native decoding honors it","RIGHT",constrained)
    }
}
