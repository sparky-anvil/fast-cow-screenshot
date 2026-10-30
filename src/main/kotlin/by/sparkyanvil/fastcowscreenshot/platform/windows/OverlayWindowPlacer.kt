package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.domain.PixelRect
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.RECT
import org.slf4j.LoggerFactory

/** Точное размещение окна в физических координатах. Вызывать на EDT для видимого окна. */
internal object OverlayWindowPlacer {
    private val log = LoggerFactory.getLogger(OverlayWindowPlacer::class.java)

    /**
     * Дважды SetWindowPos(hwnd, HWND_TOPMOST, b.left, b.top, b.width, b.height, SWP_SHOWWINDOW),
     * затем GetWindowRect. Возвращает фактический прямоугольник; null — если вызов не удался.
     * При расхождении с b: WARN "Overlay placed at {} instead of {}".
     */
    fun place(window: java.awt.Window, bounds: PixelRect): PixelRect? {
        val user32 = User32Api.INSTANCE
        val hwnd = WindowHandles.hwndOf(window)
        val topmost = HWND(Pointer.createConstant(WinConst.HWND_TOPMOST_VALUE.toLong()))
        repeat(2) {
            val ok = user32.SetWindowPos(
                hwnd, topmost, bounds.left, bounds.top, bounds.width, bounds.height, WinConst.SWP_SHOWWINDOW,
            )
            if (!ok) {
                log.warn("SetWindowPos failed for {}", bounds)
                return null
            }
        }
        val r = RECT()
        if (!user32.GetWindowRect(hwnd, r)) {
            log.warn("GetWindowRect failed")
            return null
        }
        val placed = PixelRect(r.left, r.top, r.right - r.left, r.bottom - r.top)
        if (placed != bounds) log.warn("Overlay placed at {} instead of {}", placed, bounds)
        return placed
    }
}