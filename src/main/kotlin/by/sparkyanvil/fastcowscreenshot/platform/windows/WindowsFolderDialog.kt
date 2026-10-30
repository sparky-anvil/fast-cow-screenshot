package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.BROWSEINFO
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Shell32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.WString
import com.sun.jna.platform.win32.Ole32
import java.nio.file.Path

/** SHBrowseForFolderW. Вызывать на fcs-dialog. Не бросает. */
internal class WindowsFolderDialog(private val shell32: Shell32Api = Shell32Api.INSTANCE) {
    /** Выбранная папка или null («Отмена»/виртуальная папка без пути). */
    fun show(): Path? {
        val display = Memory(WinConst.MAX_PATH * 2L).apply { clear() }
        val bi = BROWSEINFO().apply {
            hwndOwner = null
            pszDisplayName = display
            lpszTitle = WString(TITLE)
            ulFlags = WinConst.BIF_RETURNONLYFSDIRS or WinConst.BIF_NEWDIALOGSTYLE
        }
        val pidl = shell32.SHBrowseForFolder(bi) ?: return null
        try {
            val buf = CharArray(WinConst.MAX_PATH)
            if (!shell32.SHGetPathFromIDList(pidl, buf)) return null
            return Path.of(Native.toString(buf))
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(pidl)
        }
    }

    companion object {
        const val TITLE: String = "Выберите директорию для снимков"
    }
}
