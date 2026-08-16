package com.meshlit.core.advanced

import com.meshlit.core.advanced.engines.BaseEngine
import com.meshlit.core.advanced.engines.EngineCategory
import com.meshlit.core.advanced.engines.KokoroEngine
import com.meshlit.core.advanced.engines.KokoroRequest
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BaseEngineTest {

    @Test
    fun load_sets_modelPath_and_flips_isLoaded() = runBlocking {
        val engine = KokoroEngine()
        assertFalse(engine.isLoaded)
        assertNull(engine.modelPath)
        val result = engine.load(File("/tmp/fake-model.bin"))
        assertTrue(result is MeshlitResult.Success)
        assertTrue(engine.isLoaded)
        assertEquals(File("/tmp/fake-model.bin"), engine.modelPath)
    }

    @Test
    fun load_is_idempotent() = runBlocking {
        val engine = KokoroEngine()
        engine.load(File("/tmp/first.bin"))
        engine.load(File("/tmp/second.bin"))
        assertEquals(File("/tmp/second.bin"), engine.modelPath)
    }

    @Test
    fun run_before_load_returns_invalid_error() = runBlocking {
        val engine = KokoroEngine()
        val result = engine.run(KokoroRequest("hello"))
        assertTrue(result is MeshlitResult.Failure)
        assertEquals(
            "engine_not_loaded:kokoro_en",
            (result as MeshlitResult.Failure).error.tag,
        )
    }

    @Test
    fun run_after_load_returns_success_placeholder() = runBlocking {
        val engine = KokoroEngine()
        engine.load(File("/tmp/x.bin"))
        val result = engine.run(KokoroRequest("hello world"))
        assertTrue(result is MeshlitResult.Success)
        val value = (result as MeshlitResult.Success).value
        assertTrue(value.audioPath.contains("11 chars"))
    }

    @Test
    fun unload_clears_modelPath() = runBlocking {
        val engine = KokoroEngine()
        engine.load(File("/tmp/x.bin"))
        val result = engine.unload()
        assertTrue(result is MeshlitResult.Success)
        assertNull(engine.modelPath)
        assertFalse(engine.isLoaded)
    }

    @Test
    fun engine_metadata_is_correct() {
        val engine: BaseEngine<*, *> = KokoroEngine()
        assertEquals("kokoro_en", engine.id)
        assertEquals(EngineCategory.TTS, engine.category)
    }
}
