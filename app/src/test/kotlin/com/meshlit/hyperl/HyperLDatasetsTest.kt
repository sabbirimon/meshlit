package com.meshlit.hyperl

import com.meshlit.core.common.control.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.file.Files

class HyperLDatasetsTest {
    @Test fun workspaceRotationGateCleanupAndSelectedPlaintextExport()=runBlocking {
        val root=Files.createTempDirectory("meshlit-datasets").toFile()
        try {
            val gate=OperationGate();val store=HyperLDatasets(root,gate)
            val data=ByteArray(4*1024*1024+17){(it%251).toByte()}
            val (id,s)=store.import{ByteArrayInputStream(data)}
            assertEquals(2,s.chunks);assertEquals(s,store.verify(id))
            val (next,rotated)=store.rotate(id);assertEquals(s,rotated);assertEquals(2,store.ids().size)
            assertFalse(java.io.File(root,"$id/key").readBytes().contentEquals(java.io.File(root,"$next/key").readBytes()))
            assertEquals(s,store.export(next){assertArrayEquals(data,it.readBytes())})
            assertFalse(root.listFiles()!!.any{it.name.startsWith(".export-")})
            try{store.export(id){error("Provider failed")};fail("Provider")}catch(_:IllegalStateException){}
            assertFalse(root.listFiles()!!.any{it.name.startsWith(".export-")})
            gate.setFeature(ManagedFeature.HYPERL,false)
            try{store.import{ByteArrayInputStream(data)};fail("Gate")}catch(_:IllegalStateException){}
            gate.setFeature(ManagedFeature.HYPERL,true)
            try{store.import{object:InputStream(){override fun read():Int=throw java.io.IOException("Provider failed")}};fail("Import failure")}catch(_:java.io.IOException){}
            assertFalse(root.listFiles()!!.any{it.name.startsWith(".pending-")})
            store.delete(next);assertEquals(listOf(id),store.ids())
        }finally{root.deleteRecursively()}
    }
    @Test fun entryBoundsCancellationAndWorkspaceRestart()=runBlocking {
        val root=Files.createTempDirectory("meshlit-datasets-stop").toFile()
        try {
            val store=HyperLDatasets(root,OperationGate())
            repeat(4){store.import{ByteArrayInputStream(byteArrayOf(1,2,3))}}
            try{store.rotate(store.ids().first());fail("Four entry quota")}catch(_:IllegalArgumentException){}
            assertEquals(4,HyperLDatasets(root,OperationGate()).ids().size)
            try{store.verify("../key");fail("Unsafe ID")}catch(_:IllegalArgumentException){}
            store.delete(store.ids().first())
            val cancelled=launch(start=CoroutineStart.LAZY){store.import{ByteArrayInputStream(byteArrayOf(1))}}
            cancelled.cancel();cancelled.start();cancelled.join()
            assertEquals(3,store.ids().size)
            val orphan=java.io.File(root,".export-1234");orphan.mkdir();java.io.File(orphan,"plaintext").writeText("interrupted export")
            val unrelated=java.io.File(root,"keep");unrelated.mkdir();java.io.File(unrelated,"owner-file").writeText("retain")
            HyperLDatasets(root,OperationGate())
            assertFalse(orphan.exists());assertTrue(unrelated.isDirectory)
        }finally{root.deleteRecursively()}
    }
}
