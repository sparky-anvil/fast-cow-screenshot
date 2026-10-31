package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.MonitorInfo
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FrameAssemblerTest {
    private val a = PixelRect(0, 0, 4, 2)
    private val b = PixelRect(-6, 0, 6, 3)
    private val desktop = VirtualDesktop(
        listOf(MonitorInfo("A", a, a, true), MonitorInfo("B", b, b, false)),
    )
    private val area = PixelRect(-6, 0, 10, 3)

    @Test
    fun `covered rects are clipped monitor bounds`() {
        assertEquals(listOf(PixelRect(0, 0, 4, 2), PixelRect(-6, 0, 6, 3)), FrameAssembler.coveredRects(area, desktop))
    }

    @Test
    fun `gaps are filled with gray`() {
        val covered = FrameAssembler.coveredRects(area, desktop)
        val image = FrameAssembler.assemble(area, covered) { s, d, c -> d.fill(0x112233, s, s + c) }
        assertEquals(10, image.width)
        assertEquals(3, image.height)
        assertEquals(0x808080, image.getRGB(6, 2) and 0xFFFFFF)
        assertEquals(0x112233, image.getRGB(0, 2) and 0xFFFFFF)
        assertEquals(0x112233, image.getRGB(6, 0) and 0xFFFFFF)
    }

    @Test
    fun `copy receives row start indexes`() {
        val starts = mutableListOf<Int>()
        FrameAssembler.assemble(area, FrameAssembler.coveredRects(area, desktop)) { s, _, _ -> starts.add(s) }
        assertEquals(listOf(6, 16, 0, 10, 20), starts)
    }

    @Test
    fun `area outside monitors is all gray`() {
        assertTrue(FrameAssembler.coveredRects(PixelRect(100, 100, 2, 2), desktop).isEmpty())
    }
}