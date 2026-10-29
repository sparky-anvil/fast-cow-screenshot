package by.sparkyanvil.fastcowscreenshot.platform.windows

import org.junit.jupiter.api.Test
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DibEncoderTest {
    private fun sample(): BufferedImage {
        val image = BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB)
        image.setRGB(0, 0, 0xFF0000)
        image.setRGB(1, 0, 0x00FF00)
        image.setRGB(0, 1, 0x0000FF)
        image.setRGB(1, 1, 0xFFFFFF)
        return image
    }

    @Test
    fun `stride is padded to 4 bytes`() {
        assertEquals(4, DibEncoder.stride(1))
        assertEquals(8, DibEncoder.stride(2))
        assertEquals(12, DibEncoder.stride(4))
    }

    @Test
    fun `header fields`() {
        val bytes = DibEncoder.encode(sample())
        assertEquals(56, bytes.size)
        assertEquals(56L, DibEncoder.totalSize(2, 2))
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(40, buf.getInt(0))
        assertEquals(2, buf.getInt(4))
        assertEquals(2, buf.getInt(8))
        assertEquals(1.toShort(), buf.getShort(12))
        assertEquals(24.toShort(), buf.getShort(14))
        assertEquals(16, buf.getInt(20))
    }

    @Test
    fun `pixels are bgr bottom up`() {
        val bytes = DibEncoder.encode(sample())
        val expected = intArrayOf(
            0xFF, 0x00, 0x00, 0xFF, 0xFF, 0xFF, 0x00, 0x00,
            0x00, 0x00, 0xFF, 0x00, 0xFF, 0x00, 0x00, 0x00,
        ).map { it.toByte() }.toByteArray()
        assertContentEquals(expected, bytes.copyOfRange(40, 56))
    }

    @Test
    fun `alpha byte is ignored`() {
        val image = BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)
        (image.raster.dataBuffer as DataBufferInt).data[0] = 0x7F102030
        val bytes = DibEncoder.encode(image)
        assertContentEquals(
            byteArrayOf(0x30, 0x20, 0x10),
            bytes.copyOfRange(40, 43),
        )
    }

    @Test
    fun `rejects argb image`() {
        assertFailsWith<IllegalArgumentException> {
            DibEncoder.encode(BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB))
        }
    }
}