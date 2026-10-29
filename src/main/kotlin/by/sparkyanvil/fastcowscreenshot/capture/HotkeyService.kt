package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding

/** Глобальные горячие клавиши. Потокобезопасен. */
internal interface HotkeyService {
    /** Устанавливает обработчик. onHotkey вызывается на потоке fcs-hotkeys и должен быстро возвращать управление. */
    fun start(onHotkey: (HotkeyAction) -> Unit)

    /**
     * Регистрирует binding для action, заменяя прежнюю регистрацию этого action.
     * @return true — зарегистрировано; false — занято (прежняя регистрация action, если была, восстановлена).
     */
    fun register(action: HotkeyAction, binding: HotkeyBinding): Boolean

    /** Снимает все регистрации. */
    fun unregisterAll()
}