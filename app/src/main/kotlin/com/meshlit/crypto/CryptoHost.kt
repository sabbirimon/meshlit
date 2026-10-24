package com.meshlit.crypto
import android.content.Context
import com.meshlit.core.common.control.ManagedFeature
import com.meshlit.core.mcp.*
import com.meshlit.core.mcp.crypto.CryptoEngine
import com.meshlit.operations.OperationsControl
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.json.*
class CryptoHost(context: Context) {
    private val prefs = context.getSharedPreferences("local-cryptography", 0)
    private val gate = OperationsControl.get(context).gate
    private val _agents = MutableStateFlow(prefs.getBoolean("agents", false))
    val agents = _agents.asStateFlow()
    fun saveAgentGrantHuman(on: Boolean) {
        check(!on || !com.meshlit.BuildProfile.coreCandidate)
        if (!on) _agents.value = false
        check(prefs.edit().putBoolean("agents", on).commit()) { "Cannot save cryptography grant" }; _agents.value = on
    }
    suspend fun run(op: String, text: String, key: String, aad: String, expected: String, agent: Boolean = false): String =
        gate.run(ManagedFeature.FILES, agent) { withContext(Dispatchers.Default) {
            check(!agent || (_agents.value && !com.meshlit.BuildProfile.coreCandidate)) { "Human cryptography agent grant required" }
            val result = CryptoEngine.run(op, text, key, aad, expected)
            check(!agent || _agents.value) { "Cryptography delegation revoked" }; result
        } }
    fun specs() = listOf(McpToolSpec("crypto_local", "Local SHA-256/512, random 256-bit key, HMAC-SHA256 or AES-256-GCM. No network or storage. Agent grant required. Tool arguments/results are visible to the chat model; do not place private credentials here. Hex keys are random bytes, not passwords.", objectSchema(mapOf(
        "operation" to stringProp(enumValues = CryptoEngine.operations), "text" to stringProp(), "key_hex" to stringProp(), "aad" to stringProp(), "expected_hex" to stringProp()), listOf("operation"))) { args ->
        val obj = args as? JsonObject ?: error("Cryptography object required")
        require(obj.keys.all { it in setOf("operation", "text", "key_hex", "aad", "expected_hex") })
        fun field(name: String): String = if (name !in obj) "" else (obj[name] as? JsonPrimitive)?.takeIf { it.isString }?.content ?: error("String cryptography field required")
        val result = try { run(field("operation"), field("text"), field("key_hex"), field("aad"), field("expected_hex"), true) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { return@McpToolSpec McpToolResult.Error(McpToolResult.ErrorCode.EXEC_FAILED, "Cryptography failed: verify grant, bounded input, random hex key and authentication data") }
        McpToolResult.Json(buildJsonObject { put("result", result); put("local_only", true) })
    })
}
