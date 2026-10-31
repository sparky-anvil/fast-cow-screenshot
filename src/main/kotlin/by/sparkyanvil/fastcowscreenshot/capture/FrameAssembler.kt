package by.sparkyanvil.fastcowscreenshot.capture

import by.sparkyanvil.fastcowscreenshot.domain.GAP_FILL_RGB
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt

/** Сборка кадра из DIB. Без состояния. */
internal object FrameAssembler {
    /** Прямоугольники мониторов, обрезанные по area (пустые пересечения отброшены), в порядке desktop.monitors. */
    fun coveredRects(area: PixelRect, desktop: VirtualDesktop): List<PixelRect> =
        desktop.monitors.mapNotNull { it.bounds.intersect(area) }

    /**
     * Создаёт BufferedImage(area.width, area.height, TYPE_INT_RGB), заливает GAP_FILL_RGB, затем для каждой строки
     * каждого прямоугольника из covered вызывает copy(startIndex, data, count), где data — массив пикселей изображения,
     * startIndex = (r.top - area.top + row) * area.width + (r.left - area.left), count = r.width.
     */
    fun assemble(
        area: PixelRect,
        covered: List<PixelRect>,
        copy: (startIndex: Int, data: IntArray, count: Int) -> Unit,
    ): BufferedImage {
        val image = BufferedImage(area.width, area.height, BufferedImage.TYPE_INT_RGB)
        val data: IntArray = (image.raster.dataBuffer as DataBufferInt).data
        data.fill(GAP_FILL_RGB)
        for (r in covered) {
            for (row in 0 until r.height) {
                val startIndex = (r.top - area.top + row) * area.width + (r.left - area.left)
                copy(startIndex, data, r.width)
            }
        }
        return image
    }
}