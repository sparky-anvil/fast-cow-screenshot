package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef.HWND

/** Структура OPENFILENAME (x64, 152 байта). */
@Structure.FieldOrder(
    "lStructSize", "hwndOwner", "hInstance", "lpstrFilter", "lpstrCustomFilter", "nMaxCustFilter",
    "nFilterIndex", "lpstrFile", "nMaxFile", "lpstrFileTitle", "nMaxFileTitle", "lpstrInitialDir",
    "lpstrTitle", "Flags", "nFileOffset", "nFileExtension", "lpstrDefExt", "lCustData", "lpfnHook",
    "lpTemplateName", "pvReserved", "dwReserved", "FlagsEx",
)
internal class OPENFILENAME : Structure() {
    @JvmField var lStructSize: Int = 0
    @JvmField var hwndOwner: HWND? = null
    @JvmField var hInstance: Pointer? = null
    @JvmField var lpstrFilter: Pointer? = null
    @JvmField var lpstrCustomFilter: Pointer? = null
    @JvmField var nMaxCustFilter: Int = 0
    @JvmField var nFilterIndex: Int = 0
    @JvmField var lpstrFile: Pointer? = null
    @JvmField var nMaxFile: Int = 0
    @JvmField var lpstrFileTitle: Pointer? = null
    @JvmField var nMaxFileTitle: Int = 0
    @JvmField var lpstrInitialDir: WString? = null
    @JvmField var lpstrTitle: WString? = null
    @JvmField var Flags: Int = 0
    @JvmField var nFileOffset: Short = 0
    @JvmField var nFileExtension: Short = 0
    @JvmField var lpstrDefExt: WString? = null
    @JvmField var lCustData: Pointer? = null
    @JvmField var lpfnHook: Pointer? = null
    @JvmField var lpTemplateName: Pointer? = null
    @JvmField var pvReserved: Pointer? = null
    @JvmField var dwReserved: Int = 0
    @JvmField var FlagsEx: Int = 0

    init {
        lStructSize = size()
    }
}