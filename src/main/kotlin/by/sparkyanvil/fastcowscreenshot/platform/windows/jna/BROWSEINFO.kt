package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.WinDef.HWND

/** Структура BROWSEINFO (x64, 64 байта). */
@Structure.FieldOrder("hwndOwner", "pidlRoot", "pszDisplayName", "lpszTitle", "ulFlags", "lpfn", "lParam", "iImage")
internal class BROWSEINFO : Structure() {
    @JvmField var hwndOwner: HWND? = null
    @JvmField var pidlRoot: Pointer? = null
    @JvmField var pszDisplayName: Pointer? = null
    @JvmField var lpszTitle: WString? = null
    @JvmField var ulFlags: Int = 0
    @JvmField var lpfn: Pointer? = null
    @JvmField var lParam: Pointer? = null
    @JvmField var iImage: Int = 0
}