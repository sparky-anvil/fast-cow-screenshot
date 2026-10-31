package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionGeometryTest {
    @Test
    fun `rect from reverse drag`() {
        assertEquals(
            PixelRect(1700, 300, 201, 201),
            SelectionGeometry.rectFromPoints(PixelPoint(1900, 500), PixelPoint(1700, 300)),
        )
    }

    @Test
    fun `single click is 1x1 and cancelled`() {
        val r = SelectionGeometry.rectFromPoints(PixelPoint(5, 5), PixelPoint(5, 5))
        assertEquals(PixelRect(5, 5, 1, 1), r)
        assertTrue(SelectionGeometry.isCancelled(r))
    }

    @Test
    fun `2x2 is not cancelled`() {
        val r = SelectionGeometry.rectFromPoints(PixelPoint(0, 0), PixelPoint(1, 1))
        assertEquals(PixelRect(0, 0, 2, 2), r)
        assertFalse(SelectionGeometry.isCancelled(r))
    }

    @Test
    fun `1 pixel high is cancelled`() {
        val r = SelectionGeometry.rectFromPoints(PixelPoint(0, 0), PixelPoint(100, 0))
        assertTrue(SelectionGeometry.isCancelled(r))
    }

    @Test
    fun `toFrameRect shifts by negative origin`() {
        assertEquals(
            PixelRect(2460, 10, 50, 20),
            SelectionGeometry.toFrameRect(PixelRect(-100, 10, 50, 20), PixelPoint(-2560, 0)),
        )
    }
}