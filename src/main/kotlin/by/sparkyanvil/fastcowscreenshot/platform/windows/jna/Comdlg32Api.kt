package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Native
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Функции comdlg32.dll, нужные проекту. */
internal interface Comdlg32Api : StdCallLibrary {
    fun GetSaveFileName(lpofn: OPENFILENAME): Boolean
    fun CommDlgExtendedError(): Int

    companion object {
        val INSTANCE: Comdlg32Api = Native.load("comdlg32", Comdlg32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}