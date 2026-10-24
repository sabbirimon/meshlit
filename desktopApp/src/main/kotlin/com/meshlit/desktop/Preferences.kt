package com.meshlit.desktop
import com.meshlit.workspace.*
import java.util.prefs.Preferences

/** Appearance only. Prompts, conversation history and bearer credentials are session-only. */
class DesktopPreferences(private val store: Preferences = Preferences.userRoot().node("com/meshlit/desktop-preview")) {
    fun language() = WorkspaceLanguage.fromTag(store.get("language", "en"))
    fun look() = WorkspaceLook.entries.firstOrNull { it.name == store.get("look", "STUDIO") } ?: WorkspaceLook.STUDIO
    fun scale() = store.getFloat("fontScale", 1f).takeIf { it.isFinite() }?.coerceIn(.85f, 1.5f) ?: 1f
    fun advanced() = store.getBoolean("advanced", false)
    fun saveAdvanced(value: Boolean) { store.putBoolean("advanced", value); store.flush() }
    fun save(language: WorkspaceLanguage, look: WorkspaceLook, scale: Float) {
        require(scale.isFinite()); store.put("language", language.tag); store.put("look", look.name)
        store.putFloat("fontScale", scale.coerceIn(.85f, 1.5f)); store.flush()
    }
}
