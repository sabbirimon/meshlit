package com.meshlit.ui.theme

/** Atomic visual presets; font size, accessibility and operation policy survive. */
enum class AppearancePreset(val label:String,val base:BasePalette,val accent:AccentHue,val mode:ThemeMode,val surface:SurfaceStyle=SurfaceStyle.SOLID) {
    STUDIO("Studio",BasePalette.GRAPHITE,AccentHue.EMBER,ThemeMode.DARK),
    MONOCHROME("Monochrome",BasePalette.GRAPHITE,AccentHue.PEARL,ThemeMode.DARK),
    GRAPHITE("Graphite",BasePalette.GRAPHITE,AccentHue.SKY,ThemeMode.DARK),
    AURORA("Aurora",BasePalette.DUSK,AccentHue.SKY,ThemeMode.DARK,SurfaceStyle.GLASS),
    OCEAN("Ocean",BasePalette.OCEAN,AccentHue.TEAL,ThemeMode.DARK),
    PAPER("Paper",BasePalette.PAPER,AccentHue.SKY,ThemeMode.LIGHT),
    COFFEE("Coffee",BasePalette.COFFEE,AccentHue.AMBER,ThemeMode.DARK);

    fun matches(config:MeshlitThemeConfig)=!config.dynamicColors && config.customPalette is CustomPalette.None &&
        config.basePalette==base && config.accentHue==accent && config.themeMode==mode && config.surfaceStyle==surface
}
