package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Comdlg32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.OPENFILENAME
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import com.sun.jna.Memory
import com.sun.jna.WString
import org.slf4j.LoggerFactory
import java.nio.file.Path

/** GetSaveFileNameW. Вызывать на fcs-dialog. Не бросает. */
internal class WindowsSaveDialog(private val comdlg32: Comdlg32Api = Comdlg32Api.INSTANCE) {
    /** Путь, выбранный пользователем (как вернул диалог), или null — «Отмена»/ошибка. */
    fun show(initialDirectory: Path, suggestedName: String): Path? {
        val filterChars = FILTER.toCharArray()
        val filterMem = Memory(filterChars.size * 2L)
        filterMem.write(0, filterChars, 0, filterChars.size)
        val fileMem = Memory(WinConst.SAVE_DIALOG_BUFFER_CHARS * 2L)
        fileMem.clear()
        val nameChars = suggestedName.toCharArray()
        fileMem.write(0, nameChars, 0, nameChars.size)
        val ofn = OPENFILENAME()
        ofn.hwndOwner = null
        ofn.lpstrFilter = filterMem
        ofn.nFilterIndex = 1
        ofn.lpstrFile = fileMem
        ofn.nMaxFile = WinConst.SAVE_DIALOG_BUFFER_CHARS
        ofn.lpstrInitialDir = WString(initialDirectory.toString())
        ofn.lpstrTitle = WString(TITLE)
        ofn.Flags = WinConst.OFN_EXPLORER or WinConst.OFN_PATHMUSTEXIST or
            WinConst.OFN_HIDEREADONLY or WinConst.OFN_NOCHANGEDIR
        if (comdlg32.GetSaveFileName(ofn)) return Path.of(fileMem.getWideString(0))
        val code = comdlg32.CommDlgExtendedError()
        if (code != 0) log.warn("GetSaveFileName failed, CommDlgExtendedError={}", code)
        return null
    }

    companion object {
        const val TITLE: String = "Сохранить как"
        const val FILTER: String = "PNG (*.png)\u0000*.png\u0000\u0000"

        private val log = LoggerFactory.getLogger(WindowsSaveDialog::class.java)
    }
}
