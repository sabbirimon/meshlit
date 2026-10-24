package com.meshlit.recovery

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.meshlit.core.inference.models.ModelFiles
import com.meshlit.core.inference.models.ModelRuntimeOptions
import com.meshlit.core.trust.EncryptedCredentialStore
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.*
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec

@Serializable data class NativeCheckpoint(val id:String,val createdAtMs:Long,val modelSha256:String,
    val runtimeSha256:String,val options:ModelRuntimeOptions,val tokens:Int,val bytes:Long,val sha256:String)

/** One-device native CPU slot snapshots. No portable/RPC KV or distributed durability claim.
 * Caller serializes under the coordinator/host locks and executes on an IO dispatcher. */
class NativeCheckpointStore(context:Context) {
    private val directory=File(context.filesDir,"native-checkpoints").apply{check(mkdirs() || isDirectory)}
    private val registry=EncryptedCredentialStore(context,"native-checkpoint-index")
    private val json=Json{ignoreUnknownKeys=false}
    private val alias="meshlit-native-checkpoint-aes-v1"
    companion object {const val MAX_BYTES=128L*1024*1024;const val TOTAL_BYTES=256L*1024*1024}
    private fun key():java.security.Key {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        if(!store.containsAlias(alias)) KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply{
            init(KeyGenParameterSpec.Builder(alias,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setKeySize(256).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
            generateKey()
        }
        return store.getKey(alias,null)
    }
    fun list():List<NativeCheckpoint> = registry.get("items")?.let{json.decodeFromString<List<NativeCheckpoint>>(it)}.orEmpty().also{items ->
        require(items.size<=16 && items.map{it.id}.distinct().size==items.size)
        items.forEach{require(it.id.matches(Regex("[a-f0-9-]{36}")) && it.bytes in 1..MAX_BYTES && it.tokens>0)}
    }
    init {
        val retained=list().map{it.id+".kv.aes"}.toSet()
        directory.listFiles()?.filter{it.name.endsWith(".tmp") || it.name.endsWith(".kv.aes") && it.name !in retained}?.forEach{
            check(it.delete()){"Cannot clean interrupted checkpoint storage"}
        }
    }
    private fun file(id:String)=File(directory,"$id.kv.aes")
    private fun aad(item:NativeCheckpoint)=json.encodeToString(item).toByteArray(Charsets.UTF_8)
    suspend fun save(raw:File,modelHash:String,runtimeHash:String,options:ModelRuntimeOptions,tokens:Int):NativeCheckpoint {
        require(raw.isFile && raw.length() in 1..MAX_BYTES && tokens>0){"Native KV snapshot is empty or exceeds 128 MiB"}
        require(modelHash.matches(Regex("[a-f0-9]{64}")) && runtimeHash.matches(Regex("[a-f0-9]{64}")));options.validate();require(tokens<=options.contextSize)
        val items=list();require(items.size<16 && items.sumOf{it.bytes}+raw.length()<=TOTAL_BYTES){"Checkpoint quota reached; delete an old checkpoint"}
        require(directory.usableSpace>raw.length()+16L*1024*1024){"Insufficient checkpoint disk space"}
        val item=NativeCheckpoint(UUID.randomUUID().toString(),System.currentTimeMillis(),modelHash,runtimeHash,options,tokens,raw.length(),ModelFiles.sha256(raw))
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key());updateAAD(aad(item))}
        require(cipher.iv.size==12){"Unsupported AES GCM IV"}
        val target=file(item.id);val temp=File(directory,"${item.id}.tmp")
        try {
            FileOutputStream(temp).use{output ->
                DataOutputStream(output).apply{writeInt(0x4d4b5631);writeInt(cipher.iv.size);write(cipher.iv);flush()}
                raw.inputStream().use{input->transform(input,output,cipher,MAX_BYTES)}
                output.fd.sync()
            }
            check(temp.renameTo(target)){"Cannot commit checkpoint blob"}
            registry.putCommitted("items",json.encodeToString(items+item))
            return item
        } catch(e:Throwable){target.delete();throw e}
        finally{temp.delete()}
    }
    suspend fun restore(id:String,raw:File,modelHash:String,runtimeHash:String,options:ModelRuntimeOptions):NativeCheckpoint {
        val item=list().single{it.id==id}
        require(item.modelSha256==modelHash && item.runtimeSha256==runtimeHash && item.options==options){"Checkpoint requires identical model, native binary, context and cache precision"}
        val encrypted=file(item.id);require(encrypted.isFile && encrypted.length()<=MAX_BYTES+64){"Checkpoint blob missing or exceeds limit"}
        require(raw.parentFile!!.usableSpace>item.bytes+16L*1024*1024){"Insufficient restore disk space"}
        try {
            DataInputStream(encrypted.inputStream()).use{input ->
                require(input.readInt()==0x4d4b5631 && input.readInt()==12){"Invalid encrypted checkpoint"}
                val iv=ByteArray(12);input.readFully(iv)
                val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,iv));updateAAD(aad(item))}
                FileOutputStream(raw).use{output->transform(input,output,cipher,MAX_BYTES+16);output.fd.sync()}
            }
            require(raw.length()==item.bytes && ModelFiles.sha256(raw)==item.sha256){"Checkpoint integrity failed"}
            return item
        } catch(e:Throwable){raw.delete();throw e}
    }
    fun delete(id:String){val items=list();require(items.any{it.id==id});registry.putCommitted("items",json.encodeToString(items.filterNot{it.id==id}));check(!file(id).exists() || file(id).delete()){"Checkpoint index removed; blob cleanup failed"}}
    private suspend fun transform(input:InputStream,output:OutputStream,cipher:Cipher,limit:Long){
        val buffer=ByteArray(64*1024);var consumed=0L
        while(true){currentCoroutineContext().ensureActive();val count=input.read(buffer);if(count<0) break;consumed+=count;require(consumed<=limit){"Checkpoint exceeds size limit"};cipher.update(buffer,0,count)?.let{output.write(it)}}
        // Explicit doFinal propagates failed GCM authentication; never pass unchecked bytes to native code.
        output.write(cipher.doFinal())
    }
}
