package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.SelectionGeometry

/** Выделение. Только EDT, не потокобезопасен. */
internal class SelectionState {
    private var anchorPoint: PixelPoint? = null
    private var currentPoint: PixelPoint? = null

    val anchor: PixelPoint? get() = anchorPoint

    /** Текущий прямоугольник (SelectionGeometry.rectFromPoints(anchor, current)) или null до press. */
    val rect: PixelRect?
        get() {
            val a = anchorPoint ?: return null
            val c = currentPoint ?: return null
            return SelectionGeometry.rectFromPoints(a, c)
        }

    fun press(point: PixelPoint) {
        anchorPoint = point
        currentPoint = point
    }

    /** Игнорируется до press. */
    fun drag(point: PixelPoint) {
        if (anchorPoint != null) currentPoint = point
    }

    /** Итоговый прямоугольник (или null без press); состояние сбрасывается. */
    fun release(point: PixelPoint): PixelRect? {
        drag(point)
        val result = rect
        anchorPoint = null
        currentPoint = null
        return result
    }

    /** "201×201" (знак × U+00D7) или null, если rect == null. */
    fun sizeLabel(): String? = rect?.let { "${it.width}\u00D7${it.height}" }
}