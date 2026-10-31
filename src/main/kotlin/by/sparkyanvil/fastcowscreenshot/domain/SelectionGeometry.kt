package by.sparkyanvil.fastcowscreenshot.domain

import kotlin.math.abs
import kotlin.math.min

/** Геометрия выделения области. Без состояния. */
internal object SelectionGeometry {
    const val MIN_SIZE: Int = 2

    /** Прямоугольник по двум угловым пикселям, оба включительно. */
    fun rectFromPoints(a: PixelPoint, b: PixelPoint): PixelRect =
        PixelRect(min(a.x, b.x), min(a.y, b.y), abs(a.x - b.x) + 1, abs(a.y - b.y) + 1)

    /** true, если rect.width < MIN_SIZE или rect.height < MIN_SIZE. */
    fun isCancelled(rect: PixelRect): Boolean = rect.width < MIN_SIZE || rect.height < MIN_SIZE

    /** Перевод из координат стола в координаты кадра, начинающегося в frameOrigin. */
    fun toFrameRect(rect: PixelRect, frameOrigin: PixelPoint): PixelRect =
        rect.translate(-frameOrigin.x, -frameOrigin.y)
}