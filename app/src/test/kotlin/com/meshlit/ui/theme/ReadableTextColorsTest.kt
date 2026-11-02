package com.meshlit.ui.theme
import org.junit.Assert.*
import org.junit.Test
class ReadableTextColorsTest {
    @Test fun olderMaterialToolsUseTheSameReadableTheme() {
        for(base in BasePalette.entries) {
            val c=buildColorScheme(MeshlitThemeConfig.Default.copy(basePalette=base))
            val legacy=legacyColorsFor(c)
            assertEquals(c.surface,legacy.surface)
            assertEquals(c.background,legacy.background)
            assertEquals(c.primary,legacy.primary)
            assertEquals(c.onSurface,legacy.onSurface)
            assertEquals(c.onPrimary,legacy.onPrimary)
            assertEquals(c.onSecondary,legacy.onSecondary)
            assertTrue(textContrast(legacy.onSurface,legacy.surface)>=4.5f)
        }
    }
    @Test fun textRemainsReadableAcrossCuratedThemesAndAccents() {
        for(base in BasePalette.entries)for(accent in AccentHue.entries) {
            val c=buildColorScheme(MeshlitThemeConfig.Default.copy(basePalette=base,accentHue=accent))
            for((ink,paper) in listOf(c.onSurface to c.surface,c.onBackground to c.background,c.onPrimary to c.primary,c.onSecondary to c.secondary,c.onTertiary to c.tertiary))
                assertTrue("Readable text for $base/$accent",textContrast(ink,paper)>=4.5f)
        }
    }
    @Test fun customDarkButtonsAndLightSurfacesKeepTheirColorsAndReadableLabels() {
        val palette=CustomPalette.Solid(0xff151515,0xff202020,0xff303030,0xffeeeeee,0xffdddddd)
        val c=buildColorScheme(MeshlitThemeConfig.Default.copy(customPalette=palette))
        assertEquals(androidx.compose.ui.graphics.Color(palette.primary),c.primary)
        assertTrue(textContrast(c.onPrimary,c.primary)>=4.5f)
        assertTrue(textContrast(c.onSecondary,c.secondary)>=4.5f)
        assertTrue(textContrast(c.onSurface,c.surface)>=4.5f)
        assertTrue(textContrast(c.onSurfaceVariant,c.surfaceVariant)>=4.5f)
    }
}
