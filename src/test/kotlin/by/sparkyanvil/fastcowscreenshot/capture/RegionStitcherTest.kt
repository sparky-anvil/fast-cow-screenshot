package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.StitchLayout
import by.sparkyanvil.fastcowscreenshot.domain.StitchPlacement
import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class RegionStitcherTest {
    private val origin = PixelPoint(-5, 0)

    private fun frame(): BufferedImage {
        val img = BufferedImage(10, 4, BufferedImage.TYPE_INT_RGB)
        for (x in 0 until 10) for (y in 0 until 4) img.setRGB(x, y, x * 16 + y)
        return img
    }

    private fun rgb(img: BufferedImage, x: Int, y: Int) = img.getRGB(x, y) and 0xFFFFFF

    @Test
    fun `copies regions and fills gaps`() {
        val layout = StitchLayout(
            4, 2,
            listOf(
                StitchPlacement(PixelRect(-5, 0, 2, 1), 0, 0),
                StitchPlacement(PixelRect(2, 2, 2, 1), 2, 1),
            ),
        )
        val out = RegionStitcher.stitch(frame(), origin, layout, 0x123456)
        assertEquals(BufferedImage.TYPE_INT_RGB, out.type)
        assertEquals(0, rgb(out, 0, 0))
        assertEquals(16, rgb(out, 1, 0))
        assertEquals(7 * 16 + 2, rgb(out, 2, 1))
        assertEquals(8 * 16 + 2, rgb(out, 3, 1))
        for ((x, y) in listOf(2 to 0, 3 to 0, 0 to 1, 1 to 1)) assertEquals(0x123456, rgb(out, x, y))
    }

    @Test
    fun `single region equals crop`() {
        val layout = StitchLayout.compute(listOf(PixelRect(-3, 1, 4, 2)))
        val out = RegionStitcher.stitch(frame(), origin, layout, 0)
        assertEquals(4, out.width)
        assertEquals(2, out.height)
        for (i in 0 until 4) for (j in 0 until 2) assertEquals((2 + i) * 16 + (1 + j), rgb(out, i, j))
    }

    @Test
    fun `region outside frame throws`() {
        val layout = StitchLayout(2, 1, listOf(StitchPlacement(PixelRect(4, 0, 2, 1), 0, 0)))
        assertFailsWith<IllegalArgumentException> { RegionStitcher.stitch(frame(), origin, layout, 0) }
    }
}