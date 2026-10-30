package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.LastScreenshotHolder
import java.awt.MenuItem
import java.awt.PopupMenu
import java.awt.SystemTray
import java.awt.TrayIcon

/** Трей. Методы — на EDT. */
internal class TrayController(
    private val lastScreenshot: LastScreenshotHolder,
    private val onSaveAs: () -> Unit,
    private val onSettings: () -> Unit,
    private val onExit: () -> Unit,
) {
    private var icon: TrayIcon? = null

    /** @throws IllegalStateException("System tray is not supported") если SystemTray.isSupported() == false. */
    fun install() {
        check(SystemTray.isSupported()) { "System tray is not supported" }
        val saveAs = MenuItem(ITEM_SAVE_AS).apply {
            isEnabled = lastScreenshot.get() != null
            addActionListener { onSaveAs() }
        }
        val settings = MenuItem(ITEM_SETTINGS).apply { addActionListener { onSettings() } }
        val exit = MenuItem(ITEM_EXIT).apply { addActionListener { onExit() } }
        val menu = PopupMenu().apply {
            add(saveAs)
            add(settings)
            addSeparator()
            add(exit)
        }
        val trayIcon = TrayIcon(TrayIconImage.create(), TOOLTIP, menu).apply {
            isImageAutoSize = true
            addActionListener { onSettings() }
        }
        SystemTray.getSystemTray().add(trayIcon)
        icon = trayIcon
        lastScreenshot.addListener { saveAs.isEnabled = true }
    }

    /** Убирает значок из трея. */
    fun remove() {
        icon?.let { SystemTray.getSystemTray().remove(it) }
        icon = null
    }

    companion object {
        const val TOOLTIP: String = "fast-cow-screenshot"
        const val ITEM_SAVE_AS: String = "Сохранить как…"
        const val ITEM_SETTINGS: String = "Настройки…"
        const val ITEM_EXIT: String = "Выход"
    }
}
