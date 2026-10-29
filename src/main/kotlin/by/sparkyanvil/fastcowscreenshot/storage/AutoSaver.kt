package by.sparkyanvil.fastcowscreenshot.storage

import by.sparkyanvil.fastcowscreenshot.domain.ScreenshotFileNames
import org.slf4j.LoggerFactory
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime

private val log = LoggerFactory.getLogger(AutoSaver::class.java)

/** Автосохранение: создаёт папку, подбирает свободное имя и атомарно пишет PNG. */
internal class AutoSaver(private val writer: PngFileWriter = PngFileWriter()) : ScreenshotSaver {
    override fun save(image: BufferedImage, capturedAt: LocalDateTime, directory: Path): SaveResult {
        val start = System.nanoTime()
        try {
            Files.createDirectories(directory)
            for (index in 0..ScreenshotFileNames.MAX_SUFFIX) {
                val target = directory.resolve(ScreenshotFileNames.candidate(capturedAt, index))
                if (Files.exists(target)) continue
                try {
                    writer.writeAtomically(image, target, replaceExisting = false)
                } catch (e: FileAlreadyExistsException) {
                    continue
                }
                log.info("Saved {} in {} ms", target, (System.nanoTime() - start) / 1_000_000)
                return SaveResult.Saved(target)
            }
            log.error("Auto-save failed in {}: too many files", directory)
            return SaveResult.Failed(directory, IoErrorMessages.TOO_MANY_FILES)
        } catch (e: IOException) {
            log.error("Auto-save failed in {}: {}", directory, e.toString())
            return SaveResult.Failed(directory, IoErrorMessages.describe(e))
        }
    }
}