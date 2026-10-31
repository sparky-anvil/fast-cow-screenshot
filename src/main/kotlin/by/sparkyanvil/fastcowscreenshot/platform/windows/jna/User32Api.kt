package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinDef.HICON
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.RECT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Функции user32.dll, нужные проекту. Имена совпадают с WinAPI (без суффикса W). */
internal interface User32Api : StdCallLibrary {
    fun GetDC(hWnd: HWND?): HDC?
    fun ReleaseDC(hWnd: HWND?, hDC: HDC): Int
    fun EnumDisplayMonitors(hdc: HDC?, lprcClip: RECT?, lpfnEnum: WinUser.MONITORENUMPROC, dwData: LPARAM?): Boolean
    fun GetMonitorInfo(hMonitor: WinUser.HMONITOR, lpmi: WinUser.MONITORINFOEX): Boolean
    fun MonitorFromPoint(pt: POINT.ByValue, dwFlags: Int): WinUser.HMONITOR?
    fun MonitorFromWindow(hWnd: HWND, dwFlags: Int): WinUser.HMONITOR?
    fun GetCursorPos(lpPoint: POINT): Boolean
    fun GetCursorInfo(pci: CURSORINFO): Boolean
    fun CopyIcon(hIcon: HICON): HICON?
    fun DestroyIcon(hIcon: HICON): Boolean
    fun GetIconInfo(hIcon: HICON, piconinfo: WinGDI.ICONINFO): Boolean
    fun DrawIconEx(
        hdc: HDC, xLeft: Int, yTop: Int, hIcon: HICON, cxWidth: Int, cyWidth: Int,
        istepIfAniCur: Int, hbrFlickerFreeDraw: Pointer?, diFlags: Int,
    ): Boolean
    fun RegisterHotKey(hWnd: HWND?, id: Int, fsModifiers: Int, vk: Int): Boolean
    fun UnregisterHotKey(hWnd: HWND?, id: Int): Boolean
    fun GetMessage(lpMsg: WinUser.MSG, hWnd: HWND?, wMsgFilterMin: Int, wMsgFilterMax: Int): Int
    fun PeekMessage(lpMsg: WinUser.MSG, hWnd: HWND?, wMsgFilterMin: Int, wMsgFilterMax: Int, wRemoveMsg: Int): Boolean
    fun PostThreadMessage(idThread: Int, msg: Int, wParam: WPARAM, lParam: LPARAM): Boolean
    fun CreateWindowEx(
        dwExStyle: Int, lpClassName: String, lpWindowName: String, dwStyle: Int,
        x: Int, y: Int, nWidth: Int, nHeight: Int, hWndParent: HWND?, hMenu: Pointer?,
        hInstance: Pointer?, lpParam: Pointer?,
    ): HWND?
    fun DestroyWindow(hWnd: HWND): Boolean
    fun OpenClipboard(hWndNewOwner: HWND?): Boolean
    fun EmptyClipboard(): Boolean
    fun SetClipboardData(uFormat: Int, hMem: Pointer): Pointer?
    fun CloseClipboard(): Boolean
    fun SetThreadDpiAwarenessContext(dpiContext: Pointer): Pointer?
    fun GetThreadDpiAwarenessContext(): Pointer?
    fun GetAwarenessFromDpiAwarenessContext(value: Pointer): Int
    fun SetWindowPos(hWnd: HWND, hWndInsertAfter: HWND?, x: Int, y: Int, cx: Int, cy: Int, uFlags: Int): Boolean
    fun GetWindowRect(hWnd: HWND, lpRect: RECT): Boolean
    fun SetWindowDisplayAffinity(hWnd: HWND, dwAffinity: Int): Boolean
    fun MessageBox(hWnd: HWND?, lpText: String, lpCaption: String, uType: Int): Int

    companion object {
        val INSTANCE: User32Api = Native.load("user32", User32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}