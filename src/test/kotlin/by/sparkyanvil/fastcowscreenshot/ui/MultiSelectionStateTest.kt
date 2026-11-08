package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MultiSelectionStateTest {
    private fun p(x: Int, y: Int) = PixelPoint(x, y)
    private fun r(x: Int, y: Int, w: Int, h: Int) = PixelRect(x, y, w, h)

    private fun add(s: MultiSelectionState, x1: Int, y1: Int, x2: Int, y2: Int): ReleaseResult {
        s.press(p(x1, y1))
        s.drag(p(x2, y2))
        return s.release(p(x2, y2))
    }

    private fun three(): MultiSelectionState {
        val s = MultiSelectionState(10)
        add(s, 0, 0, 9, 9)
        add(s, 20, 0, 29, 9)
        add(s, 40, 0, 49, 9)
        return s
    }

    private fun withFirst(): MultiSelectionState {
        val s = MultiSelectionState(10)
        add(s, 0, 0, 99, 99)
        return s
    }

    @Test
    fun `adds regions and keeps order`() {
        val s = three()
        assertEquals(listOf(r(0, 0, 10, 10), r(20, 0, 10, 10), r(40, 0, 10, 10)), s.regions)
        assertEquals("3 / 10", s.counterLabel())
    }

    @Test
    fun `touching region is added`() {
        val s = withFirst()
        assertEquals(ReleaseResult.ADDED, add(s, 100, 0, 199, 99))
        assertEquals(2, s.regions.size)
    }

    @Test
    fun `intersecting region is rejected`() {
        val s = withFirst()
        s.press(p(150, 150))
        assertEquals(ReleaseResult.INTERSECTS, s.release(p(99, 99)))
        assertEquals(listOf(r(0, 0, 100, 100)), s.regions)
    }

    @Test
    fun `drag intersects flag`() {
        val s = withFirst()
        s.press(p(150, 150))
        s.drag(p(120, 120))
        assertFalse(s.dragIntersects)
        s.drag(p(50, 50))
        assertTrue(s.dragIntersects)
    }

    @Test
    fun `press inside region does not start drag`() {
        val s = withFirst()
        assertEquals(PressResult.INSIDE_REGION, s.press(p(50, 50)))
        assertFalse(s.isDragging)
    }

    @Test
    fun `tiny selection is ignored`() {
        val s = MultiSelectionState(10)
        s.press(p(10, 10))
        assertEquals(ReleaseResult.TOO_SMALL, s.release(p(11, 10)))
        assertEquals(0, s.regions.size)
        assertFalse(s.isDragging)
    }

    @Test
    fun `two by two is accepted`() {
        val s = MultiSelectionState(10)
        s.press(p(10, 10))
        assertEquals(ReleaseResult.ADDED, s.release(p(11, 11)))
        assertEquals(listOf(r(10, 10, 2, 2)), s.regions)
    }

    @Test
    fun `limit blocks new drag and frees after removal`() {
        val s = MultiSelectionState(2)
        add(s, 0, 0, 9, 9)
        add(s, 20, 0, 29, 9)
        assertEquals(PressResult.LIMIT_REACHED, s.press(p(500, 500)))
        assertTrue(s.isFull)
        s.removeLast()
        assertEquals(PressResult.STARTED, s.press(p(500, 500)))
    }

    @Test
    fun `remove last removes newest`() {
        val s = three()
        assertTrue(s.removeLast())
        assertEquals(listOf(r(0, 0, 10, 10), r(20, 0, 10, 10)), s.regions)
    }

    @Test
    fun `remove last on empty does nothing`() {
        assertFalse(MultiSelectionState(10).removeLast())
    }

    @Test
    fun `remove at removes containing region and renumbers`() {
        val s = three()
        assertTrue(s.removeAt(p(5, 5)))
        assertEquals(listOf(r(20, 0, 10, 10), r(40, 0, 10, 10)), s.regions)
    }

    @Test
    fun `remove at outside does nothing`() {
        val s = three()
        assertFalse(s.removeAt(p(15, 5)))
        assertEquals(3, s.regions.size)
    }

    @Test
    fun `remove at during drag does nothing`() {
        val s = MultiSelectionState(10)
        add(s, 0, 0, 9, 9)
        s.press(p(500, 500))
        assertFalse(s.removeAt(p(5, 5)))
        assertEquals(1, s.regions.size)
    }

    @Test
    fun `release without press`() {
        assertEquals(ReleaseResult.NO_DRAG, MultiSelectionState(10).release(p(1, 1)))
    }

    @Test
    fun `size label while dragging`() {
        val s = MultiSelectionState(10)
        s.press(p(0, 0))
        s.drag(p(200, 200))
        assertEquals("201\u00D7201", s.sizeLabel())
        s.release(p(200, 200))
        assertNull(s.sizeLabel())
    }

    @Test
    fun `cancel drag discards selection`() {
        val s = MultiSelectionState(10)
        s.press(p(0, 0))
        s.drag(p(50, 50))
        s.cancelDrag()
        assertNull(s.dragRect)
        assertEquals(ReleaseResult.NO_DRAG, s.release(p(50, 50)))
        assertEquals(0, s.regions.size)
    }

    @Test
    fun `regions list is a copy`() {
        val s = MultiSelectionState(10)
        add(s, 0, 0, 9, 9)
        val copy = s.regions
        add(s, 20, 0, 29, 9)
        assertEquals(1, copy.size)
    }
}