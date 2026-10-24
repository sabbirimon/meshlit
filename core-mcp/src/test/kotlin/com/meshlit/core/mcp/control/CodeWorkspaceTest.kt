package com.meshlit.core.mcp.control
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
class CodeWorkspaceTest {
    @Test fun hashPreconditionsAndPathScope(){
        val root=Files.createTempDirectory("source-workspace").toFile()
        try{val workspace=CodeWorkspace(root);val first=workspace.write("main.kt","hello",null)
            assertThrows(IllegalArgumentException::class.java){workspace.write("../outside.kt","bad",null)}
            assertThrows(IllegalArgumentException::class.java){workspace.write("main.kt","overwrite",null)}
            val second=workspace.write("main.kt","changed",first.sha256)
            assertThrows(IllegalArgumentException::class.java){workspace.write("main.kt","stale",first.sha256)}
            assertEquals("changed",workspace.read("main.kt").text);assertNotEquals(first.sha256,second.sha256)
        }finally{root.deleteRecursively()}
    }
}
