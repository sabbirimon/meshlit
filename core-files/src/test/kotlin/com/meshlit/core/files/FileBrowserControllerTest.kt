package com.meshlit.core.files

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileBrowserControllerTest {

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun navigate_to_root_then_subdir_then_up_returns_to_root() = runBlocking {
        val root = tmp.newFolder("app")
        val sub = tmp.newFolder("app", "models")
        sub.resolve("foo.gguf").writeText("x")
        val source = InternalStorageSource(allowedRoots = listOf(root))
        val ctrl = FileBrowserController(source, initialDir = root.absolutePath)
        ctrl.navigateTo(root.absolutePath)
        val rootState = ctrl.state.value
        assertEquals(1, rootState.stack.size)
        assertEquals(1, rootState.entries.size)
        assertEquals("models", rootState.entries[0].name)
        assertTrue(rootState.entries[0].isDirectory)

        ctrl.navigateTo(sub.absolutePath)
        assertEquals(2, ctrl.state.value.stack.size)
        assertEquals(1, ctrl.state.value.entries.size)
        assertEquals("foo.gguf", ctrl.state.value.entries[0].name)
        assertFalse(ctrl.state.value.entries[0].isDirectory)
        assertEquals("application/x-gguf", ctrl.state.value.entries[0].mimeGuess)

        ctrl.navigateUp()
        assertEquals(1, ctrl.state.value.stack.size)
        assertEquals(root.absolutePath, ctrl.state.value.currentDir)
    }

    @Test
    fun navigates_under_root_only() = runBlocking {
        val root = tmp.newFolder("app")
        val outside = tmp.newFolder("outside")
        outside.resolve("evil.txt").writeText("nope")
        val source = InternalStorageSource(allowedRoots = listOf(root))
        val ctrl = FileBrowserController(source, initialDir = root.absolutePath)
        ctrl.navigateTo(outside.absolutePath)
        assertEquals(emptyList<FileBrowserEntry>(), ctrl.state.value.entries)
    }

    @Test
    fun skip_hidden_and_nomedia_sentinel() = runBlocking {
        val root = tmp.newFolder("app")
        root.resolve(".hidden").writeText("nope")
        root.resolve("visible.txt").writeText("ok")
        root.resolve(".nomedia").writeText("")
        val source = InternalStorageSource(allowedRoots = listOf(root))
        val ctrl = FileBrowserController(source, initialDir = root.absolutePath)
        ctrl.navigateTo(root.absolutePath)
        val names = ctrl.state.value.entries.map { it.name }
        assertEquals(listOf("visible.txt"), names)
    }

    @Test
    fun mime_guess_for_known_extensions() {
        assertEquals("application/x-gguf", guessMime("phi-3.gguf"))
        assertEquals("application/x-onnx", guessMime("whisper.onnx"))
        assertEquals("application/x-safetensors", guessMime("qwen.safetensors"))
        assertEquals("application/json", guessMime("config.json"))
        assertEquals("text/plain", guessMime("notes.txt"))
        assertEquals("application/octet-stream", guessMime("bin"))
        assertEquals("application/octet-stream", guessMime("no-extension"))
    }
}
