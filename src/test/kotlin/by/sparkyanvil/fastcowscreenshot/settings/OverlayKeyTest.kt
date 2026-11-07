package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OverlayKeyTest {
    private fun k(code: Int, numpad: Boolean = false) = OverlayKey(false, false, false, code, numpad)

    @Test
    fun `formats modifiers in fixed order`() {
        assertEquals("Ctrl+Alt+Shift+Z", OverlayKey(true, true, true, 0x5A).format())
    }

    @Test
    fun `formats special keys`() {
        assertEquals("Enter", k(10).format())
        assertEquals("NumPad Enter", k(10, true).format())
        assertEquals("Backspace", k(8).format())
        assertEquals("Esc", k(27).format())
        assertEquals("Space", k(32).format())
        assertEquals("Delete", k(127).format())
        assertEquals("F1", k(0x70).format())
        assertEquals("F12", k(0x7B).format())
        assertEquals("0", k(0x30).format())
    }

    @Test
    fun `parses default keys`() {
        assertEquals(k(10), OverlayKey.parse("Enter"))
        assertEquals(k(10, true), OverlayKey.parse("NumPad Enter"))
        assertEquals(k(8), OverlayKey.parse("Backspace"))
        assertEquals(OverlayKey(true, false, false, 0x5A), OverlayKey.parse("Ctrl+Z"))
        assertEquals(k(27), OverlayKey.parse("Esc"))
    }

    @Test
    fun `parse is case insensitive`() {
        assertEquals(OverlayKey(true, false, true, 10, true), OverlayKey.parse("ctrl+shift+numpad enter"))
    }

    @Test
    fun `parse and format round trip`() {
        val codes = (0x30..0x39) + (0x41..0x5A) + (0x70..0x7B) + listOf(8, 10, 27, 32, 127)
        for (code in codes) {
            for (mask in 0..7) {
                val key = OverlayKey(mask and 1 != 0, mask and 2 != 0, mask and 4 != 0, code)
                assertEquals(key, OverlayKey.parse(key.format()))
            }
        }
        val np = OverlayKey(true, true, true, 10, true)
        assertEquals(np, OverlayKey.parse(np.format()))
    }

    @Test
    fun `parse rejects invalid strings`() {
        for (s in listOf("", "  ", "Ctrl+", "Ctrl+Ctrl+Z", "Win+Z", "Tab")) {
            assertNull(OverlayKey.parse(s), s)
        }
    }

    @Test
    fun `parse trims spaces around parts`() {
        assertEquals(OverlayKey(true, false, false, 0x5A), OverlayKey.parse(" Ctrl + Z "))
    }

    @Test
    fun `unsupported key code throws`() {
        assertFailsWith<IllegalArgumentException> { OverlayKey(false, false, false, 9) }
    }

    @Test
    fun `numpad only for enter`() {
        assertFailsWith<IllegalArgumentException> { OverlayKey(false, false, false, 0x41, true) }
    }

    @Test
    fun `matches requires exact modifiers`() {
        val key = OverlayKey(true, false, false, 0x5A)
        assertTrue(key.matches(true, false, false, 0x5A, false))
        assertFalse(key.matches(true, false, true, 0x5A, false))
    }

    @Test
    fun `enter does not match numpad enter`() {
        assertFalse(k(10).matches(false, false, false, 10, true))
    }

    @Test
    fun `matches unsupported code returns false`() {
        assertFalse(k(10).matches(false, false, false, 9, false))
    }
}