package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SelectionStateTest {
    @Test
    fun `no rect before press`() {
        val s = SelectionState()
        assertNull(s.rect)
        assertNull(s.sizeLabel())
    }

    @Test
    fun `drag before press ignored`() {
        val s = SelectionState()
        s.drag(PixelPoint(5, 5))
        assertNull(s.rect)
    }

    @Test
    fun `reverse drag across monitors`() {
        val s = SelectionState()
        s.press(PixelPoint(2100, 150))
        s.drag(PixelPoint(1800, 100))
        assertEquals(PixelRect(1800, 100, 301, 51), s.rect)
        assertEquals("301\u00D751", s.sizeLabel())
    }

    @Test
    fun `release returns rect and resets`() {
        val s = SelectionState()
        s.press(PixelPoint(0, 0))
        assertEquals(PixelRect(0, 0, 10, 5), s.release(PixelPoint(9, 4)))
        assertNull(s.rect)
    }

    @Test
    fun `release without press is null`() {
        assertNull(SelectionState().release(PixelPoint(1, 1)))
    }
}