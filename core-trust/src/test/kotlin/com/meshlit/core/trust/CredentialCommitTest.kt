package com.meshlit.core.trust

import android.content.SharedPreferences
import java.lang.reflect.Proxy
import org.junit.Assert.*
import org.junit.Test

class CredentialCommitTest {
    @Test fun profileAndKeyUseOneCommitAndFailureIsNotAcknowledged() {
        val changes = linkedMapOf<String, String?>()
        var commits = 0
        var succeeds = true
        val editor = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { proxy, method, args ->
            when(method.name) {
                "putString" -> { changes[args!![0] as String] = args[1] as String; proxy }
                "remove" -> { changes[args!![0] as String] = null; proxy }
                "commit" -> { commits++; succeeds }
                else -> error("Unexpected editor operation")
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(javaClass.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, _ ->
            check(method.name == "edit"); editor
        } as SharedPreferences
        commitCredentialChanges(prefs, mapOf("profiles" to "fixture", "key-id" to null))
        assertEquals(1, commits); assertEquals(mapOf("profiles" to "fixture", "key-id" to null), changes)
        succeeds = false
        assertThrows(IllegalStateException::class.java) { commitCredentialChanges(prefs, mapOf("key-id" to "fixture-secret")) }
        assertEquals(2, commits)
    }
}
