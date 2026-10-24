package com.meshlit.openclaw

import android.app.Application
import android.net.Uri
import com.meshlit.settings.SettingsRepository
import com.meshlit.core.common.control.OperationGate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.android.ext.koin.androidContext

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[33])
class PhoneAdministrationTest {
    private fun host(gate:OperationGate=OperationGate()):PhoneAdministration {
        val context=RuntimeEnvironment.getApplication()
        val settings=SettingsRepository(context)
        return PhoneAdministration(context,AndroidControl(context,settings),settings,gate)
    }
    @Test fun fileAndWebApksCannotBeApprovedAsPickerGrants() {
        val host=host()
        for(uri in listOf("file:///data/private.apk","https://example.com/app.apk"))
            assertThrows(IllegalArgumentException::class.java){host.approveApk(Uri.parse(uri))}
    }
    @Test fun backgroundAdministrationDoesNotOpenSystemUi()=runBlocking {
        assertFalse(com.meshlit.MainActivity.foregroundActive)
        val host=host()
        for(action in listOf("install","uninstall","permissions"))
            assertTrue(runCatching{host.request(action,"com.example.app",true)}.isFailure)
    }
    @Test fun statusDoesNotPretendToHaveAdbRootOrSilentAdministration() {
        val status=host().status()
        assertEquals((!com.meshlit.BuildConfig.PLAY_REVIEW).toString(),status["packageAdministrationAvailable"].toString())
        if(com.meshlit.BuildConfig.PLAY_REVIEW) assertEquals("false",status["installSourceAllowed"].toString())
        assertEquals("false",status["silentAdministrationImplemented"].toString())
        assertEquals("false",status["wirelessAdbAdapterImplemented"].toString())
        assertEquals("false",status["rootAdapterImplemented"].toString())
        assertNull(status["humanConfirmationRequired"])
    }
    @Test fun restrictedStatusSkipsPermissionQueryAndFullStatusUsesTheActualOsResult() {
        assertFalse(installSourceAllowed(false){error("The narrower build must not call the permission-requiring API")})
        var queries=0
        assertFalse(installSourceAllowed(true){queries++;false})
        assertTrue(installSourceAllowed(true){queries++;true})
        assertEquals(2,queries)
    }
    @Test fun emergencyStopBlocksHumanAndAgentAdministrationBeforeAndroidDispatch()=runBlocking {
        val gate=OperationGate().apply{emergencyStop()}
        val host=host(gate)
        for(agent in listOf(false,true)) {
            val failure=runCatching{host.request("permissions","com.example.app",agent)}.exceptionOrNull()
            assertTrue(failure is IllegalStateException)
            assertTrue(failure!!.message!!.contains("Operation stopped or disabled: AUTOMATION"))
        }
    }
    @Test fun productionDependencyGraphResolvesAdministrationWithoutAStandaloneGateBinding() {
        val koin=startKoin {
            androidContext(RuntimeEnvironment.getApplication())
            modules(com.meshlit.di.coreModule)
        }.koin
        try {
            val administration=koin.get<PhoneAdministration>()
            assertEquals("false",administration.status()["silentAdministrationImplemented"].toString())
        } finally {stopKoin()}
    }
}
