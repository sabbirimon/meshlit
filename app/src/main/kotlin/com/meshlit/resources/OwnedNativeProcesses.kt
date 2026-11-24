package com.meshlit.resources

import android.os.Process
import android.system.Os
import android.system.OsConstants
import java.io.File

data class OwnedNativeReading(val pid:Int,val startedTicks:Long,val cpuMs:Long,val rssBytes:Long,val threadCount:Int)
/** Read only direct, same-UID child executables from this app's installed native runtime. */
object OwnedNativeProcesses {
    fun read():List<OwnedNativeReading> {
        val parent=Process.myPid();val uid=Process.myUid()
        val hz=Os.sysconf(OsConstants._SC_CLK_TCK);val page=Os.sysconf(OsConstants._SC_PAGESIZE)
        if(hz<=0 || page<=0)return emptyList()
        val threadChildren=File("/proc/$parent/task").listFiles().orEmpty().take(512).flatMap{thread->
            runCatching{File(thread,"children").readText().trim().split(Regex("\\s+")).mapNotNull{it.toIntOrNull()}}.getOrDefault(emptyList())
        }.distinct().take(16)
        // Android kernels may omit task/children even for our own process. The
        // proc directory's UID permits a bounded fallback without reading another
        // app's command line or status. Parent and executable are checked below.
        val sameUid=if(threadChildren.isEmpty())File("/proc").listFiles().orEmpty().asSequence().take(4096)
            .filter{it.name.toIntOrNull()!=null && runCatching{Os.stat(it.path).st_uid==uid}.getOrDefault(false)}
            .mapNotNull{it.name.toIntOrNull()}.take(32).toList() else emptyList()
        val children=(threadChildren+sameUid).distinct().filter{it!=parent}.take(16)
        return children.mapNotNull{pid->runCatching{
            val proc=File("/proc/$pid")
            val status=File(proc,"status").readLines()
            require(status.firstOrNull{it.startsWith("PPid:")}?.substringAfter(':')?.trim()?.toInt()==parent)
            require(status.firstOrNull{it.startsWith("Uid:")}?.substringAfter(':')?.trim()?.split(Regex("\\s+"))?.first()?.toInt()==uid)
            val command=File(proc,"cmdline").readBytes().toString(Charsets.UTF_8).substringBefore('\u0000')
            require(File(command).name in setOf("libmeshlit_pipeline_server.so","libmeshlit_pipeline_worker.so"))
            val fields=File(proc,"stat").readText().substringAfterLast(") ").split(' ')
            OwnedNativeReading(pid,fields[19].toLong(),(fields[11].toLong()+fields[12].toLong())*1000/hz,
                fields[21].toLong()*page,File(proc,"task").listFiles()?.size ?: 0)
        }.getOrNull()}
    }
}
