package by.sparkyanvil.fastcowscreenshot.platform.windows

import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Кодирование в упакованный CF_DIB 24 бит bottom-up. Без состояния. */
internal object DibEncoder {
    const val HEADER_SIZE: Int = 40

    /** ((width * 3 + 3) / 4) * 4. */
    fun stride(width: Int): Int = ((width * 3 + 3) / 4) * 4

    /** HEADER_SIZE + stride(width).toLong() * height. */
    fun totalSize(width: Int, height: Int): Long = HEADER_SIZE + stride(width).toLong() * height

    /**
     * Пишет заголовок (offset 0, длина 40), затем строки y = h-1 … 0 (offset 40 + i*stride, длина stride).
     * Буфер строки переиспользуется — sink должен скопировать данные до возврата.
     * @throws IllegalArgumentException если image.type != TYPE_INT_RGB.
     */
    fun write(image: BufferedImage, sink: (offset: Long, buffer: ByteArray, length: Int) -> Unit) {
        require(image.type == BufferedImage.TYPE_INT_RGB) { "TYPE_INT_RGB expected" }
        val w = image.width
        val h = image.height
        val s = stride(w)
        val data: IntArray = (image.raster.dataBuffer as DataBufferInt).data
        val header = ByteBuffer.allocate(HEADER_SIZE).order(ByteOrder.LITTLE_ENDIAN)
        header.putInt(HEADER_SIZE)
        header.putInt(w)
        header.putInt(h)
        header.putShort(1)
        header.putShort(24)
        header.putInt(0)
        header.putInt(s * h)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        header.putInt(0)
        sink(0L, header.array(), HEADER_SIZE)
        val row = ByteArray(s)
        for (i in 0 until h) {
            val y = h - 1 - i
            for (x in 0 until w) {
                val p = data[y * w + x]
                row[3 * x] = (p and 0xFF).toByte()
                row[3 * x + 1] = ((p shr 8) and 0xFF).toByte()
                row[3 * x + 2] = ((p shr 16) and 0xFF).toByte()
            }
            sink(HEADER_SIZE.toLong() + i.toLong() * s, row, s)
        }
    }

    /** Удобная обёртка для тестов: весь DIB в ByteArray. */
    fun encode(image: BufferedImage): ByteArray {
        val out = ByteArrayOutputStream()
        write(image) { _, buffer, length -> out.write(buffer, 0, length) }
        return out.toByteArray()
    }
}