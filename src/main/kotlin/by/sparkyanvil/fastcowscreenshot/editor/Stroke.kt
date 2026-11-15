package by.sparkyanvil.fastcowscreenshot.editor

/** Точка штриха в координатах итогового изображения (физические пиксели, дробные). */
internal data class StrokePoint(val x: Float, val y: Float)

/**
 * Штрих карандаша. Неизменяем.
 * @property rgb цвет 0xRRGGBB (без альфы, штрих непрозрачный).
 * @property width толщина в пикселях изображения (> 0).
 * @property points точки ломаной в порядке рисования; 1 точка — «точка» диаметром width.
 * @throws IllegalArgumentException points пуст ("stroke must have points") или width <= 0 ("width must be positive: <w>").
 */
internal data class Stroke(val rgb: Int, val width: Int, val points: List<StrokePoint>) {
    init {
        require(points.isNotEmpty()) { "stroke must have points" }
        require(width > 0) { "width must be positive: $width" }
    }
}