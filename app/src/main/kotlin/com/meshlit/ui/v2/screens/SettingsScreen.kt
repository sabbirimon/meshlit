package com.meshlit.ui.v2.screens
import androidx.compose.runtime.Composable
import com.meshlit.ui.screens.settings.SettingsCategory
@Composable fun SettingsScreen(onOpenCategory:(SettingsCategory)->Unit={},onBack:()->Unit={}) {
    com.meshlit.ui.modern.ModernSettingsScreen(onExit=onBack)
}
@Composable fun SettingsCategoryScreen(category:SettingsCategory,onBack:()->Unit={}) {
    com.meshlit.ui.screens.settings.CategoryScreen(category,onBack)
}
