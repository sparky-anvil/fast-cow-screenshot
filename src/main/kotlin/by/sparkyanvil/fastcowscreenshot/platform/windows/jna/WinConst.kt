package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

/** Константы WinAPI, используемые проектом. */
internal object WinConst {
    const val MONITOR_DEFAULTTONULL: Int = 0x0
    const val MONITOR_DEFAULTTOPRIMARY: Int = 0x1
    const val MONITOR_DEFAULTTONEAREST: Int = 0x2
    const val MONITORINFOF_PRIMARY: Int = 0x1
    const val SRCCOPY: Int = 0x00CC0020
    const val CAPTUREBLT: Int = 0x40000000
    const val BI_RGB: Int = 0
    const val DIB_RGB_COLORS: Int = 0
    const val CURSOR_SHOWING: Int = 0x1
    const val DI_NORMAL: Int = 0x3
    const val WM_HOTKEY: Int = 0x0312
    const val WM_QUIT: Int = 0x0012
    const val WM_APP: Int = 0x8000
    const val PM_NOREMOVE: Int = 0x0
    const val CF_DIB: Int = 8
    const val GMEM_MOVEABLE: Int = 0x0002
    const val SWP_NOACTIVATE: Int = 0x0010
    const val SWP_SHOWWINDOW: Int = 0x0040
    const val HWND_TOPMOST_VALUE: Int = -1
    const val HWND_MESSAGE_VALUE: Int = -3
    const val WDA_EXCLUDEFROMCAPTURE: Int = 0x11
    const val MB_YESNO: Int = 0x4
    const val MB_ICONWARNING: Int = 0x30
    const val MB_SETFOREGROUND: Int = 0x10000
    const val MB_TOPMOST: Int = 0x40000
    const val IDYES: Int = 6
    const val OFN_HIDEREADONLY: Int = 0x4
    const val OFN_NOCHANGEDIR: Int = 0x8
    const val OFN_PATHMUSTEXIST: Int = 0x800
    const val OFN_EXPLORER: Int = 0x80000
    const val BIF_RETURNONLYFSDIRS: Int = 0x1
    const val BIF_NEWDIALOGSTYLE: Int = 0x40
    const val MAX_PATH: Int = 260
    const val SAVE_DIALOG_BUFFER_CHARS: Int = 32768
    const val COINIT_APARTMENTTHREADED: Int = 0x2
}