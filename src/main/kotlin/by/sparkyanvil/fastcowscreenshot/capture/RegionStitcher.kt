package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.StitchLayout
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/** Копирование пикселей регионов по раскладке склейки. Без состояния. Поток: fcs-capture. */
internal object RegionStitcher {
    /**
     * Создаёт BufferedImage(layout.width, layout.height, TYPE_INT_RGB), заливает fillRgb, затем для каждой
     * StitchPlacement копирует source из frame построчно в (targetLeft, targetTop).
     * @param frame кадр TYPE_INT_RGB (DataBufferInt); пиксель (0,0) кадра = frameOrigin в координатах стола.
     * @param fillRgb 0xRRGGBB.
     * @throws IllegalArgumentException если какой-либо source выходит за пределы кадра ("region outside frame: <rect>").
     */
    fun stitch(frame: BufferedImage, frameOrigin: PixelPoint, layout: StitchLayout, fillRgb: Int): BufferedImage {
        require(frame.raster.dataBuffer is DataBufferInt) { "frame must use DataBufferInt" }
        val out = BufferedImage(layout.width, layout.height, BufferedImage.TYPE_INT_RGB)
        val dst = (out.raster.dataBuffer as DataBufferInt).data
        dst.fill(fillRgb)
        val src = (frame.raster.dataBuffer as DataBufferInt).data
        for (p in layout.placements) {
            val w = p.source.width
            val h = p.source.height
            val fx = p.source.left - frameOrigin.x
            val fy = p.source.top - frameOrigin.y
            require(fx >= 0 && fy >= 0 && fx + w <= frame.width && fy + h <= frame.height) {
                "region outside frame: ${p.source}"
            }
            for (row in 0 until h) {
                System.arraycopy(src, (fy + row) * frame.width + fx, dst, (p.targetTop + row) * layout.width + p.targetLeft, w)
            }
        }
        return out
    }
}