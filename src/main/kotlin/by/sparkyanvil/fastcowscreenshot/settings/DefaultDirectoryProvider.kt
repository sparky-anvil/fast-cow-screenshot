package by.sparkyanvil.fastcowscreenshot.settings

import java.nio.file.Path

/** Источник начальной директории по умолчанию (REQ-001.44). Реализация на WinAPI — TASK-009. */
internal fun interface DefaultDirectoryProvider {
    fun initialDirectory(): Path
}
