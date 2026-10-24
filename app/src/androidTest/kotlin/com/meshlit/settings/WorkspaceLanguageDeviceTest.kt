package com.meshlit.settings

import android.content.res.Configuration
import androidx.test.platform.app.InstrumentationRegistry
import com.meshlit.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** Actual installed resources/context lookup. Compiled here; requires a device to execute. */
class WorkspaceLanguageDeviceTest {
    @Test fun resourcesResolveBothLanguagesWithoutChangingActivityContext() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        val chinese=context.createConfigurationContext(Configuration(context.resources.configuration).apply {setLocale(Locale.forLanguageTag("zh-Hans"))})
        assertEquals("发送",chinese.getString(R.string.modern_send))
        val english=context.createConfigurationContext(Configuration(context.resources.configuration).apply {setLocale(Locale.forLanguageTag("en"))})
        assertEquals("Send",english.getString(R.string.modern_send))
    }
}
