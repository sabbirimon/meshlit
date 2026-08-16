package com.meshlit.core.advanced

import com.meshlit.core.advanced.engines.CosmosEngine
import com.meshlit.core.advanced.engines.EmbeddingGemmaEngine
import com.meshlit.core.advanced.engines.EngineCategory
import com.meshlit.core.advanced.engines.EngineRegistry
import com.meshlit.core.advanced.engines.KokoroEngine
import com.meshlit.core.advanced.engines.NemotronOcrEngine
import com.meshlit.core.advanced.engines.SegFormerEngine
import com.meshlit.core.advanced.engines.SileroVadEngine
import com.meshlit.core.advanced.engines.SortformerEngine
import com.meshlit.core.advanced.engines.WhisperEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class EngineRegistryTest {

    @Test
    fun register_indexes_by_category() {
        val reg = EngineRegistry()
        reg.register(WhisperEngine())
        reg.register(KokoroEngine())
        reg.register(SileroVadEngine())

        assertEquals(1, reg.forCategory(EngineCategory.STT).size)
        assertEquals(1, reg.forCategory(EngineCategory.TTS).size)
        assertEquals(1, reg.forCategory(EngineCategory.VAD).size)
        assertEquals(0, reg.forCategory(EngineCategory.LLM).size)
    }

    @Test
    fun firstFor_returns_first_engine_in_category() {
        val reg = EngineRegistry()
        val a = WhisperEngine()
        reg.register(a)
        assertSame(a, reg.firstFor(EngineCategory.STT))
    }

    @Test
    fun register_with_same_id_replaces_previous() {
        val reg = EngineRegistry()
        val a = WhisperEngine()
        val b = WhisperEngine() // same id
        reg.register(a)
        reg.register(b)
        assertEquals(1, reg.size())
        assertSame(b, reg.firstFor(EngineCategory.STT))
    }

    @Test
    fun categories_returns_distinct_set() {
        val reg = EngineRegistry()
        reg.register(WhisperEngine())
        reg.register(KokoroEngine())
        reg.register(SortformerEngine())
        val cats = reg.categories()
        assertEquals(3, cats.size)
        assertTrue(EngineCategory.STT in cats)
        assertTrue(EngineCategory.TTS in cats)
        assertTrue(EngineCategory.DIARIZATION in cats)
    }

    @Test
    fun all_eight_engines_register_cleanly() {
        val reg = EngineRegistry()
        reg.register(WhisperEngine())
        reg.register(KokoroEngine())
        reg.register(SileroVadEngine())
        reg.register(SortformerEngine())
        reg.register(EmbeddingGemmaEngine())
        reg.register(NemotronOcrEngine())
        reg.register(SegFormerEngine())
        reg.register(CosmosEngine())
        assertEquals(8, reg.size())
        assertEquals(8, reg.categories().size)
    }

    @Test
    fun unloadAll_clears_registry() = runBlocking {
        val reg = EngineRegistry()
        reg.register(WhisperEngine())
        reg.register(WhisperEngine().also { it.load(File("/tmp/x")) })
        val result = reg.unloadAll()
        assertTrue(result is com.meshlit.core.common.MeshlitResult.Success)
        assertEquals(0, reg.size())
        assertNotNull(reg)
    }
}
