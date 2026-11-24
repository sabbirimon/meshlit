package com.meshlit.qualification
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.koin.core.context.GlobalContext
import kotlinx.serialization.json.*
import java.io.File
class DeviceConnectionInventoryTest {
    @Test fun recordConfiguredConnectionMetadataWithoutCredentials() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val koin=GlobalContext.get()
        val ssh=koin.get<com.meshlit.ssh.SshConnections>()
        val bridge=koin.get<com.meshlit.control.WebBridgeHost>()
        val app=context.applicationContext as com.meshlit.MeshlitApplication
        val report=buildJsonObject {
            put("ssh",buildJsonArray{ssh.connections.value.forEach{s->add(buildJsonObject{put("id",s.id);put("name",s.name);put("host",s.host);put("port",s.port)})}})
            put("mcp",buildJsonArray{app.mcpClientPool.configured.value.values.forEach{s->add(buildJsonObject{put("id",s.id);put("name",s.name);put("enabled",s.enabled)})}})
            put("enrolledDevices",buildJsonArray{bridge.directory.read().devices.forEach{d->add(buildJsonObject{put("id",d.id);put("name",d.name);put("state",d.state.name);put("kind",d.kind.name)})}})
            put("credentialsExported",false)
        }
        File(context.filesDir,"qualification/connection-inventory.json").apply{parentFile!!.mkdirs();writeText(report.toString())}
    }
}
