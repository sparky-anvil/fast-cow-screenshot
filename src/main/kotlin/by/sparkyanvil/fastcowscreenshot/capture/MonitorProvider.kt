package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.MonitorInfo
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop

/** Мониторы. Вызывать на fcs-capture. */
internal interface MonitorProvider {
    /** Актуальная конфигурация (каждый вызов перечисляет заново — REQ-001.N11). @throws CaptureException */
    fun desktop(): VirtualDesktop

    /** Монитор под курсором по MonitorFromPoint(MONITOR_DEFAULTTOPRIMARY); при любой ошибке — desktop.primary. Не бросает. */
    fun monitorUnderCursor(desktop: VirtualDesktop): MonitorInfo
}