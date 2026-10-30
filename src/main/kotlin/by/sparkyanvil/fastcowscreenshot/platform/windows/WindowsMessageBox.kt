package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.User32Api
import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.WinConst
import java.nio.file.Path

/** MessageBoxW. Вызывать на fcs-dialog. */
internal class WindowsMessageBox(private val user32: User32Api = User32Api.INSTANCE) {
    /** true — пользователь выбрал «Да». */
    fun confirmOverwrite(file: Path): Boolean {
        val flags = WinConst.MB_YESNO or WinConst.MB_ICONWARNING or WinConst.MB_TOPMOST or WinConst.MB_SETFOREGROUND
        val text = "Файл «${file.fileName}» уже существует.\nЗаменить его?"
        return user32.MessageBox(null, text, OVERWRITE_CAPTION, flags) == WinConst.IDYES
    }

    /** Сообщение об ошибке с одной кнопкой OK. */
    fun showError(caption: String, text: String) {
        val flags = MB_OK or MB_ICONERROR or WinConst.MB_TOPMOST or WinConst.MB_SETFOREGROUND
        user32.MessageBox(null, text, caption, flags)
    }

    companion object {
        const val MB_OK: Int = 0x0
        const val MB_ICONERROR: Int = 0x10
        const val OVERWRITE_CAPTION: String = "Сохранить как"
    }
}
