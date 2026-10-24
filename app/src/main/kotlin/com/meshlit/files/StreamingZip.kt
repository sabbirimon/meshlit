package com.meshlit.files

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.PushbackInputStream
import java.text.Normalizer
import java.util.Locale
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveLimits(val maxEntries:Int=10000,val maxExpandedBytes:Long=32L*1024*1024*1024) {
    fun validate(){require(maxEntries in 1..10000 && maxExpandedBytes in 1..128L*1024*1024*1024)}
}
data class ArchiveInput(val name:String,val open:()->InputStream)
data class ArchiveProgress(val files:Int,val bytes:Long,val current:String)

/** Bounded streaming ZIP; never materialize models/archives in memory or honor archive links. */
object StreamingZip {
    fun checkedPath(raw:String):String {
        require(raw.isNotBlank() && raw.length<=400 && !raw.startsWith('/') && raw.none{it=='\\' || it==':' || it=='\u0000' || it.isISOControl()}) { "Unsafe archive path" }
        val path=raw.removeSuffix("/");val parts=path.split('/')
        require(parts.size<=16 && parts.all{it.isNotBlank() && it!="." && it!=".." && it.length<=200}) { "Unsafe archive path or depth" }
        return path
    }
    suspend fun create(inputs:List<ArchiveInput>,output:OutputStream,limits:ArchiveLimits,progress:(ArchiveProgress)->Unit):ArchiveProgress {
        output.use {
        limits.validate();require(inputs.isNotEmpty() && inputs.size<=limits.maxEntries)
        val names=inputs.map{checkedPath(it.name)};require(names.map{Normalizer.normalize(it,Normalizer.Form.NFC).lowercase(Locale.ROOT)}.distinct().size==names.size){ "Duplicate input file names; rename before creating ZIP" }
        var bytes=0L;var files=0;val buffer=ByteArray(65536)
        ZipOutputStream(output).use{zip->
            zip.setLevel(1)
            inputs.forEachIndexed{index,input->
                currentCoroutineContext().ensureActive();zip.putNextEntry(ZipEntry(names[index]))
                input.open().use{source->while(true){
                    currentCoroutineContext().ensureActive();val count=source.read(buffer);if(count<0) break;if(count==0) continue
                    require(bytes<=limits.maxExpandedBytes-count){ "Archive input exceeds configured byte limit" }
                    zip.write(buffer,0,count);bytes+=count;progress(ArchiveProgress(files,bytes,names[index]))
                }}
                zip.closeEntry();files++;progress(ArchiveProgress(files,bytes,names[index]))
            }
        }
        return ArchiveProgress(files,bytes,"")
        }
    }
    suspend fun extract(input:InputStream,limits:ArchiveLimits,createDirectory:(String)->Unit,openFile:(String)->OutputStream,progress:(ArchiveProgress)->Unit):ArchiveProgress {
        input.use {
        limits.validate();var bytes=0L;var files=0;var entries=0;val paths=mutableSetOf<String>();val buffer=ByteArray(65536)
        val source=PushbackInputStream(input,22)
        val header=ByteArray(4);var count=0
        while(count<4){currentCoroutineContext().ensureActive();val read=source.read(header,count,4-count);require(read>0){"Truncated ZIP header"};count+=read}
        require(header.contentEquals(byteArrayOf(80,75,3,4)) || header.contentEquals(byteArrayOf(80,75,5,6))){"Not a supported ZIP archive"}
        if(header.contentEquals(byteArrayOf(80,75,5,6))){
            val end=ByteArray(18);count=0
            while(count<18){val read=source.read(end,count,18-count);require(read>0){"Truncated empty ZIP"};count+=read}
            require(end.take(16).all{it.toInt()==0}){"Unsupported empty ZIP record"}
            val comment=(end[16].toInt() and 255)+((end[17].toInt() and 255) shl 8)
            repeat(comment){require(source.read()>=0){"Truncated ZIP comment"}}
            return ArchiveProgress(0,0,"")
        }
        source.unread(header)
        ZipInputStream(source).use{zip->while(true){
            currentCoroutineContext().ensureActive();val entry=zip.nextEntry ?: break
            require(++entries<=limits.maxEntries){ "Archive entry limit exceeded" }
            val path=checkedPath(entry.name);require(paths.add(Normalizer.normalize(path,Normalizer.Form.NFC).lowercase(Locale.ROOT))){ "Duplicate archive entry" }
            require(entry.size<0 || entry.size<=limits.maxExpandedBytes){ "Archive entry exceeds byte limit" }
            if(entry.isDirectory) createDirectory(path) else {
                openFile(path).use{output->while(true){
                    currentCoroutineContext().ensureActive();val count=zip.read(buffer);if(count<0) break;if(count==0) continue
                    require(bytes<=limits.maxExpandedBytes-count){ "Expanded archive exceeds configured byte limit" }
                    output.write(buffer,0,count);bytes+=count;progress(ArchiveProgress(files,bytes,path))
                }}
                files++;progress(ArchiveProgress(files,bytes,path))
            }
            zip.closeEntry()
        }}
        return ArchiveProgress(files,bytes,"")
        }
    }
}
