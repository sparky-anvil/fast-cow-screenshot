package by.sparkyanvil.fastcowscreenshot.app

import by.sparkyanvil.fastcowscreenshot.capture.CaptureCoordinator
import by.sparkyanvil.fastcowscreenshot.capture.LastScreenshotHolder
import by.sparkyanvil.fastcowscreenshot.platform.windows.DpiAwareness
import by.sparkyanvil.fastcowscreenshot.platform.windows.ExplorerLauncher
import by.sparkyanvil.fastcowscreenshot.platform.windows.GdiScreenCapturer
import by.sparkyanvil.fastcowscreenshot.platform.windows.KnownFolders
import by.sparkyanvil.fastcowscreenshot.platform.windows.NativeMessageLoop
import by.sparkyanvil.fastcowscreenshot.platform.windows.WinApiException
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsClipboardWriter
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsCursorService
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsFolderDialog
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsHotkeyService
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsMessageBox
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsMonitorProvider
import by.sparkyanvil.fastcowscreenshot.platform.windows.WindowsSaveDialog
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyAction
import by.sparkyanvil.fastcowscreenshot.settings.HotkeyBinding
import by.sparkyanvil.fastcowscreenshot.settings.SettingsRepository
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import by.sparkyanvil.fastcowscreenshot.storage.AutoSaver
import by.sparkyanvil.fastcowscreenshot.storage.DirectoryValidator
import by.sparkyanvil.fastcowscreenshot.storage.PngFileWriter
import by.sparkyanvil.fastcowscreenshot.ui.NotificationWindow
import by.sparkyanvil.fastcowscreenshot.ui.RegionOverlay
import by.sparkyanvil.fastcowscreenshot.ui.SaveAsAction
import by.sparkyanvil.fastcowscreenshot.ui.SettingsController
import by.sparkyanvil.fastcowscreenshot.ui.SettingsWindow
import by.sparkyanvil.fastcowscreenshot.ui.TrayController
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import javax.swing.SwingUtilities
import javax.swing.UIManager
import kotlin.system.exitProcess

/** Приложение: сборка графа объектов, запуск и выход. */
internal class FastCowScreenshotApp {
    private val threads = AppThreads()
    private val settingsFile = SettingsRepository.defaultFile()
    private val store = SettingsStore(SettingsRepository(settingsFile, KnownFolders()))
    private val loop = NativeMessageLoop()
    private val hotkeys = WindowsHotkeyService(loop)
    private val last = LastScreenshotHolder(threads.ui)
    private val explorer = ExplorerLauncher()
    private val notifier: NotificationWindow = NotificationWindow({ saveAsAction.run() }, explorer, threads.dialog)
    private val saveAsAction: SaveAsAction = SaveAsAction(
        last,
        store,
        { dir, name -> WindowsSaveDialog().show(dir, name) },
        { WindowsMessageBox().confirmOverwrite(it) },
        PngFileWriter(),
        notifier,
        threads.dialog,
        threads.ui,
    )
    private val coordinator = CaptureCoordinator(
        WindowsMonitorProvider(),
        GdiScreenCapturer(),
        WindowsCursorService(),
        WindowsClipboardWriter({ loop.messageWindow }),
        AutoSaver(),
        RegionOverlay(),
        notifier,
        last,
        { store.current },
        { LocalDateTime.now() },
        threads.capture,
        threads.save,
        threads.ui,
    )
    private val controller = SettingsController(
        store,
        hotkeys,
        KnownFolders(),
        { WindowsFolderDialog().show() },
        DirectoryValidator::isWritable,
        { explorer.openFolder(it) },
        threads.dialog,
        threads.ui,
    )
    private val settingsWindow = SettingsWindow(controller, store)
    private val tray = TrayController(last, { saveAsAction.run() }, { settingsWindow.show() }, { exit() })

    /** Запуск. Вызывать на main-потоке. */
    fun start() {
        val os = System.getProperty("os.name")
        if (!os.startsWith("Windows")) {
            log.error("Unsupported OS: {}", os)
            exitProcess(1)
        }
        SwingUtilities.invokeAndWait {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName())
            val a = DpiAwareness.currentThreadAwareness()
            if (a == 2) log.info("EDT DPI awareness: {}", a) else log.warn("EDT DPI awareness: {}", a)
        }
        try {
            loop.start()
        } catch (e: WinApiException) {
            log.error("Native message loop failed", e)
            exitProcess(1)
        }
        hotkeys.start { action -> coordinator.trigger(action.mode) }

        val failed = controller.registerAll()
        if (failed.isNotEmpty()) {
            threads.ui.execute {
                notifier.showMessage(
                    HOTKEYS_UNAVAILABLE_TITLE,
                    failed.map { conflictLine(store.current.hotkeyBinding(it), it) },
                )
            }
        }
        try {
            SwingUtilities.invokeAndWait { tray.install() }
        } catch (e: java.lang.reflect.InvocationTargetException) {
            if (e.cause is IllegalStateException) {
                log.error("Tray installation failed: {}", e.cause?.message)
                exitProcess(1)
            }
            throw e
        }
        log.info("Application started, settings: {}", settingsFile)
    }

    /** Выход. Вызывать на EDT. */
    fun exit() {
        log.info("Exiting")
        exitStep { hotkeys.unregisterAll() }
        exitStep { loop.stop() }
        exitStep { notifier.dispose() }
        exitStep { settingsWindow.dispose() }
        exitStep { tray.remove() }
        exitStep { threads.shutdown(5000) }
        exitProcess(0)
    }

    private fun exitStep(step: () -> Unit) {
        try {
            step()
        } catch (e: Exception) {
            log.warn("Exit step failed: {}", e.toString())
        }
    }

    companion object {
        const val HOTKEYS_UNAVAILABLE_TITLE: String = "Горячие клавиши недоступны"

        private val log = LoggerFactory.getLogger(FastCowScreenshotApp::class.java)

        /** Строка: "<сочетание> («<режим>») — занято другим приложением". */
        fun conflictLine(binding: HotkeyBinding, action: HotkeyAction): String =
            "${binding.format()} («${action.displayName}») — занято другим приложением"
    }
}
