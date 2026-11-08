package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.OverlayKey

/** Преобразование нажатия AWT в OverlayKey. Без состояния. */
internal object OverlayKeyRecorder {
    /**
     * Порядок: HotkeyRecorder.isModifierKey(awtKeyCode) → Wait; !OverlayKey.isSupportedKey(awtKeyCode) → Invalid;
     * иначе Apply(OverlayKey(ctrl, alt, shift, awtKeyCode, numpad && awtKeyCode == OverlayKey.VK_ENTER)). Не бросает.
     */
    fun onKeyDown(ctrl: Boolean, alt: Boolean, shift: Boolean, awtKeyCode: Int, numpad: Boolean): OverlayRecordingStep {
        if (HotkeyRecorder.isModifierKey(awtKeyCode)) return OverlayRecordingStep.Wait
        if (!OverlayKey.isSupportedKey(awtKeyCode)) return OverlayRecordingStep.Invalid
        return OverlayRecordingStep.Apply(
            OverlayKey(ctrl, alt, shift, awtKeyCode, numpad && awtKeyCode == OverlayKey.VK_ENTER),
        )
    }
}