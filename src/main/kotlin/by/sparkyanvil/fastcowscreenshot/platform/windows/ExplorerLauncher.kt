package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.platform.windows.jna.Shell32Api
import org.slf4j.LoggerFactory
import java.awt.Desktop
import java.nio.file.Path

/** Проводник. Вызывать на fcs-dialog (COM STA). Не бросает. */
internal class ExplorerLauncher(private val shell32: Shell32Api = Shell32Api.INSTANCE) {
    /** Открывает папку файла и выделяет его (SHOpenFolderAndSelectItems). true — успех. */
    fun reveal(file: Path): Boolean {
        val pidl = shell32.ILCreateFromPath(file.toString())
        if (pidl == null) {
            log.warn("ILCreateFromPath failed for {}", file)
            return false
        }
        try {
            val hr = shell32.SHOpenFolderAndSelectItems(pidl, 0, null, 0)
            if (hr.toInt() != 0) {
                log.warn("SHOpenFolderAndSelectItems failed, HRESULT={}", hr.toInt())
                return false
            }
            return true
        } finally {
            shell32.ILFree(pidl)
        }
    }

    /** Открывает саму папку: java.awt.Desktop.getDesktop().open(dir.toFile()). true — успех. */
    fun openFolder(dir: Path): Boolean = try {
        Desktop.getDesktop().open(dir.toFile())
        true
    } catch (e: Exception) {
        log.warn("Open folder failed: {}", e.toString())
        false
    }

    private companion object {
        private val log = LoggerFactory.getLogger(ExplorerLauncher::class.java)
    }
}
