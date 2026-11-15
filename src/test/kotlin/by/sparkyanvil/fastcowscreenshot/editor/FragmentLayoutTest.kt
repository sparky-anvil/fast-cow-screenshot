package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FragmentLayoutTest {
    private val a = PixelRect(0, 0, 100, 50)
    private val b = PixelRect(100, 50, 200, 100)

    private fun r(l: Int, t: Int, w: Int, h: Int) = PixelRect(l, t, w, h)
    private fun p(x: Int, y: Int) = PixelPoint(x, y)

    @Test
    fun `placed applies positions keeping sizes`() {
        assertEquals(
            listOf(r(1, 2, 10, 20), r(30, 40, 3, 4)),
            FragmentLayout.placed(listOf(r(5, 5, 10, 20), r(0, 0, 3, 4)), listOf(p(1, 2), p(30, 40))),
        )
    }

    @Test
    fun `placed rejects size mismatch`() {
        assertFailsWith<IllegalArgumentException> { FragmentLayout.placed(listOf(a, b), listOf(p(0, 0))) }
    }

    @Test
    fun `normalize shift after dragging left`() {
        assertEquals(p(30, 0), FragmentLayout.normalizeShift(listOf(r(-30, 0, 100, 50), r(0, 50, 200, 100))))
    }

    @Test
    fun `normalize shift is zero for normalized layout`() {
        assertEquals(p(0, 0), FragmentLayout.normalizeShift(listOf(a, b)))
    }

    @Test
    fun `overlap is detected`() {
        assertTrue(FragmentLayout.intersectsAny(listOf(a, b), 1, r(50, 20, 200, 100)))
    }

    @Test
    fun `touching sides is allowed`() {
        assertFalse(FragmentLayout.intersectsAny(listOf(a, b), 1, r(100, 0, 200, 100)))
    }

    @Test
    fun `fragment does not intersect itself`() {
        assertFalse(FragmentLayout.intersectsAny(listOf(a, b), 0, a))
    }

    @Test
    fun `clamp limits width to sum of widths`() {
        val result = FragmentLayout.clampPosition(listOf(a, b), 1, p(1000, 50))
        assertEquals(p(100, 50), result)
        assertTrue(FragmentLayout.withinLimits(listOf(a, r(100, 50, 200, 100))))
    }

    @Test
    fun `clamp limits height to sum of heights`() {
        assertEquals(p(0, 50), FragmentLayout.clampPosition(listOf(a, b), 1, p(0, 5000)))
    }

    @Test
    fun `clamp limits negative direction`() {
        assertEquals(p(-200, -100), FragmentLayout.clampPosition(listOf(a, b), 1, p(-1000, -1000)))
    }

    @Test
    fun `clamp keeps allowed position`() {
        assertEquals(p(0, 50), FragmentLayout.clampPosition(listOf(a, b), 1, p(0, 50)))
    }

    @Test
    fun `clamp single fragment returns candidate`() {
        assertEquals(p(-500, 700), FragmentLayout.clampPosition(listOf(a), 0, p(-500, 700)))
    }

    @Test
    fun `within limits detects too wide layout`() {
        assertFalse(FragmentLayout.withinLimits(listOf(a, r(500, 0, 200, 100))))
    }

    @Test
    fun `stitch removes gaps keeping order`() {
        assertEquals(
            listOf(p(0, 0), p(100, 50)),
            FragmentLayout.stitchPositions(listOf(r(0, 0, 100, 50), r(150, 80, 200, 100))),
        )
    }

    @Test
    fun `stitch without gaps keeps positions`() {
        assertEquals(
            listOf(p(0, 0), p(30, 50)),
            FragmentLayout.stitchPositions(listOf(r(0, 0, 100, 50), r(30, 50, 200, 100))),
        )
    }

    @Test
    fun `fragment at point`() {
        assertEquals(1, FragmentLayout.fragmentAt(listOf(a, b), p(150, 60)))
        assertEquals(0, FragmentLayout.fragmentAt(listOf(a, b), p(99, 49)))
        assertNull(FragmentLayout.fragmentAt(listOf(a, b), p(100, 49)))
    }
}