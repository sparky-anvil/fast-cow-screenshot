package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Structure
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinDef.POINT

/** Структура CURSORINFO (x64, 24 байта). */
@Structure.FieldOrder("cbSize", "flags", "hCursor", "ptScreenPos")
internal class CURSORINFO : Structure() {
    @JvmField var cbSize: Int = 0
    @JvmField var flags: Int = 0
    @JvmField var hCursor: HICON? = null
    @JvmField var ptScreenPos: POINT = POINT()

    init {
        cbSize = size()
    }
}