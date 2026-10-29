package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HBITMAP
import com.sun.jna.platform.win32.WinDef.HDC
import com.sun.jna.platform.win32.WinGDI
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Функции gdi32.dll, нужные проекту. */
internal interface Gdi32Api : StdCallLibrary {
    fun CreateCompatibleDC(hdc: HDC?): HDC?
    fun DeleteDC(hdc: HDC): Boolean
    fun CreateDIBSection(
        hdc: HDC?, pbmi: WinGDI.BITMAPINFO, usage: Int, ppvBits: PointerByReference,
        hSection: Pointer?, offset: Int,
    ): HBITMAP?
    fun SelectObject(hdc: HDC, h: WinNT.HANDLE): WinNT.HANDLE?
    fun DeleteObject(ho: WinNT.HANDLE): Boolean
    fun BitBlt(hdc: HDC, x: Int, y: Int, cx: Int, cy: Int, hdcSrc: HDC, x1: Int, y1: Int, rop: Int): Boolean

    companion object {
        val INSTANCE: Gdi32Api = Native.load("gdi32", Gdi32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}