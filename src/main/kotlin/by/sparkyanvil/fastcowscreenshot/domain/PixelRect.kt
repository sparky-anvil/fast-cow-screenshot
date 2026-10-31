package by.sparkyanvil.fastcowscreenshot.domain

import kotlin.math.max
import kotlin.math.min

/**
 * Прямоугольник в физических пикселях. right/bottom исключительные. Неизменяем.
 * @throws IllegalArgumentException если width < 0 или height < 0.
 */
internal data class PixelRect(val left: Int, val top: Int, val width: Int, val height: Int) {
    init {
        require(width >= 0) { "width must not be negative: $width" }
        require(height >= 0) { "height must not be negative: $height" }
    }

    val right: Int get() = left + width
    val bottom: Int get() = top + height

    /** true, если width == 0 или height == 0. */
    val isEmpty: Boolean get() = width == 0 || height == 0

    /** true, если left <= x < right и top <= y < bottom. */
    fun contains(point: PixelPoint): Boolean =
        point.x >= left && point.x < right && point.y >= top && point.y < bottom

    /** Пересечение; null, если пересечение пустое (нулевая ширина или высота). */
    fun intersect(other: PixelRect): PixelRect? {
        val l = max(left, other.left)
        val t = max(top, other.top)
        val r = min(right, other.right)
        val b = min(bottom, other.bottom)
        return if (r <= l || b <= t) null else PixelRect(l, t, r - l, b - t)
    }

    /** Сдвиг на (dx, dy) без изменения размера. */
    fun translate(dx: Int, dy: Int): PixelRect = PixelRect(left + dx, top + dy, width, height)

    companion object {
        /**
         * Ограничивающий прямоугольник. @throws IllegalArgumentException если rects пуст.
         */
        fun union(rects: List<PixelRect>): PixelRect {
            require(rects.isNotEmpty()) { "rects must not be empty" }
            val l = rects.minOf { it.left }
            val t = rects.minOf { it.top }
            val r = rects.maxOf { it.right }
            val b = rects.maxOf { it.bottom }
            return PixelRect(l, t, r - l, b - t)
        }
    }
}