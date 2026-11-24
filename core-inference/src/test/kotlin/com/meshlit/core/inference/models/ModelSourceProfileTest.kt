package com.meshlit.core.inference.models

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class ModelSourceProfileTest {
    @Test fun sourceSurvivesStorageAndDoesNotEnableItself() {
        val p=OnlineProfile("local-model", "Local models", "http://127.0.0.1:8080/v1", sourceKind=ModelSourceKind.MULTI_LOCAL)
        p.validate()
        val restored=Json.decodeFromString<OnlineProfile>(Json.encodeToString(p))
        assertEquals(ModelSourceKind.MULTI_LOCAL, restored.sourceKind)
        assertFalse(restored.enabled)
        assertFalse(restored.agentAllowed)
    }
    @Test fun cleartextIsLimitedToLiteralLoopback() {
        for (url in listOf("http://192.168.1.3/v1","http://localhost/v1","http://127.0.0.1.example/v1","http://user:secret@127.0.0.1/v1")) {
            assertThrows(IllegalArgumentException::class.java) { OnlineProfile("remote", "Remote", url, sourceKind=ModelSourceKind.GROUP_CLUSTER).validate() }
        }
        OnlineProfile("remote", "Remote", "https://cluster.example/v1", sourceKind=ModelSourceKind.GROUP_CLUSTER).validate()
    }
}
