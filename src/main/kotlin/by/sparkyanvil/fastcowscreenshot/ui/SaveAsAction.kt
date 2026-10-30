package by.sparkyanvil.fastcowscreenshot.ui

import by.sparkyanvil.fastcowscreenshot.capture.CaptureNotifier
import by.sparkyanvil.fastcowscreenshot.capture.LastScreenshotHolder
import by.sparkyanvil.fastcowscreenshot.domain.SaveAsFileName
import by.sparkyanvil.fastcowscreenshot.domain.ScreenshotFileNames
import by.sparkyanvil.fastcowscreenshot.settings.AppSettings
import by.sparkyanvil.fastcowscreenshot.settings.SettingsStore
import by.sparkyanvil.fastcowscreenshot.storage.IoErrorMessages
import by.sparkyanvil.fastcowscreenshot.storage.PngFileWriter
import org.slf4j.LoggerFactory
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicBoolean

/** «Сохранить как…». run() — с любого потока, возвращается сразу. */
internal class SaveAsAction(
    private val lastScreenshot: LastScreenshotHolder,
    private val settings: SettingsStore,
    private val showDialog: (initialDirectory: Path, suggestedName: String) -> Path?,
    private val confirmOverwrite: (Path) -> Boolean,
    private val writer: PngFileWriter,
    private val notifier: CaptureNotifier,
    private val dialogExecutor: Executor,
    private val uiExecutor: Executor,
) {
    private val inProgress = AtomicBoolean(false)

    /** Фиксирует последний снимок и выполняет диалог и запись на dialogExecutor. */
    fun run() {
        val shot = lastScreenshot.get()
        if (shot == null) {
            log.debug("Save as: no screenshot")
            return
        }
        if (!inProgress.compareAndSet(false, true)) {
            log.debug("Save as already open")
            return
        }
        dialogExecutor.execute {
            var target: Path? = null
            try {
                val s = settings.current
                val chosen = showDialog(initialDirectory(s), ScreenshotFileNames.candidate(shot.capturedAt, 0))
                if (chosen != null) {
                    val normalized = SaveAsFileName.normalize(chosen)
                    target = normalized
                    if (!Files.exists(normalized) || confirmOverwrite(normalized)) {
                        writer.writeAtomically(shot.image, normalized, replaceExisting = true)
                        settings.update { it.copy(lastSaveAsDirectory = normalized.parent.toString()) }
                        log.info("Saved as {}", normalized)
                    }
                }
            } catch (e: IOException) {
                log.error("Save as failed: {}", e.toString())
                val lines = listOfNotNull(IoErrorMessages.describe(e), target?.parent?.toString())
                uiExecutor.execute { notifier.showMessage(SAVE_FAILED_TITLE, lines) }
            } finally {
                inProgress.set(false)
            }
        }
    }

    /** lastSaveAsDirectory, если это существующая папка; иначе defaultDirectory. */
    fun initialDirectory(s: AppSettings): Path {
        val last = s.lastSaveAsDirectory?.let { runCatching { Path.of(it) }.getOrNull() }
        return if (last != null && Files.isDirectory(last)) last else Path.of(s.defaultDirectory)
    }

    companion object {
        const val SAVE_FAILED_TITLE: String = "Не удалось сохранить снимок"

        private val log = LoggerFactory.getLogger(SaveAsAction::class.java)
    }
}
