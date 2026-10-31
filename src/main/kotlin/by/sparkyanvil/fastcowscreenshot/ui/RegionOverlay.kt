package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.RegionSelector
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.platform.windows.CursorPosition
import by.sparkyanvil.fastcowscreenshot.platform.windows.OverlayWindowPlacer
import org.slf4j.LoggerFactory
import java.awt.Color
import java.awt.Cursor
import java.awt.Font
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Window
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.awt.geom.AffineTransform
import java.awt.image.BufferedImage
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.JFrame
import javax.swing.JPanel
import kotlin.math.roundToInt

/** Оверлей. Все методы — на EDT. */
internal class RegionOverlay : RegionSelector {
    override fun select(frame: BufferedImage, desktop: VirtualDesktop, onResult: (PixelRect?) -> Unit) {
        val startNs = System.nanoTime()
        val done = AtomicBoolean(false)
        val state = SelectionState()
        val windows = mutableListOf<JFrame>()
        val panels = mutableListOf<OverlayPanel>()

        fun finish(rect: PixelRect?) {
            if (done.compareAndSet(false, true)) {
                windows.forEach { it.dispose() }
                onResult(rect)
            }
        }

        fun repaintAll() = panels.forEach { it.repaint() }

        try {
            for (m in desktop.monitors) {
                val f = JFrame()
                f.isUndecorated = true
                f.type = Window.Type.UTILITY
                f.isAlwaysOnTop = true
                f.isResizable = false
                val panel = OverlayPanel(frame, desktop, state)
                panel.cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                panel.isFocusable = true
                f.contentPane = panel
                f.setSize(PROBE_SIZE, PROBE_SIZE)
                f.isVisible = true
                val placed = OverlayWindowPlacer.place(f, m.bounds)
                    ?: throw IllegalStateException("Overlay placement failed for ${m.deviceName}")
                panel.placedBounds = placed
                windows += f
                panels += panel

                val scale = f.graphicsConfiguration.defaultTransform.scaleX
                fun point(e: MouseEvent): PixelPoint = CursorPosition.current()
                    ?: PixelPoint(placed.left + (e.x * scale).roundToInt(), placed.top + (e.y * scale).roundToInt())

                panel.addMouseListener(object : MouseAdapter() {
                    override fun mousePressed(e: MouseEvent) {
                        when (e.button) {
                            MouseEvent.BUTTON1 -> {
                                state.press(point(e))
                                repaintAll()
                            }
                            MouseEvent.BUTTON3 -> finish(null)
                        }
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) finish(state.release(point(e)))
                    }
                })
                panel.addMouseMotionListener(object : MouseAdapter() {
                    override fun mouseDragged(e: MouseEvent) {
                        state.drag(point(e))
                        repaintAll()
                    }
                })
                val keys = object : KeyAdapter() {
                    override fun keyPressed(e: KeyEvent) {
                        if (e.keyCode == KeyEvent.VK_ESCAPE) finish(null)
                    }
                }
                f.addKeyListener(keys)
                panel.addKeyListener(keys)
            }
            val cursor = CursorPosition.current()
            val index = panels.indexOfFirst { p -> cursor != null && p.placedBounds.contains(cursor) }.coerceAtLeast(0)
            windows[index].toFront()
            windows[index].requestFocus()
            panels[index].requestFocusInWindow()
            log.info("Overlay shown in {} ms", (System.nanoTime() - startNs) / NANOS_PER_MS)
        } catch (e: Exception) {
            log.warn("Overlay closed: {}", e.toString())
            finish(null)
        }
    }

    companion object {
        const val DIM_ALPHA: Int = 102 // 40 % от 255
        const val BORDER_RGB: Int = 0x2E86DE
        const val LABEL_BG_ARGB: Int = 0xA0000000.toInt()
        const val LABEL_FONT_PT: Int = 14 // умножается на scaleX монитора
        const val LABEL_PADDING: Int = 4 // логических px, умножается на scaleX

        private const val PROBE_SIZE: Int = 100
        private const val NANOS_PER_MS: Long = 1_000_000L
        private val log = LoggerFactory.getLogger(RegionOverlay::class.java)
    }
}

/** Панель одного монитора: рисует фрагмент кадра 1:1 в физических пикселях. */
private class OverlayPanel(
    private val frame: BufferedImage,
    private val desktop: VirtualDesktop,
    private val state: SelectionState,
) : JPanel() {
    var placedBounds: PixelRect = desktop.bounds

    override fun paintComponent(g: Graphics) {
        val g2 = g.create() as Graphics2D
        try {
            val t = g2.transform
            val scale = t.scaleX
            g2.transform = AffineTransform.getTranslateInstance(t.translateX, t.translateY)
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            val p = placedBounds
            val sx = p.left - desktop.bounds.left
            val sy = p.top - desktop.bounds.top
            g2.drawImage(frame, 0, 0, p.width, p.height, sx, sy, sx + p.width, sy + p.height, null)
            g2.color = Color(0, 0, 0, RegionOverlay.DIM_ALPHA)
            g2.fillRect(0, 0, p.width, p.height)
            val sel = state.rect?.intersect(p)
            if (sel != null) {
                val l = sel.translate(-p.left, -p.top)
                g2.drawImage(
                    frame, l.left, l.top, l.right, l.bottom,
                    sx + l.left, sy + l.top, sx + l.right, sy + l.bottom, null,
                )
                g2.color = Color(RegionOverlay.BORDER_RGB)
                g2.drawRect(l.left, l.top, l.width - 1, l.height - 1)
            }
            paintLabel(g2, p, scale)
        } finally {
            g2.dispose()
        }
    }

    private fun paintLabel(g2: Graphics2D, p: PixelRect, scale: Double) {
        val label = state.sizeLabel() ?: return
        val cur = CursorPosition.current() ?: return
        if (!p.contains(cur)) return
        g2.font = Font("Segoe UI", Font.BOLD, (RegionOverlay.LABEL_FONT_PT * scale).roundToInt())
        val pad = (RegionOverlay.LABEL_PADDING * scale).roundToInt()
        val fm = g2.fontMetrics
        val textW = fm.stringWidth(label)
        val boxW = textW + 2 * pad
        val boxH = fm.height + 2 * pad
        val offset = (LABEL_OFFSET * scale).roundToInt()
        val boxX = (cur.x - p.left + offset).coerceAtMost(p.width - boxW).coerceAtLeast(0)
        val boxY = (cur.y - p.top + offset).coerceAtMost(p.height - boxH).coerceAtLeast(0)
        g2.color = Color(RegionOverlay.LABEL_BG_ARGB, true)
        g2.fillRect(boxX, boxY, boxW, boxH)
        g2.color = Color.WHITE
        g2.drawString(label, boxX + pad, boxY + pad + fm.ascent)
    }

    private companion object {
        const val LABEL_OFFSET: Int = 16
    }
}