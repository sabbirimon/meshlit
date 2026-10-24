package com.meshlit.core.mcp.control
import java.io.File
import java.security.MessageDigest
import kotlinx.serialization.Serializable
@Serializable data class WorkspaceFile(val name:String,val bytes:Long,val sha256:String,val text:String?=null)
/** App-managed source files only; hash preconditions prevent blind agent overwrites. */
class CodeWorkspace(private val directory:File){
    init{directory.mkdirs()}
    private fun file(name:String):File{
        require(name.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,119}")) && !name.endsWith(".tmp") && name !in setOf(".","..")){"Use a simple source filename"}
        return File(directory,name).also{require(it.canonicalFile.parentFile==directory.canonicalFile){"File outside workspace"}}
    }
    private fun hash(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
    @Synchronized fun list():List<WorkspaceFile> = directory.listFiles().orEmpty().filter{it.isFile && !it.name.endsWith(".tmp")}
        .sortedBy{it.name}.take(64).map{read(it.name,false)}
    @Synchronized fun read(name:String,includeText:Boolean=true):WorkspaceFile{
        val path=file(name);require(path.isFile && path.length()<=1_048_576){"File unavailable or too large"}
        val bytes=path.readBytes();return WorkspaceFile(name,bytes.size.toLong(),hash(bytes),if(includeText) bytes.toString(Charsets.UTF_8) else null)
    }
    @Synchronized fun write(name:String,text:String,expectedSha256:String?):WorkspaceFile{
        val path=file(name);val bytes=text.toByteArray();require(bytes.size<=1_048_576){"Source file exceeds 1 MiB"}
        if(path.exists()) require(expectedSha256!=null && read(name,false).sha256==expectedSha256){"File changed; read it again before saving"}
        else require(expectedSha256==null && list().size<64){"Workspace file limit or stale precondition"}
        require(directory.listFiles().orEmpty().sumOf{it.length()}-path.length()+bytes.size<=20L*1024*1024){"Workspace storage budget exceeded"}
        val temporary=File.createTempFile("workspace-", ".tmp", directory);try{java.io.FileOutputStream(temporary).use{it.write(bytes);it.fd.sync()};check(temporary.renameTo(path)){"Unable to save source file"}}finally{temporary.delete()}
        return read(name,false)
    }
}
