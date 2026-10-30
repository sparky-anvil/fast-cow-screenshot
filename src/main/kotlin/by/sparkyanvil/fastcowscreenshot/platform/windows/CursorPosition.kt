package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.domain.PixelPoint
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import com.sun.jna.platform.win32.WinDef.POINT

/** Позиция курсора в физических пикселях. Вызывать на EDT или fcs-capture. */
internal object CursorPosition {
    /** GetCursorPos; null при ошибке. */
    fun current(): PixelPoint? {
        val p = POINT()
        return if (User32Api.INSTANCE.GetCursorPos(p)) PixelPoint(p.x, p.y) else null
    }
}