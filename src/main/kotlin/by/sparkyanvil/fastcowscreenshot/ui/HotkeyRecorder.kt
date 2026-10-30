package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import java.awt.event.KeyEvent

/** Преобразование нажатия клавиш AWT в HotkeyBinding. Коды AWT для 0–9, A–Z, F1–F12 равны кодам Windows. */
internal object HotkeyRecorder {
    /** Коды клавиш-модификаторов AWT: VK_CONTROL, VK_SHIFT, VK_ALT, VK_WINDOWS, VK_ALT_GRAPH. */
    fun isModifierKey(awtKeyCode: Int): Boolean = when (awtKeyCode) {
        KeyEvent.VK_CONTROL, KeyEvent.VK_SHIFT, KeyEvent.VK_ALT, KeyEvent.VK_WINDOWS, KeyEvent.VK_ALT_GRAPH -> true
        else -> false
    }

    /** HotkeyBinding или null, если нет Ctrl/Alt или клавиша не поддерживается. Не бросает. */
    fun toBinding(ctrl: Boolean, alt: Boolean, shift: Boolean, awtKeyCode: Int): HotkeyBinding? {
        if (!ctrl && !alt) return null
        if (!HotkeyBinding.isSupportedKey(awtKeyCode)) return null
        return HotkeyBinding(ctrl, alt, shift, awtKeyCode)
    }
}
