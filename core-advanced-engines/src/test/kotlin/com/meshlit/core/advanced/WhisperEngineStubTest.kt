package com.meshlit.core.advanced

import com.meshlit.core.advanced.engines.WhisperEngine
import com.meshlit.core.advanced.engines.WhisperRequest
import com.meshlit.core.common.MeshlitResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WhisperEngineStubTest {

    @Test
    fun transcribes_audio_path() = runBlocking {
        val engine = WhisperEngine()
        engine.load(File("/tmp/audio.wav"))
        val result = engine.run(WhisperRequest(audioPath = "/tmp/audio.wav", language = "en"))
        assertTrue(result is MeshlitResult.Success)
        val resp = (result as MeshlitResult.Success).value
        assertTrue(resp.text.contains("/tmp/audio.wav"))
        assertEquals("en", resp.language)
    }

    @Test
    fun default_language_is_en() = runBlocking {
        val engine = WhisperEngine()
        engine.load(File("/tmp/x"))
        val resp = (engine.run(WhisperRequest(audioPath = "/tmp/x")) as MeshlitResult.Success).value
        assertEquals("en", resp.language)
    }
}
