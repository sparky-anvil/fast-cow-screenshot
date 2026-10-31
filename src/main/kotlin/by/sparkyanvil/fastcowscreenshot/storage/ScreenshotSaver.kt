package by.sparkyanvil.fastcowscreenshot.storage

import java.awt.image.BufferedImage
import java.nio.file.Path
import java.time.LocalDateTime

/** Автосохранение. Вызывать на fcs-save. Не бросает исключений. */
internal interface ScreenshotSaver {
    fun save(image: BufferedImage, capturedAt: LocalDateTime, directory: Path): SaveResult
}