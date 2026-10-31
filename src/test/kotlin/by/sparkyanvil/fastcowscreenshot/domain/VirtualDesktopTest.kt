package by.sparkyanvil.fastcowscreenshot.domain

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VirtualDesktopTest {
    private val a = MonitorInfo("\\\\.\\DISPLAY1", PixelRect(0, 0, 1920, 1080), PixelRect(0, 0, 1920, 1040), true)
    private val b = MonitorInfo(
        "\\\\.\\DISPLAY2",
        PixelRect(-2560, 0, 2560, 1440),
        PixelRect(-2560, 0, 2560, 1400),
        false,
    )

    @Test
    fun `bounds is union of monitors`() {
        assertEquals(PixelRect(-2560, 0, 4480, 1440), VirtualDesktop(listOf(a, b)).bounds)
    }

    @Test
    fun `primary is monitor flagged primary`() {
        assertEquals(a, VirtualDesktop(listOf(b, a)).primary)
    }

    @Test
    fun `primary falls back to first`() {
        assertEquals(b, VirtualDesktop(listOf(b, a.copy(isPrimary = false))).primary)
    }

    @Test
    fun `monitorAt finds monitor with negative coordinates`() {
        assertEquals(b, VirtualDesktop(listOf(a, b)).monitorAt(PixelPoint(-1, 0)))
    }

    @Test
    fun `monitorAt returns null in gap`() {
        assertNull(VirtualDesktop(listOf(a, b)).monitorAt(PixelPoint(100, 1200)))
    }

    @Test
    fun `portrait monitor bounds`() {
        val c = MonitorInfo(
            "\\\\.\\DISPLAY3",
            PixelRect(1920, -500, 1080, 1920),
            PixelRect(1920, -500, 1080, 1880),
            false,
        )
        assertEquals(PixelRect(0, -500, 3000, 1920), VirtualDesktop(listOf(a, c)).bounds)
    }
}