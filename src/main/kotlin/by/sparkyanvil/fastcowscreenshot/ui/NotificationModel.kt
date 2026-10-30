package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.CaptureOutcome
import by.sparkyanvil.fastcowscreenshot.capture.ClipboardResult
import by.sparkyanvil.fastcowscreenshot.domain.TextEllipsis
import by.sparkyanvil.fastcowscreenshot.storage.SaveResult
import java.nio.file.Path

/** Что показать. folderTooltip — полный путь, если строка папки сокращена; иначе null. */
internal data class NotificationModel(
    val title: String,
    val lines: List<String>,
    val fileToReveal: Path?,
    val showSaveAs: Boolean,
    val folderTooltip: String?,
)

/** Построение [NotificationModel] из результата захвата. */
internal object NotificationModels {
    const val MAX_PATH_CHARS: Int = 48
    const val TITLE_SAVED: String = "Снимок сохранён"
    const val TITLE_COPIED_ONLY: String = "Снимок скопирован в буфер обмена"
    const val TITLE_SAVE_FAILED: String = "Не удалось сохранить снимок"
    const val TITLE_CLIPBOARD_FAILED: String = "Не удалось скопировать в буфер обмена"
    const val TITLE_BOTH_FAILED: String = "Не удалось скопировать в буфер обмена и сохранить снимок"

    /** Таблица раздела 8 задачи TASK-011. */
    fun fromOutcome(outcome: CaptureOutcome): NotificationModel {
        val clipboardOk = outcome.clipboard is ClipboardResult.Success
        val save = outcome.save
        return when (save) {
            is SaveResult.Saved -> {
                val dir = save.file.parent
                NotificationModel(
                    title = if (clipboardOk) TITLE_SAVED else TITLE_CLIPBOARD_FAILED,
                    lines = listOf(save.file.fileName.toString(), folderLine(dir)),
                    fileToReveal = save.file,
                    showSaveAs = true,
                    folderTooltip = tooltip(dir),
                )
            }
            null -> NotificationModel(
                title = if (clipboardOk) TITLE_COPIED_ONLY else TITLE_CLIPBOARD_FAILED,
                lines = emptyList(),
                fileToReveal = null,
                showSaveAs = true,
                folderTooltip = null,
            )
            is SaveResult.Failed -> {
                val dir = save.directory
                if (clipboardOk) {
                    val unknown = dir.toString().isEmpty()
                    NotificationModel(
                        title = TITLE_SAVE_FAILED,
                        lines = if (unknown) listOf(save.reason) else listOf(save.reason, folderLine(dir)),
                        fileToReveal = null,
                        showSaveAs = true,
                        folderTooltip = tooltip(dir),
                    )
                } else {
                    NotificationModel(TITLE_BOTH_FAILED, listOf(save.reason), null, true, null)
                }
            }
        }
    }

    /** NotificationModel(title, lines, null, false, null). */
    fun message(title: String, lines: List<String>): NotificationModel =
        NotificationModel(title, lines, null, false, null)

    private fun folderLine(dir: Path?): String = TextEllipsis.middle(dir?.toString().orEmpty(), MAX_PATH_CHARS)

    private fun tooltip(dir: Path?): String? {
        val text = dir?.toString().orEmpty()
        return if (text.length > MAX_PATH_CHARS) text else null
    }
}
