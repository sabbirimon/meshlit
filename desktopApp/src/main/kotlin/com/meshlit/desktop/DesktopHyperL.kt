package com.meshlit.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.meshlit.core.hyperl.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest

private fun verifyHyperLNative() {
    val resources = Path.of(System.getProperty("compose.application.resources.dir", "desktopApp/build/local-resources"))
    val library = resources.resolve("local/${System.mapLibraryName("meshlit_hyperl")}")
    check(Files.isRegularFile(library, java.nio.file.LinkOption.NOFOLLOW_LINKS) && Files.size(library) <= 16 * 1024 * 1024) { "Bundled HyperL library unavailable or oversized" }
    val manifestBytes = Files.newInputStream(resources.resolve("local/hyperl-engine.json"), java.nio.file.LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(262145) }
    require(manifestBytes.size <= 262144) { "HyperL manifest exceeds its limit" }
    val manifest = boundedJson(manifestBytes.decodeToString(throwOnInvalidSequence = true))
    val bytes = Files.newInputStream(library, java.nio.file.LinkOption.NOFOLLOW_LINKS).use { it.readNBytes(16 * 1024 * 1024 + 1) }.also { require(it.size <= 16 * 1024 * 1024) }
    val actual = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    check(actual == manifest["sha256"]!!.jsonPrimitive.content) { "HyperL native checksum mismatch" }
}

@Composable internal fun HyperLPanel() {
    var accepted by remember { mutableStateOf(false) }
    var recipeId by remember { mutableStateOf("affine_relu") }
    var custom by remember { mutableStateOf(false) }
    var measure by remember { mutableStateOf(false) }
    var programText by remember { mutableStateOf(HyperLCodec.json.encodeToString(HyperLProgram.serializer(), HyperLLibrary.recipe("affine_relu").program)) }
    var input by remember { mutableStateOf("{\"x\":[-1,2,3],\"w\":[2,3,4],\"bias\":[1,1,1]}") }
    var result by remember { mutableStateOf("") }; var failure by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }; var job by remember { mutableStateOf<Job?>(null) }
    var showTerms by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    Text("Built-in HyperL · native CPU", style = MaterialTheme.typography.h6)
    Text("Twelve bounded f32 recipes or your own declarative graph, executed by the verified native CPU library. This is separate from the LLM engine; GPU/NPU drivers need separate qualification.")
    TextButton({ showTerms = !showTerms }) { Text("Read HyperL licence") }
    if (showTerms) Text(HyperLLibrary::class.java.getResourceAsStream("/hyperl/LICENSE")?.bufferedReader()?.use { it.readText() } ?: "Licence resource unavailable")
    Row { Checkbox(accepted, { accepted = it; if (!it) { job?.cancel(); result = "" } }); Text("Enable after reviewing HyperL's separate terms") }
    Row { Checkbox(custom, { custom = it; result = "" }, enabled = !running); Text("Custom HyperL graph JSON") }
    HyperLLibrary.ids.chunked(3).forEach { group -> Row { group.forEach { id -> TextButton({
        job?.cancel(); running = false; recipeId = id; custom = false; result = ""
        input = buildJsonObject { HyperLLibrary.recipe(id).example.forEach { (name, values) -> putJsonArray(name) { values.forEach { add(it) } } } }.toString()
        programText = HyperLCodec.json.encodeToString(HyperLProgram.serializer(), HyperLLibrary.recipe(id).program)
    }, enabled = !running) { Text((if (recipeId == id) "✓ " else "") + id) } } } }
    if (custom) OutlinedTextField(programText, { programText = it.take(HyperLCodec.MAX_PROGRAM_CHARS); result = "" },
        label = { Text("HyperL program · JSON") }, modifier = Modifier.fillMaxWidth(), maxLines = 10, enabled = !running)
    else Text(HyperLLibrary.recipe(recipeId).purpose)
    Text("Recipe examples are editable numerical inputs; Run computes the real result.", style = MaterialTheme.typography.caption)
    Row { Checkbox(measure, { measure = it }, enabled = !running); Text("Measure CPU · one warm-up and five repeated runs") }
    OutlinedTextField(input, { input = it.take(65536); result = "" }, label = { Text("Input vectors · JSON") }, modifier = Modifier.fillMaxWidth(), maxLines = 8, enabled = !running)
    Row {
        Button({
            running = true; failure = null; result = ""
            val selected = recipeId; val text = input; val graph = programText; val useCustom = custom; val benchmark = measure
            job = scope.launch {
                try {
                    result = withTimeout(10_000) { withContext(Dispatchers.Default) {
                        verifyHyperLNative()
                        val values = HyperLCodec.inputs(text)
                        val program = if (useCustom) HyperLCodec.program(graph) else HyperLLibrary.recipe(selected).program
                        if (benchmark) {
                            val warmup = NativeCpu.execute(program, values, 64L * 1024 * 1024)
                            val digest = MessageDigest.getInstance("SHA-256")
                            val bytes = java.nio.ByteBuffer.allocate(warmup.size * 4).order(java.nio.ByteOrder.LITTLE_ENDIAN)
                            warmup.forEach { bytes.putFloat(it) }
                            val hash = digest.digest(bytes.array()).joinToString("") { "%02x".format(it) }
                            val samples = List(5) {
                                currentCoroutineContext().ensureActive()
                                val started = System.nanoTime()
                                val output = NativeCpu.execute(program, values, 64L * 1024 * 1024)
                                val elapsed = (System.nanoTime() - started) / 1_000_000.0
                                check(output.contentEquals(warmup)) { "Output changed across repeated runs" }
                                elapsed
                            }
                            "${HyperLContract.RUNTIME_REVISION} · native CPU · ${warmup.size} outputs\nMedian ${samples.sorted()[2]} ms · p95 ${samples.max()} ms\nSamples: ${samples.joinToString()} ms\nOutput SHA-256 $hash\nOne warm-up; five identical outputs. Includes native admission/JNI copies, excludes parsing and output comparison. Small-run p95 is the slowest sample."
                        } else {
                            val started = System.nanoTime()
                            val output = NativeCpu.execute(program, values, 64L * 1024 * 1024)
                            "${output.take(256).joinToString(prefix = "[", postfix = "]")} · ${output.size} outputs · native execution ${(System.nanoTime() - started) / 1_000_000.0} ms" + if (output.size > 256) " · preview limited to 256 values" else ""
                        }
                    } }
                } catch (e: TimeoutCancellationException) { failure = "CPU operation exceeded the ten-second deadline" }
                catch (e: CancellationException) { throw e }
                catch (e: Throwable) { failure = e.message?.take(240) ?: e.javaClass.simpleName }
                finally { running = false }
            }
        }, enabled = accepted && !running) { Text(if (custom) "Run custom graph" else "Run native CPU recipe") }
        TextButton({ job?.cancel(); result = "" }, enabled = running) { Text("Stop") }
    }
    failure?.let { Text("Execution failed: $it", color = MaterialTheme.colors.error) }
    if (result.isNotEmpty()) Text("Native output: $result")
}

internal fun hyperLCheck(output: String) = runBlocking {
    verifyHyperLNative()
    var count = 0
    for (id in HyperLLibrary.ids) {
        val recipe = HyperLLibrary.recipe(id)
        val native = NativeCpu.execute(recipe.program, recipe.example, 64L * 1024 * 1024)
        val reference = HyperLCpuBackend(64L * 1024 * 1024).execute(recipe.program, recipe.example)
        check(native.contentEquals(reference)) { "Native mismatch for $id" }; count++
    }
    check(NativeCpu.precise(floatArrayOf(100000000f, 1f, -100000000f), 64L * 1024 * 1024) == 1f)
    Files.writeString(Path.of(output), buildJsonObject {
        put("nativeCpuRecipesVerified", count); put("preciseReductionResult", 1)
        put("runtime", HyperLContract.RUNTIME_REVISION); put("scope", "Actual Intel JNI; no distributed LLM or GPU claim")
    }.toString())
    println("Built-in HyperL native CPU recipes and precise reduction passed.")
}
