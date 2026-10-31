package by.sparkyanvil.fastcowscreenshot.storage

import java.io.IOException
import java.nio.file.AccessDeniedException
import java.nio.file.FileSystemException
import java.nio.file.NoSuchFileException

/** Причина ошибки ввода-вывода для пользователя. */
internal object IoErrorMessages {
    const val ACCESS_DENIED: String = "Нет прав на запись в папку"
    const val DISK_FULL: String = "Недостаточно места на диске"
    const val UNAVAILABLE: String = "Папка недоступна"
    const val TOO_MANY_FILES: String = "Слишком много файлов с одинаковым именем"

    /** Преобразует исключение в текст для пользователя. */
    fun describe(e: IOException): String {
        if (e is AccessDeniedException) return ACCESS_DENIED
        val text = (e as? FileSystemException)?.reason ?: e.message ?: ""
        if (text.contains("not enough space", ignoreCase = true)) return DISK_FULL
        if (e is NoSuchFileException ||
            text.contains("not ready", ignoreCase = true) ||
            text.contains("network", ignoreCase = true) ||
            text.contains("cannot find the path", ignoreCase = true)
        ) {
            return UNAVAILABLE
        }
        return "Ошибка записи: ${text.ifEmpty { e.javaClass.simpleName }}"
    }
}