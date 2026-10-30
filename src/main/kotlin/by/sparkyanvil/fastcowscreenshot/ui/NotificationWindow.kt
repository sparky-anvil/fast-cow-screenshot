package by.sparkyanvil.fastcowscreenshot.ui

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.awt.ComposePanel
import by.sparkyanvil.fastcowscreenshot.capture.CaptureNotifier
import by.sparkyanvil.fastcowscreenshot.capture.CaptureOutcome
import by.sparkyanvil.fastcowscreenshot.platform.windows.ExplorerLauncher
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowHandles
import org.slf4j.LoggerFactory
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.awt.Window
import java.util.concurrent.Executor
import javax.swing.JWindow
import javax.swing.Timer

/** Окно уведомлений. Все методы — на EDT. */
internal class NotificationWindow(
    private val onSaveAs: () -> Unit,
    private val explorer: ExplorerLauncher,
    private val dialogExecutor: Executor,
) : CaptureNotifier {
    private val state = mutableStateOf<NotificationModel?>(null)
    private var window: JWindow? = null
    private val timer = Timer(DISPLAY_MS) { hide() }.apply { isRepeats = false }

    override fun showOutcome(outcome: CaptureOutcome) = show(NotificationModels.fromOutcome(outcome))

    override fun showMessage(title: String, lines: List<String>) = show(NotificationModels.message(title, lines))

    /** Показывает уведомление, заменяя предыдущее, и перезапускает таймер скрытия. */
    fun show(model: NotificationModel) {
        val w = window ?: createWindow().also { window = it }
        state.value = model
        val gc = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration
        val b = gc.bounds
        val ins = Toolkit.getDefaultToolkit().getScreenInsets(gc)
        val x = b.x + b.width - ins.right - WIDTH - MARGIN
        val y = b.y + b.height - ins.bottom - HEIGHT - MARGIN
        w.setLocation(x, y)
        w.isVisible = true
        timer.restart()
        log.debug("Notification shown: {}", model.title)
    }

    /** Скрывает окно и останавливает таймер. */
    fun hide() {
        timer.stop()
        window?.isVisible = false
    }

    /** window.dispose(), timer.stop(). */
    fun dispose() {
        timer.stop()
        window?.dispose()
        window = null
    }

    private fun createWindow(): JWindow {
        val w = JWindow()
        w.focusableWindowState = false
        w.isAlwaysOnTop = true
        w.type = Window.Type.POPUP
        w.setSize(WIDTH, HEIGHT)
        val panel = ComposePanel()
        panel.setContent {
            state.value?.let { m ->
                NotificationContent(
                    m,
                    onClick = { onBodyClick(m) },
                    onSaveAs = {
                        hide()
                        onSaveAs()
                    },
                )
            }
        }
        w.contentPane.add(panel)
        w.addNotify()
        WindowHandles.excludeFromCapture(w)
        return w
    }

    private fun onBodyClick(model: NotificationModel) {
        hide()
        val file = model.fileToReveal ?: return
        dialogExecutor.execute { explorer.reveal(file) }
    }

    companion object {
        const val DISPLAY_MS: Int = 3000
        const val WIDTH: Int = 360
        const val HEIGHT: Int = 130
        const val MARGIN: Int = 12
        const val SAVE_AS_TEXT: String = "Сохранить как…"

        private val log = LoggerFactory.getLogger(NotificationWindow::class.java)
    }
}
