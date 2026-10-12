package com.meshlit.settings
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.meshlit.core.common.RemoteEndpoint
import com.meshlit.devices.PairingPayload
import com.meshlit.ui.theme.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import java.io.File
class SettingsPersistenceRegressionTest {
    @Test fun endpointInsertUpdateRemoveAndAppearanceResetPreserveData() = runBlocking {
        val file = File.createTempFile("settings", ".preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val store = PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })
        try {
            val repo = SettingsRepository(null, store)
            val endpoint = RemoteEndpoint("B", "Phone B", "http://192.168.1.20:8080")
            repo.upsertEndpoint(endpoint)
            assertEquals(1, repo.remoteEndpointsFlow.first().size)
            repo.setActiveEndpoint("B"); repo.trustEndpoint("B", true)
            repo.setThemeMode(ThemeMode.LIGHT); repo.resetToDefaults()
            assertEquals("B", repo.activeEndpointIdFlow.first())
            assertTrue(repo.remoteEndpointsFlow.first().single().trusted)
            repo.upsertEndpoint(endpoint.copy(name = "Renamed", trusted = true))
            assertEquals(1, repo.remoteEndpointsFlow.first().size)
            repo.upsertEndpoint(endpoint.copy(baseUrl = "http://192.168.1.99:8080", trusted = true))
            assertFalse(repo.remoteEndpointsFlow.first().single().trusted)
            repo.removeEndpoint("B"); assertTrue(repo.remoteEndpointsFlow.first().isEmpty()); assertEquals("", repo.activeEndpointIdFlow.first())
        } finally { scope.cancel(); file.delete() }
    }
    @Test fun malformedPairingPayloadNeverBecomesAnEndpoint() {
        listOf("meshlit://pair?url=not-a-url", "meshlit://pair?url=ftp%3A%2F%2Fhost", "meshlit://pair?url=http%3A%2F%2Fuser%3Asecret%40host", "{\"nodeName\":\"A\",\"baseUrl\":\"javascript:alert(1)\",\"nodeId\":\"A\",\"capabilityTier\":\"MID\"}").forEach {
            assertTrue("Rejected: $it", runCatching { PairingPayload.decode(it).toRemoteEndpoint() }.isFailure)
        }
    }
}
