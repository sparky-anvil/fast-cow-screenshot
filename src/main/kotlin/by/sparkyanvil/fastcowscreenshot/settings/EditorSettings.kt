package by.sparkyanvil.fastcowscreenshot.settings

import kotlinx.serialization.Serializable

/**
 * Настройки карандаша редактора (REQ-003.17–19). Хранятся как есть; неверные значения заменяются по умолчанию
 * только при чтении через penRgb()/effectivePenWidth().
 */
@Serializable
internal data class EditorSettings(
    val penColor: String = DEFAULT_PEN_COLOR, // "#RRGGBB", одно из PALETTE
    val penWidth: Int = DEFAULT_PEN_WIDTH,    // px изображения, одно из PEN_WIDTHS
) {
    /** RGB цвета 0xRRGGBB, если penColor разбирается и входит в PALETTE_RGB; иначе DEFAULT_PEN_RGB. */
    fun penRgb(): Int = MultiRegionSettings.parseHexColor(penColor)?.takeIf { it in PALETTE_RGB } ?: DEFAULT_PEN_RGB

    /** penWidth, если входит в PEN_WIDTHS; иначе DEFAULT_PEN_WIDTH. */
    fun effectivePenWidth(): Int = if (penWidth in PEN_WIDTHS) penWidth else DEFAULT_PEN_WIDTH

    companion object {
        const val DEFAULT_PEN_COLOR: String = "#FF0000"
        const val DEFAULT_PEN_RGB: Int = 0xFF0000
        const val DEFAULT_PEN_WIDTH: Int = 4

        /** Палитра в порядке показа на панели (REQ-003.18). */
        val PALETTE: List<String> = listOf("#FF0000", "#FFD800", "#00C853", "#2979FF", "#000000", "#FFFFFF")

        /** PALETTE в виде 0xRRGGBB, тот же порядок. */
        val PALETTE_RGB: List<Int> = listOf(0xFF0000, 0xFFD800, 0x00C853, 0x2979FF, 0x000000, 0xFFFFFF)

        /** Толщины в порядке показа (REQ-003.17). */
        val PEN_WIDTHS: List<Int> = listOf(2, 4, 8, 16)
    }
}
