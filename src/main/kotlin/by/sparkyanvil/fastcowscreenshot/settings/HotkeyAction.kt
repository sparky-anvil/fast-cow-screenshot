package by.sparkyanvil.fastcowscreenshot.settings

import by.sparkyanvil.fastcowscreenshot.domain.CaptureMode

/** Действие горячей клавиши. displayName — текст для UI. */
internal enum class HotkeyAction(val displayName: String, val mode: CaptureMode) {
    MONITOR_UNDER_CURSOR("Монитор под курсором", CaptureMode.MONITOR_UNDER_CURSOR),
    ALL_MONITORS("Все мониторы", CaptureMode.ALL_MONITORS),
    REGION("Область", CaptureMode.REGION),
}
