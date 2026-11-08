package by.sparkyanvil.fastcowscreenshot.domain

import kotlin.math.max

/**
 * Положение одного региона на склеенном изображении.
 * source — регион в координатах стола (физические пиксели); targetLeft/targetTop — левый верхний угол на результате.
 */
internal data class StitchPlacement(val source: PixelRect, val targetLeft: Int, val targetTop: Int)

/**
 * Раскладка склейки «сжатие пустот» (REQ-002.20). Неизменяема. Потокобезопасна.
 * placements отсортированы по (source.top, source.left).
 */
internal data class StitchLayout(val width: Int, val height: Int, val placements: List<StitchPlacement>) {
    companion object {
        /**
         * @param regions непустой список непустых попарно непересекающихся прямоугольников, любой порядок.
         * @throws IllegalArgumentException regions пуст ("regions must not be empty"); есть пустой прямоугольник
         *   ("empty region: <rect>"); два прямоугольника пересекаются ("regions intersect: <a> and <b>").
         */
        fun compute(regions: List<PixelRect>): StitchLayout {
            require(regions.isNotEmpty()) { "regions must not be empty" }
            for (r in regions) {
                require(!r.isEmpty) { "empty region: $r" }
            }
            for (i in regions.indices) {
                for (j in i + 1 until regions.size) {
                    require(regions[i].intersect(regions[j]) == null) {
                        "regions intersect: ${regions[i]} and ${regions[j]}"
                    }
                }
            }
            val xs = mergeIntervals(regions.map { it.left to it.right })
            val ys = mergeIntervals(regions.map { it.top to it.bottom })
            val width = xs.sumOf { it.second - it.first }
            val height = ys.sumOf { it.second - it.first }
            val placements = regions
                .sortedWith(compareBy({ it.top }, { it.left }))
                .map { StitchPlacement(it, mapCoordinate(xs, it.left), mapCoordinate(ys, it.top)) }
            return StitchLayout(width, height, placements)
        }
    }
}

/** Сливает пересекающиеся/касающиеся отрезки; возвращает (start, endExclusive, offset). */
private fun mergeIntervals(list: List<Pair<Int, Int>>): List<Triple<Int, Int, Int>> {
    val sorted = list.sortedBy { it.first }
    val merged = ArrayList<Pair<Int, Int>>()
    var curStart = sorted[0].first
    var curEnd = sorted[0].second
    for (k in 1 until sorted.size) {
        val (s, e) = sorted[k]
        if (s <= curEnd) {
            curEnd = max(curEnd, e)
        } else {
            merged.add(curStart to curEnd)
            curStart = s
            curEnd = e
        }
    }
    merged.add(curStart to curEnd)
    val result = ArrayList<Triple<Int, Int, Int>>()
    var offset = 0
    for ((s, e) in merged) {
        result.add(Triple(s, e, offset))
        offset += e - s
    }
    return result
}

private fun mapCoordinate(segs: List<Triple<Int, Int, Int>>, c: Int): Int {
    val seg = segs.first { c >= it.first && c < it.second }
    return seg.third + (c - seg.first)
}
