package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.MultiRegionRequest
import by.sparkyanvil.fastcowscreenshot.capture.MultiRegionSelector
import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.domain.VirtualDesktop
import by.sparkyanvil.fastcowscreenshot.platform.windows.CursorPosition
import by.sparkyanvil.fastcowscreenshot.platform.windows.OverlayWindowPlacer
import by.sparkyanvil.fastcowscreenshot.settings.OverlayAction
import by.sparkyanvil.fastcowscreenshot.settings.OverlayKeymap
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
import javax.swing.Timer
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Оверлей «Несколько областей». Все методы — на EDT.
 * @param onOpened вызывается один раз в начале select, до создания окон.
 * @param onClosed вызывается один раз при закрытии (подтверждение, отмена, ошибка) до onResult.
 */
internal class MultiRegionOverlay(
    private val onOpened: () -> Unit,
    private val onClosed: () -> Unit,
) : MultiRegionSelector {
    override fun select(
        frame: BufferedImage,
        desktop: VirtualDesktop,
        request: MultiRegionRequest,
        onResult: (List<PixelRect>?) -> Unit,
    ) {
        val startNs = System.nanoTime()
        val done = AtomicBoolean(false)
        val state = MultiSelectionState(request.maxRegions)
        val windows = mutableListOf<JFrame>()
        val panels = mutableListOf<MultiOverlayPanel>()
        val timer = Timer(MESSAGE_DURATION_MS) {
            panels.forEach {
                it.message = null
                it.repaint()
            }
        }
        timer.isRepeats = false

        fun repaintAll() = panels.forEach { it.repaint() }

        fun show(text: String) {
            panels.forEach { it.message = text }
            timer.restart()
            repaintAll()
        }

        fun finish(result: List<PixelRect>?) {
            if (done.compareAndSet(false, true)) {
                timer.stop()
                windows.forEach { it.dispose() }
                try {
                    onClosed()
                } catch (e: Exception) {
                    log.warn("onClosed failed: {}", e.toString())
                }
                onResult(result)
            }
        }

        try {
            onOpened()
            val hint = keysHint(request.keymap)
            for (m in desktop.monitors) {
                val f = JFrame()
                f.isUndecorated = true
                f.type = Window.Type.UTILITY
                f.isAlwaysOnTop = true
                f.isResizable = false
                val panel = MultiOverlayPanel(frame, desktop, state, hint)
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
                            MouseEvent.BUTTON1 -> when (state.press(point(e))) {
                                PressResult.STARTED -> repaintAll()
                                PressResult.LIMIT_REACHED -> show(limitMessage(request.maxRegions, request.keymap))
                                PressResult.INSIDE_REGION -> show(MSG_INTERSECT)
                            }
                            MouseEvent.BUTTON3 -> if (!state.isDragging && state.removeAt(point(e))) repaintAll()
                        }
                    }

                    override fun mouseReleased(e: MouseEvent) {
                        if (e.button == MouseEvent.BUTTON1) {
                            when (state.release(point(e))) {
                                ReleaseResult.ADDED, ReleaseResult.TOO_SMALL -> repaintAll()
                                ReleaseResult.INTERSECTS -> show(MSG_INTERSECT)
                                ReleaseResult.NO_DRAG -> Unit
                            }
                        }
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
                        val action = request.keymap.actionFor(
                            e.isControlDown, e.isAltDown, e.isShiftDown, e.keyCode,
                            e.keyLocation == KeyEvent.KEY_LOCATION_NUMPAD,
                        ) ?: return
                        e.consume()
                        when (action) {
                            OverlayAction.CONFIRM ->
                                if (state.regions.isEmpty()) show(MSG_EMPTY) else finish(state.regions)
                            OverlayAction.REMOVE_LAST -> if (state.removeLast()) repaintAll()
                            OverlayAction.CANCEL -> finish(null)
                        }
                    }
                }
                panel.addKeyListener(keys)
            }
            val cursor = CursorPosition.current()
            val index = panels.indexOfFirst { p -> cursor != null && p.placedBounds.contains(cursor) }.coerceAtLeast(0)
            windows[index].toFront()
            windows[index].requestFocus()
            panels[index].requestFocusInWindow()
            log.info("Multi-region overlay shown in {} ms", (System.nanoTime() - startNs) / NANOS_PER_MS)
        } catch (e: Exception) {
            log.warn("Multi-region overlay closed: {}", e.toString())
            finish(null)
        }
    }

    companion object {
        const val DIM_ALPHA: Int = 102
        const val BORDER_RGB: Int = 0x2E86DE
        const val INVALID_RGB: Int = 0xE74C3C
        const val BORDER_WIDTH: Int = 2 // логических px, умножается на scaleX
        const val LABEL_BG_ARGB: Int = 0xA0000000.toInt()
        const val LABEL_FONT_PT: Int = 14 // умножается на scaleX
        const val HINT_FONT_PT: Int = 12 // умножается на scaleX
        const val LABEL_PADDING: Int = 4 // логических px, умножается на scaleX
        const val PANEL_MARGIN: Int = 16 // логических px, умножается на scaleX
        const val MESSAGE_DURATION_MS: Int = 2000

        const val MSG_INTERSECT: String = "Области не должны пересекаться"
        const val MSG_LIMIT_FORMAT: String = "Достигнут лимит: %d областей. %s — собрать снимок"
        const val MSG_EMPTY: String = "Выделите хотя бы одну область"
        const val HINT_KEYS_FORMAT: String = "%s — собрать · %s — удалить последнюю · %s — отмена"

        private const val PROBE_SIZE: Int = 100
        private const val NANOS_PER_MS: Long = 1_000_000L
        private val log = LoggerFactory.getLogger(MultiRegionOverlay::class.java)

        /** HINT_KEYS_FORMAT с keymap.primaryName(CONFIRM), (REMOVE_LAST), (CANCEL). */
        fun keysHint(keymap: OverlayKeymap): String = HINT_KEYS_FORMAT.format(
            keymap.primaryName(OverlayAction.CONFIRM),
            keymap.primaryName(OverlayAction.REMOVE_LAST),
            keymap.primaryName(OverlayAction.CANCEL),
        )

        /** MSG_LIMIT_FORMAT с maxRegions и keymap.primaryName(CONFIRM). */
        fun limitMessage(maxRegions: Int, keymap: OverlayKeymap): String =
            MSG_LIMIT_FORMAT.format(maxRegions, keymap.primaryName(OverlayAction.CONFIRM))
    }
}

/** Панель одного монитора: рисует фрагмент кадра 1:1 в физических пикселях. */
private class MultiOverlayPanel(
    private val frame: BufferedImage,
    private val desktop: VirtualDesktop,
    private val state: MultiSelectionState,
    private val hint: String,
) : JPanel() {
    var placedBounds: PixelRect = desktop.bounds
    var message: String? = null

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
            g2.color = Color(0, 0, 0, MultiRegionOverlay.DIM_ALPHA)
            g2.fillRect(0, 0, p.width, p.height)

            val bw = max(1, (MultiRegionOverlay.BORDER_WIDTH * scale).roundToInt())
            for ((i, r) in state.regions.withIndex()) {
                val vis = r.intersect(p) ?: continue
                val l = paintFragment(g2, vis, p, sx, sy, MultiRegionOverlay.BORDER_RGB, bw)
                paintNumber(g2, "${i + 1}", l, bw, scale)
            }
            val drag = state.dragRect?.intersect(p)
            if (drag != null) {
                val rgb = if (state.dragIntersects) MultiRegionOverlay.INVALID_RGB else MultiRegionOverlay.BORDER_RGB
                paintFragment(g2, drag, p, sx, sy, rgb, bw)
            }
            paintSizeLabel(g2, p, scale)
            paintTopPanel(g2, p, scale)
            paintHint(g2, p, scale)
        } finally {
            g2.dispose()
        }
    }

    /** Рисует незатемнённый фрагмент и рамку; возвращает прямоугольник в координатах панели. */
    private fun paintFragment(g2: Graphics2D, vis: PixelRect, p: PixelRect, sx: Int, sy: Int, rgb: Int, bw: Int): PixelRect {
        val l = vis.translate(-p.left, -p.top)
        g2.drawImage(frame, l.left, l.top, l.right, l.bottom, sx + l.left, sy + l.top, sx + l.right, sy + l.bottom, null)
        g2.color = Color(rgb)
        for (k in 0 until bw) {
            val w = l.width - 1 - 2 * k
            val h = l.height - 1 - 2 * k
            if (w < 0 || h < 0) break
            g2.drawRect(l.left + k, l.top + k, w, h)
        }
        return l
    }

    private fun paintNumber(g2: Graphics2D, text: String, l: PixelRect, bw: Int, scale: Double) {
        g2.font = Font("Segoe UI", Font.BOLD, (MultiRegionOverlay.LABEL_FONT_PT * scale).roundToInt())
        val pad = (MultiRegionOverlay.LABEL_PADDING * scale).roundToInt()
        val fm = g2.fontMetrics
        val boxW = fm.stringWidth(text) + 2 * pad
        val boxH = fm.height + 2 * pad
        val x = l.left + bw
        val y = l.top + bw
        g2.color = Color(MultiRegionOverlay.LABEL_BG_ARGB, true)
        g2.fillRect(x, y, boxW, boxH)
        g2.color = Color.WHITE
        g2.drawString(text, x + pad, y + pad + fm.ascent)
    }

    private fun paintSizeLabel(g2: Graphics2D, p: PixelRect, scale: Double) {
        val label = state.sizeLabel() ?: return
        val cur = CursorPosition.current() ?: return
        if (!p.contains(cur)) return
        g2.font = Font("Segoe UI", Font.BOLD, (MultiRegionOverlay.LABEL_FONT_PT * scale).roundToInt())
        val pad = (MultiRegionOverlay.LABEL_PADDING * scale).roundToInt()
        val fm = g2.fontMetrics
        val boxW = fm.stringWidth(label) + 2 * pad
        val boxH = fm.height + 2 * pad
        val offset = (LABEL_OFFSET * scale).roundToInt()
        val boxX = (cur.x - p.left + offset).coerceAtMost(p.width - boxW).coerceAtLeast(0)
        val boxY = (cur.y - p.top + offset).coerceAtMost(p.height - boxH).coerceAtLeast(0)
        g2.color = Color(MultiRegionOverlay.LABEL_BG_ARGB, true)
        g2.fillRect(boxX, boxY, boxW, boxH)
        g2.color = Color.WHITE
        g2.drawString(label, boxX + pad, boxY + pad + g2.fontMetrics.ascent)
    }

    private fun paintTopPanel(g2: Graphics2D, p: PixelRect, scale: Double) {
        g2.font = Font("Segoe UI", Font.BOLD, (MultiRegionOverlay.LABEL_FONT_PT * scale).roundToInt())
        val pad = (MultiRegionOverlay.LABEL_PADDING * scale).roundToInt()
        val fm = g2.fontMetrics
        val lines = listOfNotNull(state.counterLabel(), message)
        val boxW = lines.maxOf { fm.stringWidth(it) } + 2 * pad
        val boxH = fm.height * lines.size + 2 * pad
        val boxX = (p.width - boxW) / 2
        val boxY = (MultiRegionOverlay.PANEL_MARGIN * scale).roundToInt()
        g2.color = Color(MultiRegionOverlay.LABEL_BG_ARGB, true)
        g2.fillRect(boxX, boxY, boxW, boxH)
        g2.color = Color.WHITE
        for ((i, line) in lines.withIndex()) {
            val w = fm.stringWidth(line)
            g2.drawString(line, boxX + (boxW - w) / 2, boxY + pad + i * fm.height + fm.ascent)
        }
    }

    private fun paintHint(g2: Graphics2D, p: PixelRect, scale: Double) {
        g2.font = Font("Segoe UI", Font.PLAIN, (MultiRegionOverlay.HINT_FONT_PT * scale).roundToInt())
        val pad = (MultiRegionOverlay.LABEL_PADDING * scale).roundToInt()
        val fm = g2.fontMetrics
        val boxW = fm.stringWidth(hint) + 2 * pad
        val boxH = fm.height + 2 * pad
        val boxX = (p.width - boxW) / 2
        val boxY = p.height - (MultiRegionOverlay.PANEL_MARGIN * scale).roundToInt() - boxH
        g2.color = Color(MultiRegionOverlay.LABEL_BG_ARGB, true)
        g2.fillRect(boxX, boxY, boxW, boxH)
        g2.color = Color.WHITE
        g2.drawString(hint, boxX + pad, boxY + pad + fm.ascent)
    }

    private companion object {
        const val LABEL_OFFSET: Int = 16
    }
}