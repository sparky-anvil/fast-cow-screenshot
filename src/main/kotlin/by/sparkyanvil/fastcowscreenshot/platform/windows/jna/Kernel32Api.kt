package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Функции kernel32.dll, нужные проекту. */
internal interface Kernel32Api : StdCallLibrary {
    fun GlobalAlloc(uFlags: Int, dwBytes: Long): Pointer?
    fun GlobalLock(hMem: Pointer): Pointer?
    fun GlobalUnlock(hMem: Pointer): Boolean
    fun GlobalFree(hMem: Pointer): Pointer?
    fun GetCurrentThreadId(): Int

    companion object {
        val INSTANCE: Kernel32Api = Native.load("kernel32", Kernel32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}