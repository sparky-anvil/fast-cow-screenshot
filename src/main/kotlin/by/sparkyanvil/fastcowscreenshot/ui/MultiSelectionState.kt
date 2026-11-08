package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.SelectionGeometry

/** Результат нажатия левой кнопки. */
internal enum class PressResult { STARTED, LIMIT_REACHED, INSIDE_REGION }

/** Результат отпускания левой кнопки. */
internal enum class ReleaseResult { ADDED, TOO_SMALL, INTERSECTS, NO_DRAG }

/**
 * Набор регионов оверлея «Несколько областей». Только EDT, не потокобезопасен.
 * @param maxRegions лимит, >= 1 (@throws IllegalArgumentException иначе).
 */
internal class MultiSelectionState(val maxRegions: Int) {
    init {
        require(maxRegions >= 1) { "maxRegions must be positive: $maxRegions" }
    }

    private val items = mutableListOf<PixelRect>()
    private var anchor: PixelPoint? = null
    private var current: PixelPoint? = null

    /** Регионы в порядке добавления (номер на экране = индекс + 1). Возвращает неизменяемую копию. */
    val regions: List<PixelRect> get() = items.toList()

    /** Текущий перетаскиваемый прямоугольник или null, если перетаскивания нет. */
    val dragRect: PixelRect?
        get() {
            val a = anchor ?: return null
            val c = current ?: return null
            return SelectionGeometry.rectFromPoints(a, c)
        }

    /** true, если идёт перетаскивание. */
    val isDragging: Boolean get() = anchor != null

    /** true, если dragRect != null и пересекается хотя бы с одним регионом. */
    val dragIntersects: Boolean
        get() {
            val rect = dragRect ?: return false
            return items.any { it.intersect(rect) != null }
        }

    /** regions.size >= maxRegions. */
    val isFull: Boolean get() = items.size >= maxRegions

    /** Порядок проверок: isFull → LIMIT_REACHED; точка внутри региона → INSIDE_REGION; иначе начать перетаскивание → STARTED. */
    fun press(point: PixelPoint): PressResult {
        if (isFull) return PressResult.LIMIT_REACHED
        if (items.any { it.contains(point) }) return PressResult.INSIDE_REGION
        anchor = point
        current = point
        return PressResult.STARTED
    }

    /** Обновляет второй угол; без перетаскивания игнорируется. */
    fun drag(point: PixelPoint) {
        if (anchor != null) current = point
    }

    /**
     * Завершает перетаскивание (состояние перетаскивания сбрасывается при любом результате, кроме NO_DRAG).
     * Порядок: нет перетаскивания → NO_DRAG; SelectionGeometry.isCancelled(rect) → TOO_SMALL; пересечение → INTERSECTS;
     * иначе регион добавляется → ADDED.
     */
    fun release(point: PixelPoint): ReleaseResult {
        if (anchor == null) return ReleaseResult.NO_DRAG
        current = point
        val rect = dragRect ?: return ReleaseResult.NO_DRAG
        anchor = null
        current = null
        if (SelectionGeometry.isCancelled(rect)) return ReleaseResult.TOO_SMALL
        if (items.any { it.intersect(rect) != null }) return ReleaseResult.INTERSECTS
        items += rect
        return ReleaseResult.ADDED
    }

    /** Удаляет последний регион. false, если регионов нет. */
    fun removeLast(): Boolean {
        if (items.isEmpty()) return false
        items.removeAt(items.lastIndex)
        return true
    }

    /** Удаляет регион, содержащий точку. false, если такого нет. Во время перетаскивания — ничего, false. */
    fun removeAt(point: PixelPoint): Boolean {
        if (isDragging) return false
        val idx = items.indexOfFirst { it.contains(point) }
        if (idx == -1) return false
        items.removeAt(idx)
        return true
    }

    /** Прерывает перетаскивание без добавления. */
    fun cancelDrag() {
        anchor = null
        current = null
    }

    /** "k / N", например "3 / 10". */
    fun counterLabel(): String = "%d / %d".format(items.size, maxRegions)

    /** "201×201" (× — U+00D7) для dragRect или null. */
    fun sizeLabel(): String? = dragRect?.let { "${it.width}\u00D7${it.height}" }
}