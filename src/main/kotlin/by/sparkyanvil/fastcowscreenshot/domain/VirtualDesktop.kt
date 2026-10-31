package by.sparkyanvil.fastcowscreenshot.domain

/** Снимок конфигурации мониторов на момент захвата. Неизменяем. */
internal data class VirtualDesktop(val monitors: List<MonitorInfo>) {
    init {
        require(monitors.isNotEmpty()) { "monitors must not be empty" }
    }

    /** Ограничивающий прямоугольник всех мониторов. */
    val bounds: PixelRect = PixelRect.union(monitors.map { it.bounds })

    /** Первый монитор с isPrimary == true; если такого нет — первый монитор. */
    val primary: MonitorInfo = monitors.firstOrNull { it.isPrimary } ?: monitors.first()

    /** Первый монитор (в порядке списка), чьи bounds содержат point; null — если ни один. */
    fun monitorAt(point: PixelPoint): MonitorInfo? = monitors.firstOrNull { it.bounds.contains(point) }
}