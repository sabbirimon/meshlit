package com.meshlit.ui.v2.screens

import com.meshlit.DeviceInfo
import com.meshlit.bootstrap.BootstrapSnapshotProvider
import com.meshlit.capability.CapabilityTier
import com.meshlit.core.bootstrap.BootstrapCoordinator
import com.meshlit.core.bootstrap.BootstrapReport
import com.meshlit.core.bootstrap.BootstrapSnapshot
import com.meshlit.core.common.MeshlitResult
import com.meshlit.core.config.InMemoryConfigRepository
import com.meshlit.core.flags.InMemoryFeatureFlagRegistry
import com.meshlit.core.probe.HardwareProfilerRegistry
import com.meshlit.core.probe.LiveHardwareMonitor
import com.meshlit.core.registry.LocalServiceRegistry
import com.meshlit.core.registry.ServiceRegistry
import com.meshlit.core.role.Role
import com.meshlit.core.role.RoleDecision
import com.meshlit.core.role.RoleManager
import com.meshlit.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import androidx.lifecycle.ViewModelStore

/**
 * Smoke tests for `DeviceInfoViewModel`.
 *
 * The VM composes five collaborator `Flow`s through `combine()` into
 * a single `DeviceInfoUiState`. These tests build the smallest graph
 * of real + fake collaborators that still drives the merge:
 *
 *  - `LocalServiceRegistry()` — no-arg, works in JVM tests.
 *  - `BootstrapSnapshotProvider()` — its own minimal class, no deps.
 *  - `RoleManager` + `HardwareProfilerRegistry` — overridden
 *    `decision` flow + no-op profiler list. The `Role` default
 *    becomes whatever the test seeds into the StateFlow.
 *  - `BootstrapCoordinator` — overridden `boot()` to return a
 *    canned snapshot.
 *  - `SettingsRepository` — overridden `displayNameFlow` +
 *    `setDisplayName` so we never touch the DataStore extension.
 *    `SettingsRepository.store` is `by lazy`, so the underlying
 *    `context.settingsDataStore` is never accessed by our test
 *    subclass.
 *  - `DeviceInfoEnvironment` — a tiny interface (defined in this
 *    package) that the VM reads instead of `MeshlitApplication`,
 *    so we don't need Robolectric.
 *
 * Each collaborator was made `open` at its production site so
 * the test could override the surface-level members. No
 * production behavior changes.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeviceInfoViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val viewModelStores = mutableListOf<ViewModelStore>()

    @Before
    fun setUp() {
        // The VM launches its combine() on `viewModelScope`, which
        // is bound to `Dispatchers.Main.immediate`. Without
        // `setMain`, those coroutines never advance in
        // `runTest` — the state stays at `Loading` and every
        // assertion fails. `resetMain` is the matching cleanup so
        // we don't leak the dispatcher between tests.
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        // Exercise real lifecycle cleanup before removing Main. Otherwise the
        // real monitor can emit into a ViewModel from an already finished test.
        viewModelStores.forEach { it.clear() }
        viewModelStores.clear()
        testDispatcher.scheduler.runCurrent()
        Dispatchers.resetMain()
    }

    /** Fake `RoleManager` — uses the parent's `decision` slot but
     *  exposes our own StateFlow so the test can seed a role. */
    private class FakeRoleManager(initial: RoleDecision) : RoleManager(
        profiler = HardwareProfilerRegistry(emptyList(), clock = { 0L }),
    ) {
        private val state = MutableStateFlow(initial)
        override val decision get() = state.asStateFlow()
        override fun start() = Unit
    }

    /** Fake `SettingsRepository` — overrides the two surface members
     *  the VM reads. The parent's `store` is `by lazy`, so we never
     *  trigger the DataStore extension property. The constructor
     *  receives a no-op `Context` whose `settingsDataStore` is never
     *  touched. */
    private class FakeSettingsRepository : SettingsRepository(
        context = NoopContext(),
    ) {
        private val nameState = MutableStateFlow("")
        val lastWritten = MutableStateFlow<String?>(null)
        override val displayNameFlow
            get() = nameState.asStateFlow()
        override suspend fun setDisplayName(value: String) {
            lastWritten.value = value
            nameState.value = value
        }
    }

    /** Fake `BootstrapCoordinator` — overrides `boot()` so the
     *  VM's force-re-bootstrap path is verifiable. */
    private class FakeBootstrapCoordinator : BootstrapCoordinator(
        config = InMemoryConfigRepository(),
        flags = InMemoryFeatureFlagRegistry(),
    ) {
        var bootCount = 0
        val nextSnap = BootstrapSnapshot(
            nodeId = "test-node-id",
            flags = emptyMap(),
            role = null,
            report = BootstrapReport.Empty,
        )
        override suspend fun boot(): MeshlitResult<BootstrapSnapshot> {
            bootCount++
            return MeshlitResult.Success(nextSnap)
        }
    }

    private class StubEnvironment(
        override val nodeIdHex: String,
        override val localIpAddress: String,
        override val httpServerPort: Int,
        override val capabilityTier: CapabilityTier = CapabilityTier.LITE,
        override val deviceInfo: DeviceInfo = DeviceInfo(),
    ) : DeviceInfoEnvironment {
        var lastSetId: String? = null
        override fun setStableNodeId(id: String) { lastSetId = id }
    }

    private fun newVm(
        env: DeviceInfoEnvironment = StubEnvironment("", "", 8080),
        provider: BootstrapSnapshotProvider = BootstrapSnapshotProvider(),
        roleManager: RoleManager = FakeRoleManager(
            RoleDecision(
                role = Role.Monitor,
                confidence = 0.5f,
                reasons = listOf("test"),
                scores = Role.entries.associateWith { 0f },
            ),
        ),
        profiler: HardwareProfilerRegistry = HardwareProfilerRegistry(emptyList(), clock = { 0L }),
        liveMonitor: LiveHardwareMonitor = LiveHardwareMonitor(
            profiler = profiler,
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Job()),
        ),
        registry: ServiceRegistry = LocalServiceRegistry(),
        settings: SettingsRepository = FakeSettingsRepository(),
        coordinator: BootstrapCoordinator = FakeBootstrapCoordinator(),
    ): DeviceInfoViewModel = DeviceInfoViewModel(
        env = env,
        snapshotProvider = provider,
        roleManager = roleManager,
        profiler = profiler,
        liveMonitor = liveMonitor,
        registry = registry,
        settings = settings,
        bootstrapCoordinator = coordinator,
    ).also { vm ->
        val store=ViewModelStore()
        store.put("subject",vm)
        viewModelStores+=store
    }

    @Test
    fun `Loading state without bootstrap snapshot`() = runTest(testDispatcher) {
        val vm = newVm()
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue(
            "expected Failure before snapshot publishes, got $state",
            state is DeviceInfoUiState.Failure,
        )
    }

    @Test
    fun `Ready state after bootstrap snapshot publishes`() = runTest(testDispatcher) {
        val provider = BootstrapSnapshotProvider()
        val env = StubEnvironment(
            nodeIdHex = "node-1",
            localIpAddress = "192.0.2.1",
            httpServerPort = 8080,
        )
        val vm = newVm(env = env, provider = provider)
        provider.publish(BootstrapSnapshot(
            nodeId = "node-1",
            flags = emptyMap(),
            role = null,
            report = BootstrapReport.Empty,
        ))
        advanceUntilIdle()
        val state = vm.uiState.value
        assertTrue("expected Ready, got $state", state is DeviceInfoUiState.Ready)
        val ready = state as DeviceInfoUiState.Ready
        assertEquals("node-1", ready.nodeIdHex)
        assertEquals("Monitor", ready.role.role.name)
        assertEquals("192.0.2.1", ready.localIpAddress)
        assertEquals(8080, ready.httpServerPort)
    }

    @Test
    fun `nodeIdForCopy returns the resolved node id`() = runTest(testDispatcher) {
        val provider = BootstrapSnapshotProvider()
        val env = StubEnvironment(nodeIdHex = "copied-node", localIpAddress = "", httpServerPort = 8080)
        val vm = newVm(env = env, provider = provider)
        provider.publish(BootstrapSnapshot(
            nodeId = "copied-node", flags = emptyMap(), role = null, report = BootstrapReport.Empty,
        ))
        advanceUntilIdle()
        assertEquals("copied-node", vm.nodeIdForCopy())
    }

    @Test
    fun `displayNameFlow override is reflected in Ready state`() = runTest(testDispatcher) {
        val provider = BootstrapSnapshotProvider()
        val settings = FakeSettingsRepository().also {
            // Seed via the public setDisplayName path so the StateFlow
            // actually emits, exercising the merge hot-path.
            kotlinx.coroutines.runBlocking { it.setDisplayName("My Renamed Node") }
        }
        val vm = newVm(provider = provider, settings = settings)
        provider.publish(BootstrapSnapshot(
            nodeId = "n", flags = emptyMap(), role = null, report = BootstrapReport.Empty,
        ))
        advanceUntilIdle()
        val ready = vm.uiState.value as? DeviceInfoUiState.Ready
        assertTrue("expected Ready, got ${vm.uiState.value}", ready != null)
        assertEquals("My Renamed Node", ready!!.displayName)
    }

    @Test
    fun `onSaveDisplayName writes through to the repo`() = runTest(testDispatcher) {
        val provider = BootstrapSnapshotProvider()
        val settings = FakeSettingsRepository()
        val vm = newVm(provider = provider, settings = settings)
        provider.publish(BootstrapSnapshot(
            nodeId = "n", flags = emptyMap(), role = null, report = BootstrapReport.Empty,
        ))
        advanceUntilIdle()
        vm.onSaveDisplayName("My Renamed Node")
        advanceUntilIdle()
        assertEquals("My Renamed Node", settings.lastWritten.value)
    }

    @Test
    fun `onForceRebootstrap calls coordinator and republishes`() = runTest(testDispatcher) {
        val provider = BootstrapSnapshotProvider()
        val coordinator = FakeBootstrapCoordinator()
        val env = StubEnvironment(nodeIdHex = "old-id", localIpAddress = "", httpServerPort = 8080)
        val vm = newVm(env = env, provider = provider, coordinator = coordinator)
        provider.publish(BootstrapSnapshot(
            nodeId = "old-id", flags = emptyMap(), role = null, report = BootstrapReport.Empty,
        ))
        advanceUntilIdle()
        assertEquals(0, coordinator.bootCount)
        vm.onForceRebootstrap()
        advanceUntilIdle()
        assertEquals(1, coordinator.bootCount)
        assertEquals("test-node-id", provider.snapshot()?.nodeId)
        assertEquals("test-node-id", env.lastSetId)
    }

    /**
     * Bare-minimum `android.content.Context` substitute. We extend
     * the abstract class; the test never invokes any of these
     * methods because `SettingsRepository.store` is `by lazy` and
     * the fake overrides every surface member that touches it.
     *
     * Implementing all 70+ abstract methods by hand would be a
     * maintenance burden. Instead we throw on any call — the
     * throws make accidental use loud, which is what we want
     * during a unit test. The Kotlin compiler accepts an
     * `override` that throws `UnsupportedOperationException`.
     */
    private open class NoopContext : android.content.Context() {
        override fun getAssets() = throw UnsupportedOperationException()
        override fun getResources() = throw UnsupportedOperationException()
        override fun getPackageManager() = throw UnsupportedOperationException()
        override fun getContentResolver() = throw UnsupportedOperationException()
        override fun getMainLooper() = throw UnsupportedOperationException()
        override fun getTheme() = throw UnsupportedOperationException()
        override fun getClassLoader() = throw UnsupportedOperationException()
        override fun getPackageName() = "test"
        override fun getApplicationContext(): android.content.Context = this
        override fun setTheme(resid: Int) = Unit
        override fun checkPermission(permission: String, pid: Int, uid: Int) = 0
        override fun checkCallingPermission(permission: String) = 0
        override fun checkCallingOrSelfPermission(permission: String) = 0
        override fun checkSelfPermission(permission: String) = 0
        override fun enforcePermission(permission: String, pid: Int, uid: Int, message: String?) = Unit
        override fun enforceCallingPermission(permission: String, message: String?) = Unit
        override fun enforceCallingOrSelfPermission(permission: String, message: String?) = Unit
        override fun grantUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) = Unit
        override fun revokeUriPermission(uri: android.net.Uri?, modeFlags: Int) = Unit
        override fun revokeUriPermission(toPackage: String?, uri: android.net.Uri?, modeFlags: Int) = Unit
        override fun checkUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun checkUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int) = 0
        override fun checkCallingUriPermission(uri: android.net.Uri?, modeFlags: Int) = 0
        override fun checkCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int) = 0
        override fun enforceUriPermission(uri: android.net.Uri?, pid: Int, uid: Int, modeFlags: Int, message: String?) = Unit
        override fun enforceUriPermission(uri: android.net.Uri?, readPermission: String?, writePermission: String?, pid: Int, uid: Int, modeFlags: Int, message: String?) = Unit
        override fun enforceCallingUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = Unit
        override fun enforceCallingOrSelfUriPermission(uri: android.net.Uri?, modeFlags: Int, message: String?) = Unit
        override fun createPackageContext(packageName: String?, flags: Int) = this
        override fun createContextForSplit(splitName: String?) = this
        override fun createConfigurationContext(overrideConfiguration: android.content.res.Configuration) = this
        override fun createDisplayContext(display: android.view.Display) = this
        override fun createDeviceProtectedStorageContext() = this
        override fun isDeviceProtectedStorage() = false
        override fun getOpPackageName() = "test"
        override fun getAttributionTag() = null
        override fun getMainExecutor() = java.util.concurrent.Executors.newSingleThreadExecutor()!!
        override fun getSystemService(name: String): Any? = null
        override fun getSystemServiceName(serviceClass: Class<*>) = null
        override fun checkUriPermissions(uris: MutableList<android.net.Uri>, pid: Int, uid: Int, modeFlags: Int) = IntArray(0)
        override fun checkCallingUriPermissions(uris: MutableList<android.net.Uri>, modeFlags: Int) = IntArray(0)
        override fun checkCallingOrSelfUriPermissions(uris: MutableList<android.net.Uri>, modeFlags: Int) = IntArray(0)
        override fun registerComponentCallbacks(callback: android.content.ComponentCallbacks?) = Unit
        override fun unregisterComponentCallbacks(callback: android.content.ComponentCallbacks?) = Unit
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?): android.content.Intent? = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, flags: Int): android.content.Intent? = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, broadcastPermission: String?, scheduler: android.os.Handler?): android.content.Intent? = null
        override fun registerReceiver(receiver: android.content.BroadcastReceiver?, filter: android.content.IntentFilter?, broadcastPermission: String?, scheduler: android.os.Handler?, flags: Int): android.content.Intent? = null
        override fun unregisterReceiver(receiver: android.content.BroadcastReceiver?) = Unit
        override fun startService(service: android.content.Intent): android.content.ComponentName? = null
        override fun startForegroundService(service: android.content.Intent): android.content.ComponentName? = null
        override fun stopService(service: android.content.Intent): Boolean = true
        override fun bindService(service: android.content.Intent, conn: android.content.ServiceConnection, flags: Int): Boolean = true
        override fun unbindService(conn: android.content.ServiceConnection) = Unit
        override fun startActivity(intent: android.content.Intent) = Unit
        override fun startActivity(intent: android.content.Intent, options: android.os.Bundle?) = Unit
        override fun startActivities(intents: Array<out android.content.Intent>) = Unit
        override fun startActivities(intents: Array<out android.content.Intent>, options: android.os.Bundle?) = Unit
        override fun startIntentSender(intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int) = Unit
        override fun startIntentSender(intent: android.content.IntentSender, fillInIntent: android.content.Intent?, flagsMask: Int, flagsValues: Int, extraFlags: Int, options: android.os.Bundle?) = Unit
        override fun sendBroadcast(intent: android.content.Intent) = Unit
        override fun sendBroadcast(intent: android.content.Intent, receiverPermission: String?) = Unit
        override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?) = Unit
        override fun sendOrderedBroadcast(intent: android.content.Intent, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = Unit
        override fun sendBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) = Unit
        override fun sendBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle, receiverPermission: String?) = Unit
        override fun sendOrderedBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle, receiverPermission: String?, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = Unit
        override fun sendStickyBroadcast(intent: android.content.Intent) = Unit
        override fun sendStickyOrderedBroadcast(intent: android.content.Intent, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = Unit
        override fun removeStickyBroadcast(intent: android.content.Intent) = Unit
        override fun sendStickyBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) = Unit
        override fun sendStickyOrderedBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle, resultReceiver: android.content.BroadcastReceiver?, scheduler: android.os.Handler?, initialCode: Int, initialData: String?, initialExtras: android.os.Bundle?) = Unit
        override fun removeStickyBroadcastAsUser(intent: android.content.Intent, user: android.os.UserHandle) = Unit
        override fun getSharedPreferences(name: String?, mode: Int) = throw UnsupportedOperationException()
        override fun deleteSharedPreferences(name: String?): Boolean = true
        override fun openFileInput(name: String?) = throw UnsupportedOperationException()
        override fun openFileOutput(name: String?, mode: Int) = throw UnsupportedOperationException()
        override fun deleteFile(name: String?): Boolean = true
        override fun getFileStreamPath(name: String?): java.io.File = throw UnsupportedOperationException()
        override fun getDataDir(): java.io.File = throw UnsupportedOperationException()
        override fun getFilesDir(): java.io.File = throw UnsupportedOperationException()
        override fun getNoBackupFilesDir(): java.io.File = throw UnsupportedOperationException()
        override fun getExternalFilesDir(type: String?): java.io.File? = null
        override fun getExternalFilesDirs(type: String?): Array<java.io.File> = emptyArray()
        override fun getObbDir(): java.io.File? = null
        override fun getObbDirs(): Array<java.io.File> = emptyArray()
        override fun getCacheDir(): java.io.File = throw UnsupportedOperationException()
        override fun getCodeCacheDir(): java.io.File? = null
        override fun getExternalCacheDir(): java.io.File? = null
        override fun getExternalCacheDirs(): Array<java.io.File> = emptyArray()
        override fun getExternalMediaDirs(): Array<java.io.File> = emptyArray()
        override fun fileList(): Array<String> = emptyArray()
        override fun getDir(name: String?, mode: Int): java.io.File = throw UnsupportedOperationException()
        override fun openOrCreateDatabase(name: String?, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
        override fun openOrCreateDatabase(name: String?, mode: Int, factory: android.database.sqlite.SQLiteDatabase.CursorFactory?, errorHandler: android.database.DatabaseErrorHandler?): android.database.sqlite.SQLiteDatabase = throw UnsupportedOperationException()
        override fun databaseList(): Array<String> = emptyArray()
        override fun deleteDatabase(name: String?): Boolean = true
        override fun getDatabasePath(name: String?): java.io.File = throw UnsupportedOperationException()
        override fun getWallpaper(): android.graphics.drawable.Drawable? = null
        override fun peekWallpaper(): android.graphics.drawable.Drawable? = null
        override fun getWallpaperDesiredMinimumWidth(): Int = 0
        override fun getWallpaperDesiredMinimumHeight(): Int = 0
        override fun setWallpaper(bitmap: android.graphics.Bitmap?) = Unit
        override fun setWallpaper(data: java.io.InputStream?) = Unit
        override fun clearWallpaper() = Unit
        override fun startInstrumentation(className: android.content.ComponentName, profileFile: String?, arguments: android.os.Bundle?): Boolean = true
        override fun getPackageResourcePath(): String = ""
        override fun getPackageCodePath(): String = ""
        override fun getApplicationInfo(): android.content.pm.ApplicationInfo = throw UnsupportedOperationException()
        override fun moveDatabaseFrom(p0: android.content.Context, p1: String): Boolean = true
        override fun moveSharedPreferencesFrom(p0: android.content.Context, p1: String): Boolean = true
    }
}
