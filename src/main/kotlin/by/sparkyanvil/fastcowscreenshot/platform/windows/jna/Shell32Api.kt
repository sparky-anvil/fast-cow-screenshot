package by.sparkyanvil.fastcowscreenshot.platform.windows.jna

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinNT
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** Функции shell32.dll, нужные проекту. */
internal interface Shell32Api : StdCallLibrary {
    fun ILCreateFromPath(pszPath: String): Pointer?
    fun ILFree(pidl: Pointer)
    fun SHOpenFolderAndSelectItems(pidlFolder: Pointer, cidl: Int, apidl: Pointer?, dwFlags: Int): WinNT.HRESULT
    fun SHBrowseForFolder(lpbi: BROWSEINFO): Pointer?
    fun SHGetPathFromIDList(pidl: Pointer, pszPath: CharArray): Boolean

    companion object {
        val INSTANCE: Shell32Api = Native.load("shell32", Shell32Api::class.java, W32APIOptions.DEFAULT_OPTIONS)
    }
}