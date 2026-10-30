package by.sparkyanvil.fastcowscreenshot.ui

import java.awt.Color
import java.awt.Font
import java.awt.RenderingHints
import java.awt.geom.RoundRectangle2D
import java.awt.image.BufferedImage

/** Значок трея, рисуемый программно. */
internal object TrayIconImage {
    private const val SIZE = 32
    private const val RADIUS = 8.0
    private const val FONT_SIZE = 22
    private val BACKGROUND = Color(0x2E86DE)

    /** 32×32 TYPE_INT_ARGB: скруглённый квадрат #2E86DE (радиус 8), белая буква "F", шрифт Font.SANS_SERIF BOLD 22, по центру. */
    fun create(): BufferedImage {
        val image = BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON)
            g.color = BACKGROUND
            g.fill(RoundRectangle2D.Double(0.0, 0.0, SIZE.toDouble(), SIZE.toDouble(), RADIUS * 2, RADIUS * 2))
            g.color = Color.WHITE
            g.font = Font(Font.SANS_SERIF, Font.BOLD, FONT_SIZE)
            val fm = g.fontMetrics
            val x = (SIZE - fm.stringWidth("F")) / 2
            val y = (SIZE - fm.height) / 2 + fm.ascent
            g.drawString("F", x, y)
        } finally {
            g.dispose()
        }
        return image
    }
}
