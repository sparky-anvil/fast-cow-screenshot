package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@EnabledOnOs(OS.WINDOWS)
class WindowsHotkeyServiceTest {
    private val f12 = HotkeyBinding(ctrl = true, alt = true, shift = true, keyCode = 0x7B)
    private val f11 = HotkeyBinding(ctrl = true, alt = true, shift = true, keyCode = 0x7A)

    private fun withService(block: (WindowsHotkeyService) -> Unit) {
        val loop = NativeMessageLoop()
        loop.start()
        val service = WindowsHotkeyService(loop)
        try {
            block(service)
        } finally {
            try {
                service.unregisterAll()
            } finally {
                loop.stop()
            }
        }
    }

    @Test
    fun `registers rare binding`() = withService { service ->
        assertTrue(service.register(HotkeyAction.REGION, f12))
    }

    @Test
    fun `same binding for second action fails and keeps first`() = withService { service ->
        assertTrue(service.register(HotkeyAction.REGION, f12))
        assertFalse(service.register(HotkeyAction.ALL_MONITORS, f12))
        assertTrue(service.register(HotkeyAction.REGION, f12))
    }

    @Test
    fun `reassign replaces previous binding`() = withService { service ->
        assertTrue(service.register(HotkeyAction.REGION, f12))
        assertTrue(service.register(HotkeyAction.REGION, f11))
        assertTrue(service.register(HotkeyAction.ALL_MONITORS, f12))
    }
}