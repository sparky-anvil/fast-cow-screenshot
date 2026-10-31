package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.Serializable

/** Настройки приложения, сохраняемые в settings.json. */
@Serializable
internal data class AppSettings(
    val version: Int = CURRENT_VERSION,
    val defaultDirectory: String,
    val lastSaveAsDirectory: String? = null,
    val includeCursor: Boolean = false,
    val autoSaveEnabled: Boolean = true,
    val hotkeys: HotkeySettings = HotkeySettings(),
) {
    /** HotkeyBinding.parse(hotkeys.get(action)) ?: HotkeyBinding.defaultFor(action). */
    fun hotkeyBinding(action: HotkeyAction): HotkeyBinding =
        HotkeyBinding.parse(hotkeys.get(action)) ?: HotkeyBinding.defaultFor(action)

    companion object {
        const val CURRENT_VERSION: Int = 1
    }
}
