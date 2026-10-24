package com.meshlit.desktop
import java.nio.file.Path
import kotlinx.coroutines.*
import kotlin.test.*
import org.junit.Test
class HumanCommandRunnerTest {
    @Test fun realExitOutputAndCancellation(): Unit = runBlocking {
        org.junit.Assume.assumeFalse("POSIX execution check; Windows needs independent PowerShell qualification",System.getProperty("os.name").contains("Windows"))
        HumanCommandRunner().use { runner ->
            val result=runner.run("printf 'real terminal'; printf 'diagnostic' >&2; exit 7",Path.of(System.getProperty("java.io.tmpdir")).toAbsolutePath(),Path.of("/bin/sh"))
            assertEquals(7,result.exit);assertEquals("real terminal",result.stdout);assertEquals("diagnostic",result.stderr)
            val child=launch { runner.run("sleep 60",Path.of("/tmp"),Path.of("/bin/sh")) }
            delay(200); withTimeout(3000){child.cancelAndJoin()}
            assertEquals(0,runner.run("exit 0",Path.of("/tmp"),Path.of("/bin/sh")).exit)
        }
    }
    @Test fun drainsOutputBeyondLimit(): Unit = runBlocking {
        org.junit.Assume.assumeFalse("POSIX execution check; Windows needs independent PowerShell qualification",System.getProperty("os.name").contains("Windows"))
        HumanCommandRunner().use { runner ->
            val out=withTimeout(5000){runner.run("head -c 80000 /dev/zero",Path.of("/tmp"),Path.of("/bin/sh"))}
            assertTrue(out.truncated);assertEquals(65536,out.stdout.length);assertEquals(0,out.exit)
        }
    }
}
