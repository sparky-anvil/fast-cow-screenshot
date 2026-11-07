package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StitchLayoutTest {
    private fun r(l: Int, t: Int, w: Int, h: Int) = PixelRect(l, t, w, h)

    private fun pos(layout: StitchLayout, rect: PixelRect): Pair<Int, Int> {
        val p = layout.placements.first { it.source == rect }
        return Pair(p.targetLeft, p.targetTop)
    }

    @Test
    fun `single region is placed at origin`() {
        val a = r(30, 40, 50, 60)
        val l = StitchLayout.compute(listOf(a))
        assertEquals(50, l.width)
        assertEquals(60, l.height)
        assertEquals(0 to 0, pos(l, a))
    }

    @Test
    fun `touching regions stay adjacent`() {
        val a = r(0, 0, 100, 100)
        val b = r(100, 0, 100, 100)
        val l = StitchLayout.compute(listOf(a, b))
        assertEquals(200, l.width)
        assertEquals(100, l.height)
        assertEquals(0 to 0, pos(l, a))
        assertEquals(100 to 0, pos(l, b))
    }

    @Test
    fun `diagonal regions collapse gaps`() {
        val a = r(0, 0, 100, 50)
        val b = r(500, 300, 200, 100)
        val l = StitchLayout.compute(listOf(a, b))
        assertEquals(300, l.width)
        assertEquals(150, l.height)
        assertEquals(0 to 0, pos(l, a))
        assertEquals(100 to 50, pos(l, b))
    }

    @Test
    fun `regions in a row keep vertical offset`() {
        val a = r(0, 0, 100, 100)
        val b = r(300, 20, 100, 40)
        val l = StitchLayout.compute(listOf(a, b))
        assertEquals(200, l.width)
        assertEquals(100, l.height)
        assertEquals(0 to 0, pos(l, a))
        assertEquals(100 to 20, pos(l, b))
    }

    @Test
    fun `regions in a column keep horizontal offset`() {
        val a = r(0, 0, 100, 100)
        val b = r(30, 500, 40, 50)
        val l = StitchLayout.compute(listOf(a, b))
        assertEquals(100, l.width)
        assertEquals(150, l.height)
        assertEquals(0 to 0, pos(l, a))
        assertEquals(30 to 100, pos(l, b))
    }

    @Test
    fun `negative coordinates are supported`() {
        val a = r(-1920, 100, 100, 50)
        val b = r(0, 0, 200, 100)
        val l = StitchLayout.compute(listOf(a, b))
        assertEquals(300, l.width)
        assertEquals(150, l.height)
        assertEquals(0 to 100, pos(l, a))
        assertEquals(100 to 0, pos(l, b))
        assertEquals(r(0, 0, 200, 100), l.placements[0].source)
    }

    @Test
    fun `overlapping projections are not removed`() {
        val a = r(0, 0, 300, 10)
        val b = r(100, 50, 10, 10)
        val c = r(250, 100, 10, 10)
        val l = StitchLayout.compute(listOf(a, b, c))
        assertEquals(300, l.width)
        assertEquals(30, l.height)
        assertEquals(0 to 0, pos(l, a))
        assertEquals(100 to 10, pos(l, b))
        assertEquals(250 to 20, pos(l, c))
    }

    private fun grid() = (0..9).map { i -> r(i * 200, (i % 2) * 500, 50, 40) }

    @Test
    fun `ten regions in a grid`() {
        val regions = grid()
        val l = StitchLayout.compute(regions)
        assertEquals(500, l.width)
        assertEquals(80, l.height)
        regions.forEachIndexed { i, rect ->
            assertEquals(i * 50 to (i % 2) * 40, pos(l, rect))
        }
    }

    @Test
    fun `result does not depend on input order`() {
        val regions = grid()
        val expected = StitchLayout.compute(regions)
        assertEquals(expected, StitchLayout.compute(regions.reversed()))
        assertEquals(expected, StitchLayout.compute(regions.shuffled(Random(42))))
    }

    @Test
    fun `order is preserved left to right`() {
        val a = r(0, 0, 10, 10)
        val b = r(1000, 5, 10, 10)
        val c = r(50, 500, 10, 10)
        val l = StitchLayout.compute(listOf(a, b, c))
        assertEquals(0, pos(l, a).first)
        assertEquals(20, pos(l, b).first)
        assertEquals(10, pos(l, c).first)
        assertTrue(pos(l, a).first < pos(l, c).first && pos(l, c).first < pos(l, b).first)
    }

    @Test
    fun `empty list is rejected`() {
        assertFailsWith<IllegalArgumentException> { StitchLayout.compute(emptyList()) }
    }

    @Test
    fun `empty region is rejected`() {
        assertFailsWith<IllegalArgumentException> { StitchLayout.compute(listOf(r(0, 0, 0, 10))) }
    }

    @Test
    fun `intersecting regions are rejected`() {
        assertFailsWith<IllegalArgumentException> {
            StitchLayout.compute(listOf(r(0, 0, 10, 10), r(9, 9, 10, 10)))
        }
    }
}
