package com.meshlit.desktop

import com.meshlit.workspace.ChatTurn
import kotlinx.coroutines.*
import kotlinx.serialization.json.*
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.*

class StudioAgentTest {
    private fun call(tool: String, vararg args: Pair<String, String>) = buildJsonObject {
        put("tool", tool); putJsonObject("arguments") { args.forEach { put(it.first, it.second) } }
    }.toString()
    private fun fixture(test: (Path) -> Unit) {
        val root = Files.createTempDirectory("meshlit-agent-test")
        try { Files.writeString(root.resolve("sample.txt"), "before\n"); test(root) } finally { root.toFile().deleteRecursively() }
    }
    private suspend fun run(root: Path?, mode: StudioMode, policy: StudioApproval = StudioApproval.ASK,
        dirty: () -> Boolean = { false }, review: suspend (StudioReview) -> Boolean = { true },
        actions: List<String>): String {
        var index = 0
        return StudioAgent(root, dirty).run(mode, policy, listOf(ChatTurn("user", "Update my sample")),
            generate = { _, _ -> actions[index++ % actions.size] }, review = review, event = {})
    }

    @Test fun planBlocksWritesEvenUnderFullPolicy() = fixture { root -> runBlocking {
        assertFailsWith<IllegalStateException> { run(root, StudioMode.PLAN, StudioApproval.FULL, actions = listOf(call("create_file", "path" to "new.txt", "content" to "no"))) }
        assertFalse(Files.exists(root.resolve("new.txt")))
    } }
    @Test fun editReviewsAndAppliesExactlyOneChange() = fixture { root -> runBlocking {
        val reviews = mutableListOf<StudioReview>()
        val result = run(root, StudioMode.EDIT, review = { reviews += it; true }, actions = listOf(
            call("read_file", "path" to "sample.txt"), call("replace_text", "path" to "sample.txt", "old" to "before", "new" to "after"),
            call("create_file", "path" to "never.txt", "content" to "never")))
        assertEquals("after\n", Files.readString(root.resolve("sample.txt")))
        assertFalse(Files.exists(root.resolve("never.txt")))
        assertTrue(result.contains("one file change")); assertEquals(2, reviews.size)
        assertTrue(reviews.last().detail.contains("Remove:\nbefore\n\nInsert:\nafter"))
    } }
    @Test fun declineAndCancellationDoNotWrite() = fixture { root -> runBlocking {
        val action = call("create_file", "path" to "never.txt", "content" to "no")
        assertTrue(run(root, StudioMode.AUTOPILOT, review = { false }, actions = listOf(action)).contains("declined"))
        val waiting = CompletableDeferred<Unit>()
        val job = launch { run(root, StudioMode.AUTOPILOT, review = { waiting.complete(Unit); awaitCancellation() }, actions = listOf(action)) }
        waiting.await(); job.cancelAndJoin()
        assertFalse(Files.exists(root.resolve("never.txt")))
    } }
    @Test fun staleOrDirtyEditorPreventsReplacement() = fixture { root -> runBlocking {
        val actions = listOf(call("read_file", "path" to "sample.txt"), call("replace_text", "path" to "sample.txt", "old" to "before", "new" to "after"))
        assertFailsWith<IllegalStateException> { run(root, StudioMode.AUTOPILOT, dirty = { true }, actions = actions) }
        assertEquals("before\n", Files.readString(root.resolve("sample.txt")))
        assertFailsWith<IllegalStateException> { run(root, StudioMode.AUTOPILOT, review = { if(it.writes) Files.writeString(root.resolve("sample.txt"), "external edit"); true }, actions = actions) }
        assertEquals("external edit", Files.readString(root.resolve("sample.txt")))
    } }
    @Test fun workspaceBoundariesAndPrivatePathsAreEnforced() = fixture { root -> runBlocking {
        for(path in listOf("../outside", "/etc/passwd", ".env", "folder/../sample.txt", "secret.key")) {
            assertFailsWith<IllegalArgumentException> { run(root, StudioMode.PLAN, actions = listOf(call("read_file", "path" to path))) }
        }
        Files.createSymbolicLink(root.resolve("linked.txt"), root.resolve("sample.txt"))
        assertFailsWith<IllegalArgumentException> { run(root, StudioMode.PLAN, actions = listOf(call("read_file", "path" to "linked.txt"))) }
        assertFailsWith<IllegalStateException> { run(null, StudioMode.PLAN, actions = listOf(call("read_file", "path" to "sample.txt"))) }
    } }
    @Test fun autopilotHasBoundedStepsAndSafePolicyStillAsksBeforeWrites() = fixture { root -> runBlocking {
        var rounds = 0; var approvals = 0
        val result = StudioAgent(root, { false }).run(StudioMode.AUTOPILOT, StudioApproval.SAFE, listOf(ChatTurn("user", "list")),
            generate = { _, _ -> rounds++; call("list_files", "path" to ".") }, review = { approvals++; true }, event = {})
        assertEquals(8, rounds); assertEquals(0, approvals); assertTrue(result.startsWith("Step limit"))
        run(root, StudioMode.EDIT, StudioApproval.SAFE, review = { approvals++; true }, actions = listOf(call("create_file", "path" to "new.txt", "content" to "created")))
        assertEquals(1, approvals); assertEquals("created", Files.readString(root.resolve("new.txt")))
    } }
    @Test fun malformedAndUnregisteredActionsNeverRun() {
        for(raw in listOf("rm -rf /", "```json\n{}\n```", call("shell", "command" to "pwd"), call("system_status", "extra" to "no"), "{\"tool\":\"finish\",\"arguments\":{\"text\":false}}")) {
            assertFails { StudioAction.parse(raw) }
        }
    }
    @Test fun createNeverOverwritesExistingFileAndReplaceRequiresReadReceipt() = fixture { root -> runBlocking {
        assertFails { run(root, StudioMode.EDIT, StudioApproval.FULL, actions = listOf(call("create_file", "path" to "sample.txt", "content" to "overwritten"))) }
        assertFails { run(root, StudioMode.EDIT, StudioApproval.FULL, actions = listOf(call("replace_text", "path" to "sample.txt", "old" to "before", "new" to "after"))) }
        assertEquals("before\n", Files.readString(root.resolve("sample.txt")))
    } }
}
