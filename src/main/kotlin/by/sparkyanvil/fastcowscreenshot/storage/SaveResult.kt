package by.sparkyanvil.fastcowscreenshot.storage

import java.nio.file.Path

/** Результат сохранения. reason — текст для пользователя (на русском). */
internal sealed interface SaveResult {
    data class Saved(val file: Path) : SaveResult
    data class Failed(val directory: Path, val reason: String) : SaveResult
}