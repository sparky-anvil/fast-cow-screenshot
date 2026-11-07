package by.sparkyanvil.fastcowscreenshot.settings

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class OverlayKeymapTest {
    private val keymap = MultiRegionSettings().keymap()

    @Test
    fun `default keymap maps keys to actions`() {
        assertEquals(OverlayAction.CONFIRM, keymap.actionFor(false, false, false, 10, false))
        assertEquals(OverlayAction.CONFIRM, keymap.actionFor(false, false, false, 10, true))
        assertEquals(OverlayAction.REMOVE_LAST, keymap.actionFor(false, false, false, 8, false))
        assertEquals(OverlayAction.REMOVE_LAST, keymap.actionFor(true, false, false, 0x5A, false))
        assertEquals(OverlayAction.CANCEL, keymap.actionFor(false, false, false, 27, false))
    }

    @Test
    fun `unknown key returns null`() {
        assertNull(keymap.actionFor(false, false, false, 0x41, false))
        assertNull(keymap.actionFor(false, false, false, 0x5A, false))
    }

    @Test
    fun `first action wins on duplicate`() {
        val esc = OverlayKey(false, false, false, 27)
        val map = OverlayKeymap(listOf(esc), emptyList(), listOf(esc))
        assertEquals(OverlayAction.CONFIRM, map.actionFor(false, false, false, 27, false))
    }

    @Test
    fun `primary name uses first key or dash`() {
        assertEquals("Enter", keymap.primaryName(OverlayAction.CONFIRM))
        val empty = OverlayKeymap(emptyList(), emptyList(), emptyList())
        assertEquals("\u2014", empty.primaryName(OverlayAction.CONFIRM))
    }
}