package com.meshlit.ui.v2.screens

import com.meshlit.ui.screens.settings.CategoryScreen
import com.meshlit.ui.screens.settings.SettingsCategory
import com.meshlit.ui.screens.settings.SettingsScreen as V1SettingsScreen
import com.meshlit.ui.v2.components.MeshlitDeepLinkWrap

/**
 * v2 Settings wrapper.
 *
 * Sits the v1 [V1SettingsScreen] under a [MeshlitDeepLinkWrap] so the
 * chrome matches the rest of the v2 build (lead bar + "Settings"
 * headline), while the search bar + category list underneath comes
 * from the v1 implementation untouched.
 *
 * The wrapper closes over [onOpenCategory] + [onBack] callbacks so
 * the v2 nav graph owns the back-stack. Tapping a category pushes
 * `settings/category/${name}`; the back arrow inside the category
 * pops one route. Without these callbacks, tapping a category is
 * a no-op (the bug this commit fixes).
 *
 * State (loading indicator, error banner) and the search bar are
 * owned by the v1 screen. The wrapper does not reimplement them.
 */
@androidx.compose.runtime.Composable
fun SettingsScreen(
    onOpenCategory: (SettingsCategory) -> Unit = {},
    onBack: () -> Unit = {},
) {
    MeshlitDeepLinkWrap(
        headline = "Settings",
        subtitle = "Search and tune every surface",
    ) {
        V1SettingsScreen(
            onOpenCategory = onOpenCategory,
            // v2 chrome already owns the drawer + lead bar;
            // skip v1's MeshlitHeader so they don't double-stack.
            onOpenDrawer = {},
            omitHeader = true,
        )
    }
}

/**
 * v2 Settings category screen wrapper. Renders the v1
 * [CategoryScreen] under the same [MeshlitDeepLinkWrap] so the
 * chrome matches `SettingsScreen`. The back arrow pushes the v2
 * back stack; the Advanced / Simple toggle inside the v1 screen
 * keeps working because the wrapper never touches its state.
 */
@androidx.compose.runtime.Composable
fun SettingsCategoryScreen(
    category: SettingsCategory,
    onBack: () -> Unit = {},
) {
    MeshlitDeepLinkWrap(
        headline = category.displayName,
        subtitle = category.subtitle,
    ) {
        CategoryScreen(
            category = category,
            onBack = onBack,
            // v2 chrome already owns the lead bar; skip v1's
            // TopAppBar so the two headers don't stack.
            omitHeader = true,
        )
    }
}
