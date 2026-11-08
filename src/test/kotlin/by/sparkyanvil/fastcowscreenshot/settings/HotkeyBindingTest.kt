package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HotkeyBindingTest {
    @Test
    fun `parses default binding`() {
        assertEquals(HotkeyBinding(true, false, true, 0x31), HotkeyBinding.parse("Ctrl+Shift+1"))
    }

    @Test
    fun `parse is case and space insensitive`() {
        assertEquals(HotkeyBinding(true, false, true, 0x74), HotkeyBinding.parse(" ctrl + shift + f5 "))
    }

    @Test
    fun `parse rejects invalid`() {
        listOf("Shift+1", "Ctrl+Shift", "Ctrl+Ctrl+1", "Ctrl+Enter", "").forEach {
            assertNull(HotkeyBinding.parse(it), it)
        }
    }

    @Test
    fun `format round trip`() {
        val binding = HotkeyBinding(true, true, true, 0x41)
        assertEquals("Ctrl+Alt+Shift+A", binding.format())
        assertEquals(binding, HotkeyBinding.parse(binding.format()))
    }

    @Test
    fun `win modifiers include norepeat`() {
        assertEquals(0x4006, HotkeyBinding.parse("Ctrl+Shift+1")?.winModifiers)
    }

    @Test
    fun `constructor rejects binding without ctrl or alt`() {
        assertFailsWith<IllegalArgumentException> { HotkeyBinding(false, false, true, 0x31) }
    }

    @Test
    fun `defaults per action`() {
        assertEquals("Ctrl+Shift+3", HotkeyBinding.defaultFor(HotkeyAction.REGION).format())
    }

    @Test
    fun `multi region default is ctrl shift 4`() {
        assertEquals("Ctrl+Shift+4", HotkeyBinding.defaultFor(HotkeyAction.MULTI_REGION).format())
        assertEquals(
            "Ctrl+Shift+4",
            AppSettings(defaultDirectory = "C:\\s").hotkeyBinding(HotkeyAction.MULTI_REGION).format(),
        )
    }

    @Test
    fun `hotkey settings with and get multi region`() {
        val s = HotkeySettings().with(HotkeyAction.MULTI_REGION, "Ctrl+Alt+M")
        assertEquals("Ctrl+Alt+M", s.get(HotkeyAction.MULTI_REGION))
        assertEquals(HotkeySettings().region, s.region)
    }
}
