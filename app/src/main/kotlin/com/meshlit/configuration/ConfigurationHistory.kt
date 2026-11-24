package com.meshlit.configuration
import android.content.Context
import android.util.AtomicFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.*
import com.meshlit.core.mcp.control.ConfigurationSnapshots
import com.meshlit.core.mcp.control.ConfigurationDelta
import java.io.File
@Serializable data class ConfigurationCheckpoint(val id:String,val at:Long,val configuration:PortableConfiguration,val delta:ConfigurationDelta?=null)
/** Small private configuration history; excludes credential/identity/grant stores and model binaries. */
class ConfigurationHistory(context:Context) {
    private val file=AtomicFile(File(context.filesDir,"configuration-history.json"))
    private val json=Json{ignoreUnknownKeys=false}
    @Synchronized fun list():List<ConfigurationCheckpoint> {
        if(!file.baseFile.exists())return emptyList()
        val bytes=file.openRead().use{stream->val output=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192);while(true){val n=stream.read(buffer);if(n<0)break;require(output.size()+n<=3_000_000);output.write(buffer,0,n)};output.toByteArray()}
        return json.decodeFromString<List<ConfigurationCheckpoint>>(bytes.toString(Charsets.UTF_8)).also{require(it.size<=10);it.forEach{r->r.configuration.validate()}}
    }
    @Synchronized fun save(config:PortableConfiguration):ConfigurationCheckpoint {
        config.validate();val before=list();val current=json.encodeToJsonElement(PortableConfiguration.serializer(),config).jsonObject
        val delta=before.lastOrNull()?.let{ConfigurationSnapshots.delta(json.encodeToJsonElement(PortableConfiguration.serializer(),it.configuration).jsonObject,current)}
        val entry=ConfigurationCheckpoint(java.util.UUID.randomUUID().toString(),System.currentTimeMillis(),config,delta)
        val bytes=json.encodeToString((before+entry).takeLast(10)).toByteArray();require(bytes.size<=3_000_000)
        var stream:java.io.FileOutputStream?=null
        try{stream=file.startWrite();stream.write(bytes);file.finishWrite(stream)}catch(e:Exception){stream?.let(file::failWrite);throw e}
        return entry
    }
}
