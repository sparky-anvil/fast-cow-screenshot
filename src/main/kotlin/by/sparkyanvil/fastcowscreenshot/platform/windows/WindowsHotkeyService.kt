package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.capture.HotkeyService
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import com.sun.jna.Native
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WindowsHotkeyService::class.java)

/** Реализация HotkeyService на RegisterHotKey. */
internal class WindowsHotkeyService(
    private val loop: NativeMessageLoop,
    private val user32: User32Api = User32Api.INSTANCE,
) : HotkeyService {
    /** Читается и меняется только внутри loop.invoke. */
    private val registered = mutableMapOf<HotkeyAction, HotkeyBinding>()

    override fun start(onHotkey: (HotkeyAction) -> Unit) {
        loop.onHotkey = { id -> HotkeyAction.entries.getOrNull(id - 1)?.let(onHotkey) }
    }

    override fun register(action: HotkeyAction, binding: HotkeyBinding): Boolean = loop.invoke {
        val id = action.ordinal + 1
        val previous = registered[action]
        if (previous != null) {
            user32.UnregisterHotKey(null, id)
            registered.remove(action)
        }
        if (user32.RegisterHotKey(null, id, binding.winModifiers, binding.keyCode)) {
            registered[action] = binding
            log.info("Hotkey registered: {} = {}", action, binding.format())
            true
        } else {
            log.warn(
                "RegisterHotKey failed for {}: {}, GetLastError={}",
                action, binding.format(), Native.getLastError(),
            )
            if (previous != null && user32.RegisterHotKey(null, id, previous.winModifiers, previous.keyCode)) {
                registered[action] = previous
            }
            false
        }
    }

    override fun unregisterAll() {
        loop.invoke {
            registered.keys.forEach { user32.UnregisterHotKey(null, it.ordinal + 1) }
            registered.clear()
        }
    }
}