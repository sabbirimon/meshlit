package com.meshlit.settings
import android.app.Application
import com.meshlit.workspace.WorkspaceLanguage
import com.meshlit.ui.theme.AppearancePreset
import com.meshlit.ui.theme.AccentHue
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application=Application::class,sdk=[34])
class WorkspaceLanguageTest {
    @Test fun appearanceRetainsLanguageAndLanguageDoesNotEditInferenceSettings() = runBlocking {
        val settings=SettingsRepository(RuntimeEnvironment.getApplication())
        settings.setUiLanguage(WorkspaceLanguage.SIMPLIFIED_CHINESE)
        val before=settings.customModelPathFlow.first()
        settings.applyAppearance(AppearancePreset.MONOCHROME)
        assertEquals(WorkspaceLanguage.SIMPLIFIED_CHINESE,settings.flow.first().uiLanguage)
        assertEquals(AccentHue.PEARL,settings.flow.first().accentHue)
        assertEquals(before,settings.customModelPathFlow.first())
        // A fresh repository instance reads the same persisted settings, not a UI-only flag.
        assertEquals(WorkspaceLanguage.SIMPLIFIED_CHINESE,SettingsRepository(RuntimeEnvironment.getApplication()).flow.first().uiLanguage)
        settings.setUiLanguage(WorkspaceLanguage.ENGLISH)
    }
}
