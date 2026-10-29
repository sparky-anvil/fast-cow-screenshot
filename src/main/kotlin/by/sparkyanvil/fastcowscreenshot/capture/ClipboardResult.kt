package by.sparkyanvil.fastcowscreenshot.capture

/** Результат записи в буфер. reason — техническое описание для лога (на английском). */
internal sealed interface ClipboardResult {
    data object Success : ClipboardResult
    data class Failure(val reason: String) : ClipboardResult
}