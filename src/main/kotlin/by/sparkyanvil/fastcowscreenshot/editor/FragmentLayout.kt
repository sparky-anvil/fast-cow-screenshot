package by.sparkyanvil.fastcowscreenshot.editor

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.StitchLayout

/** Чистая геометрия фрагментов редактора (TDD-004 §8). Без состояния, потокобезопасен. */
internal object FragmentLayout {
    /**
     * Прямоугольники фрагментов на изображении: i-й = размер sources[i] в позиции positions[i].
     * @throws IllegalArgumentException если размеры списков разные ("sizes differ: <a> vs <b>").
     */
    fun placed(sources: List<PixelRect>, positions: List<PixelPoint>): List<PixelRect> {
        require(sources.size == positions.size) { "sizes differ: ${sources.size} vs ${positions.size}" }
        return sources.indices.map { i -> PixelRect(positions[i].x, positions[i].y, sources[i].width, sources[i].height) }
    }

    /** Сдвиг, переносящий левый верхний угол PixelRect.union(rects) в (0,0): PixelPoint(-left, -top). rects не пуст. */
    fun normalizeShift(rects: List<PixelRect>): PixelPoint {
        val b = PixelRect.union(rects)
        return PixelPoint(-b.left, -b.top)
    }

    /** true, если candidate имеет ненулевую общую площадь хотя бы с одним rects[j], j != index. */
    fun intersectsAny(rects: List<PixelRect>, index: Int, candidate: PixelRect): Boolean =
        rects.indices.any { j -> j != index && rects[j].intersect(candidate) != null }

    /**
     * Ограничивает позицию фрагмента index (REQ-003.27): после переноса rects[index] в результат ширина
     * ограничивающего прямоугольника ≤ суммы ширин всех rects, высота ≤ суммы высот. Если фрагмент один — candidate.
     */
    fun clampPosition(rects: List<PixelRect>, index: Int, candidate: PixelPoint): PixelPoint {
        if (rects.size == 1) return candidate
        val o = PixelRect.union(rects.filterIndexed { j, _ -> j != index })
        val w = rects[index].width
        val h = rects[index].height
        val sumW = rects.sumOf { it.width }
        val sumH = rects.sumOf { it.height }
        val x = candidate.x.coerceIn(o.right - sumW, o.left + sumW - w)
        val y = candidate.y.coerceIn(o.bottom - sumH, o.top + sumH - h)
        return PixelPoint(x, y)
    }

    /** true, если union(rects).width ≤ Σ width и union(rects).height ≤ Σ height. */
    fun withinLimits(rects: List<PixelRect>): Boolean {
        val b = PixelRect.union(rects)
        return b.width <= rects.sumOf { it.width } && b.height <= rects.sumOf { it.height }
    }

    /**
     * Позиции после «Склеить» (REQ-003.28) в порядке rects: StitchLayout.compute(rects), для каждого rects[i]
     * — (targetLeft, targetTop) его placement. rects: непустые, непересекающиеся.
     */
    fun stitchPositions(rects: List<PixelRect>): List<PixelPoint> {
        val byRect = StitchLayout.compute(rects).placements.associateBy { it.source }
        return rects.map { r ->
            val p = byRect.getValue(r)
            PixelPoint(p.targetLeft, p.targetTop)
        }
    }

    /** Индекс первого rects[i], для которого rects[i].contains(point); null — ни одного. */
    fun fragmentAt(rects: List<PixelRect>, point: PixelPoint): Int? {
        val i = rects.indexOfFirst { it.contains(point) }
        return if (i < 0) null else i
    }
}