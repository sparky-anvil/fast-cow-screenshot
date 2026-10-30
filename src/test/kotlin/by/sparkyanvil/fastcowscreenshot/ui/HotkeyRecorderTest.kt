package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import org.junit.jupiter.api.Test
import java.awt.event.KeyEvent
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HotkeyRecorderTest {
    @Test
    fun `maps ctrl shift digit`() {
        assertEquals(HotkeyBinding(true, false, true, 0x31), HotkeyRecorder.toBinding(true, false, true, KeyEvent.VK_1))
    }

    @Test
    fun `maps alt f12`() {
        assertEquals(0x7B, HotkeyRecorder.toBinding(false, true, false, KeyEvent.VK_F12)?.keyCode)
    }

    @Test
    fun `rejects shift only`() {
        assertNull(HotkeyRecorder.toBinding(false, false, true, KeyEvent.VK_A))
    }

    @Test
    fun `rejects unsupported key`() {
        assertNull(HotkeyRecorder.toBinding(true, false, false, KeyEvent.VK_ENTER))
    }

    @Test
    fun `detects modifier keys`() {
        assertTrue(HotkeyRecorder.isModifierKey(KeyEvent.VK_SHIFT))
        assertFalse(HotkeyRecorder.isModifierKey(KeyEvent.VK_A))
    }
}
