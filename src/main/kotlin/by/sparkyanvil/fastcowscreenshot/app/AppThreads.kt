package by.sparkyanvil.fastcowscreenshot.app

import by.sparkyanvil.fastcowscreenshot.platform.windows.DpiAwareness
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.platform.win32.Ole32
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.SwingUtilities

/** Потоки приложения (conventions.md §6). */
internal class AppThreads(
    captureInit: () -> Unit = { DpiAwareness.enterPerMonitorV2() },
    dialogInit: () -> Unit = { Ole32.INSTANCE.CoInitializeEx(null, WinConst.COINIT_APARTMENTTHREADED) },
) {
    /** Один поток "fcs-capture", daemon, сначала captureInit(). */
    val capture: ExecutorService = singleThread("fcs-capture", captureInit)

    /** Один поток "fcs-save", daemon. */
    val save: ExecutorService = singleThread("fcs-save") {}

    /** Один поток "fcs-dialog", daemon, сначала dialogInit(). */
    val dialog: ExecutorService = singleThread("fcs-dialog", dialogInit)

    val ui: Executor = Executor { SwingUtilities.invokeLater(it) }

    /** shutdown() всех; save.awaitTermination(timeoutMs); затем shutdownNow() всех. */
    fun shutdown(timeoutMs: Long) {
        capture.shutdown()
        save.shutdown()
        dialog.shutdown()
        save.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)
        capture.shutdownNow()
        save.shutdownNow()
        dialog.shutdownNow()
    }

    private fun singleThread(name: String, init: () -> Unit): ExecutorService =
        Executors.newSingleThreadExecutor { r -> Thread({ init(); r.run() }, name).apply { isDaemon = true } }
}