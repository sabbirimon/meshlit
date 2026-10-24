package com.meshlit.qualification

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.core.common.control.OperationGate
import com.meshlit.core.hyperl.*
import com.meshlit.hyperl.HyperLDatasets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class HyperLNativeAndroidTest {
    @Test fun packagedNativeRecipesAndPrivateAuthenticatedStorage()=runBlocking {
        for(id in HyperLLibrary.ids){val r=HyperLLibrary.recipe(id)
            assertArrayEquals(id,HyperLCpuBackend().execute(r.program,r.example),NativeCpu.execute(r.program,r.example,16L*1024*1024),0f)
        }
        assertEquals(1f,NativeCpu.precise(floatArrayOf(16777216f,1f,-16777216f),16L*1024*1024),0f)
        assumeTrue(Build.VERSION.SDK_INT>=26)
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val root=File(context.noBackupFilesDir,"hyperl-test-${java.util.UUID.randomUUID()}")
        try {
            val store=HyperLDatasets(root,OperationGate());val data=ByteArray(4*1024*1024+17){(it%251).toByte()}
            val (id,s)=store.import{ByteArrayInputStream(data)}
            assertEquals(2,s.chunks);assertEquals(s,store.verify(id))
            val (next,rotated)=store.rotate(id);assertEquals(s,rotated)
            store.export(next){assertArrayEquals(data,it.readBytes())}
        }finally{root.deleteRecursively()}
    }
}
