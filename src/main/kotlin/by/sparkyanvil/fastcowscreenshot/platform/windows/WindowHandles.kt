package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Native
import com.sun.jna.platform.win32.WinDef.HWND
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(WindowHandles::class.java)

/** HWND AWT-окон. Вызывать на EDT. */
internal object WindowHandles {
    /** HWND окна; окно должно быть displayable. */
    fun hwndOf(window: java.awt.Window): HWND {
        require(window.isDisplayable) { "window is not displayable" }
        return HWND(Native.getWindowPointer(window))
    }

    /** Исключает окно из захвата экрана. Возвращает результат вызова. */
    fun excludeFromCapture(window: java.awt.Window): Boolean {
        val ok = User32Api.INSTANCE.SetWindowDisplayAffinity(hwndOf(window), WinConst.WDA_EXCLUDEFROMCAPTURE)
        if (!ok) {
            log.warn("SetWindowDisplayAffinity failed, GetLastError={}", Native.getLastError())
        }
        return ok
    }
}