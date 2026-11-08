package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.Serializable

/** Горячие клавиши по режимам в строковом виде ("Ctrl+Shift+1"). */
@Serializable
internal data class HotkeySettings(
    val monitorUnderCursor: String = "Ctrl+Shift+1",
    val allMonitors: String = "Ctrl+Shift+2",
    val region: String = "Ctrl+Shift+3",
    val multiRegion: String = "Ctrl+Shift+4",
) {
    /** Строка клавиши для действия. */
    fun get(action: HotkeyAction): String = when (action) {
        HotkeyAction.MONITOR_UNDER_CURSOR -> monitorUnderCursor
        HotkeyAction.ALL_MONITORS -> allMonitors
        HotkeyAction.REGION -> region
        HotkeyAction.MULTI_REGION -> multiRegion
    }

    /** Копия с заменённой строкой клавиши для действия. */
    fun with(action: HotkeyAction, text: String): HotkeySettings = when (action) {
        HotkeyAction.MONITOR_UNDER_CURSOR -> copy(monitorUnderCursor = text)
        HotkeyAction.ALL_MONITORS -> copy(allMonitors = text)
        HotkeyAction.REGION -> copy(region = text)
        HotkeyAction.MULTI_REGION -> copy(multiRegion = text)
    }
}
