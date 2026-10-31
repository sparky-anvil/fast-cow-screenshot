package by.sparkyanvil.fastcowscreenshot.platform.windows

import by.sparkyanvil.fastcowscreenshot.settings.DefaultDirectoryProvider
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Shell32Util
import com.sun.jna.platform.win32.Win32Exception
import org.slf4j.LoggerFactory
import java.nio.file.Path

private val log = LoggerFactory.getLogger(KnownFolders::class.java)

/** Начальная директория по умолчанию (REQ-001.44). Вызывать с любого потока. */
internal class KnownFolders : DefaultDirectoryProvider {
    override fun initialDirectory(): Path {
        for (folderId in listOf(FOLDERID_SCREENSHOTS, FOLDERID_PICTURES)) {
            try {
                return Path.of(Shell32Util.getKnownFolderPath(Guid.GUID.fromString(folderId)))
            } catch (e: Win32Exception) {
                log.warn("Known folder {} unavailable: {}", folderId, e.message)
            }
        }
        return Path.of(System.getenv("USERPROFILE") ?: System.getProperty("user.home"), "Pictures")
    }

    companion object {
        const val FOLDERID_SCREENSHOTS: String = "{B7BEDE81-DF94-4682-A7D8-57A52620B86F}"
        const val FOLDERID_PICTURES: String = "{33E28130-4E1E-4676-835A-98395C3BC3BB}"
    }
}