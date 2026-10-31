package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PixelRectTest {
    @Test
    fun `right and bottom are exclusive`() {
        val r = PixelRect(10, 20, 30, 40)
        assertEquals(40, r.right)
        assertEquals(60, r.bottom)
        assertTrue(r.contains(PixelPoint(39, 59)))
        assertFalse(r.contains(PixelPoint(40, 59)))
    }

    @Test
    fun `intersect returns overlap`() {
        assertEquals(
            PixelRect(50, 60, 50, 40),
            PixelRect(0, 0, 100, 100).intersect(PixelRect(50, 60, 100, 100)),
        )
    }

    @Test
    fun `intersect returns null for touching rects`() {
        assertNull(PixelRect(0, 0, 10, 10).intersect(PixelRect(10, 0, 10, 10)))
    }

    @Test
    fun `union covers negative coordinates`() {
        assertEquals(
            PixelRect(-2560, 0, 4480, 1440),
            PixelRect.union(listOf(PixelRect(0, 0, 1920, 1080), PixelRect(-2560, 0, 2560, 1440))),
        )
    }

    @Test
    fun `union of empty list fails`() {
        assertFailsWith<IllegalArgumentException> { PixelRect.union(emptyList()) }
    }

    @Test
    fun `negative width fails`() {
        assertFailsWith<IllegalArgumentException> { PixelRect(0, 0, -1, 5) }
    }
}