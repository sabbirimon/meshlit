package com.meshlit.ui.modern

import android.content.res.Configuration
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.meshlit.ui.theme.LocalMeshlitThemeConfig
import java.util.Locale

/** Override only string lookup; retain the real Activity context for permissions and services. */
@Composable internal fun workspaceStringResource(@StringRes id: Int, vararg args: Any): String {
    val context = LocalContext.current
    val language = LocalMeshlitThemeConfig.current.uiLanguage
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val resources = remember(context, language, configuration) {
        context.createConfigurationContext(Configuration(configuration).apply {
            setLocale(Locale.forLanguageTag(language.tag))
        }).resources
    }
    return if (args.isEmpty()) resources.getString(id) else resources.getString(id, *args)
}
